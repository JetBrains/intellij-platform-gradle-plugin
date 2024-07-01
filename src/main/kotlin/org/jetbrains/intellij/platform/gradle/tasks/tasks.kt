// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.argumentProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.PluginArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SandboxArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SplitModeArgumentProvider
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.launchFor
import org.jetbrains.intellij.platform.gradle.providers.JavaRuntimeMetadataValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.path.IntelliJPluginVerifierPathResolver
import org.jetbrains.intellij.platform.gradle.resolvers.path.JavaRuntimePathResolver
import org.jetbrains.intellij.platform.gradle.resolvers.path.MarketplaceZipSignerPathResolver
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.*

/**
 * Registers a task of type [T] with the given names
 * and configures its extra capabilities based on the [org.jetbrains.intellij.platform.gradle.tasks.aware]
 * interfaces it uses.
 * Every new task is supposed to be registered using this method to get extra configuration used.
 *
 * @param T the type of task to register
 * @param names names of tasks to be registered.
 * @param configureWithType if `true`, apply the provided [configuration] to all tasks of [T] type, otherwise — only to tasks defined with [names]
 * @param configuration a function used to configure tasks
 */
internal inline fun <reified T : Task> Project.registerTask(
    vararg names: String,
    configureWithType: Boolean = true,
    noinline configuration: T.() -> Unit = {},
) {
    // Register new tasks of T type if it does not exist yet
    val log = Logger(javaClass)

    names.forEach { name ->
        log.info("Configuring task: $name")
        tasks.maybeCreate<T>(name)
    }

    when (configureWithType) {
        true -> tasks.withType<T>(configuration)
        false -> names.forEach { tasks.named<T>(it).configure(configuration) }
    }
}

internal fun <T : Task> Project.preconfigureTask(task: T) {
    // Preconfigure all tasks of T type if they inherit from *Aware interfaces

    with(task) task@{
        if (name != Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            dependsOn(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
        }

        /**
         * Applies the base [Configurations.INTELLIJ_PLATFORM] configuration to [IntelliJPlatformVersionAware] tasks so they can access details of the used IntelliJ
         * Platform, such as [ProductInfo] or its root directory location.
         *
         * @see IntelliJPlatformVersionAware
         */
        if (this is IntelliJPlatformVersionAware) {
            intelliJPlatformConfiguration = configurations.maybeCreate(Configurations.INTELLIJ_PLATFORM)
            intelliJPlatformPluginConfiguration = configurations.maybeCreate(Configurations.INTELLIJ_PLATFORM_PLUGIN)
        }

        /**
         * Makes tasks aware of the Coroutines Java Agent file required to debug coroutines when running IDE locally.
         *
         * @see CoroutinesJavaAgentAware
         */
        if (this is CoroutinesJavaAgentAware) {
            val initializeIntelliJPlatformPluginTaskProvider =
                tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

            coroutinesJavaAgentFile.convention(initializeIntelliJPlatformPluginTaskProvider.flatMap {
                it.coroutinesJavaAgent
            })
        }

        /**
         * The [PluginAware] resolves and parses the `plugin.xml` file for easy access in other tasks.
         */
        if (this is PluginAware) {
            val patchPluginXmlTaskProvider = tasks.named<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML)
            pluginXml.convention(patchPluginXmlTaskProvider.flatMap { it.outputFile })
        }

        /**
         * The [RuntimeAware] adjusts tasks for the running a guest IDE purpose.
         * This configuration picks relevant Java Runtime using the [JavaRuntimePathResolver] and [RuntimeAware.runtimeMetadata].
         */
        if (this is RuntimeAware) {
            val javaRuntimePathResolver = JavaRuntimePathResolver(
                jetbrainsRuntime = configurations[Configurations.JETBRAINS_RUNTIME],
                intellijPlatform = intelliJPlatformConfiguration,
                javaToolchainSpec = project.the<JavaPluginExtension>().toolchain,
                javaToolchainService = project.serviceOf<JavaToolchainService>(),
            )

            runtimeDirectory = layout.dir(provider {
                javaRuntimePathResolver.resolve().toFile()
            })
            runtimeMetadata = providers.of(JavaRuntimeMetadataValueSource::class) {
                parameters {
                    executable = layout.file(
                        runtimeDirectory.map { it.asPath.resolveJavaRuntimeExecutable().toFile() }
                    )
                }
            }
            runtimeArchitecture = runtimeMetadata.map { it["os.arch"].orEmpty() }
        }

        /**
         * The [PluginVerifierAware] resolves and provides the IntelliJ Plugin Verifier for the further usage.
         */
        if (this is PluginVerifierAware) {
            val intelliJPluginVerifierPathResolver = IntelliJPluginVerifierPathResolver(
                intellijPluginVerifier = configurations[Configurations.INTELLIJ_PLUGIN_VERIFIER],
                localPath = extensionProvider.flatMap { it.verifyPlugin.cliPath },
            )

            pluginVerifierExecutable.convention(layout.file(provider {
                intelliJPluginVerifierPathResolver
                    .runCatching { resolve().toFile() }
                    .getOrNull()
            }))
        }

        /**
         * The [SigningAware] resolves and provides the Marketplace ZIP Signer for the further usage.
         */
        if (this is SigningAware) {
            val marketplaceZipSignerPathResolver = MarketplaceZipSignerPathResolver(
                marketplaceZipSigner = configurations[Configurations.MARKETPLACE_ZIP_SIGNER].asLenient,
                localPath = extensionProvider.flatMap { it.signing.cliPath },
            )

            zipSignerExecutable.convention(layout.file(provider {
                marketplaceZipSignerPathResolver
                    .runCatching { resolve().toFile() }
                    .getOrNull()
            }))
        }

        /**
         * The [JavaCompilerAware] resolves and provides the Java Compiler dependency.
         */
        if (this is JavaCompilerAware) {
            javaCompilerConfiguration = configurations[Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER]
        }

        /**
         * The [SplitModeAware] allows to auto-reload plugin when run in the IDE.
         */
        if (this is AutoReloadAware) {
            autoReload.convention(extensionProvider.flatMap { it.autoReload })
        }

        /**
         * The [RunnableIdeAware] is more complex one than [RuntimeAware] as it preconfigures also the
         * [JavaForkOptions]-based tasks by setting JVM Arguments providers and classpath.
         */
        if (this is RunnableIdeAware) {
            enableAssertions = true

            jvmArgumentProviders.add(
                IntelliJPlatformArgumentProvider(
                    intelliJPlatformConfiguration,
                    coroutinesJavaAgentFile,
                    runtimeArchitecture,
                    options = this,
                )
            )
            jvmArgumentProviders.add(
                SandboxArgumentProvider(
                    sandboxConfigDirectory,
                    sandboxPluginsDirectory,
                    sandboxSystemDirectory,
                    sandboxLogDirectory,
                )
            )
            jvmArgumentProviders.add(
                PluginArgumentProvider(
                    pluginXml,
                )
            )

            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")

            if (this is JavaExec) {
                mainClass = "com.intellij.idea.Main"
                javaLauncher = runtimeDirectory.zip(runtimeMetadata) { directory, metadata ->
                    IntelliJPlatformJavaLauncher(directory, metadata)
                }

                classpath += files(
                    runtimeArchitecture.map { architecture ->
                        productInfo
                            .launchFor(architecture)
                            .bootClassPathJarNames
                            .map { platformPath.resolve("lib/$it") }
                    }
                )

                classpath += files(
                    runtimeDirectory.map { it.file("lib/tools") }
                )

                if (this is SplitModeAware) {
                    argumentProviders.add(SplitModeArgumentProvider(splitMode))
                }
            }
        }

        if (this is KotlinMetadataAware) {
            kotlinPluginAvailable.convention(false)

            val implementationConfiguration = project.configurations[Configurations.External.IMPLEMENTATION]
            val compileOnlyConfiguration = project.configurations[Configurations.External.COMPILE_ONLY]

            kotlinxCoroutinesLibraryPresent.convention(project.provider {
                listOf(implementationConfiguration, compileOnlyConfiguration).any { configuration ->
                    configuration.dependencies.any {
                        it.group == "org.jetbrains.kotlinx" && it.name.startsWith("kotlinx-coroutines")
                    }
                }
            })

            project.pluginManager.withPlugin(Plugins.External.KOTLIN) {
                val kotlinOptionsProvider = project.tasks.named(Tasks.External.COMPILE_KOTLIN).map {
                    it.withGroovyBuilder { getProperty("kotlinOptions") }
                        .withGroovyBuilder { getProperty("options") }
                }

                kotlinPluginAvailable.convention(true)

                kotlinJvmTarget.convention(kotlinOptionsProvider.flatMap {
                    it.withGroovyBuilder { getProperty("jvmTarget") as Property<*> }
                        .map { jvmTarget -> jvmTarget.withGroovyBuilder { getProperty("target") } }
                        .map { value -> value as String }
                })
                kotlinApiVersion.convention(kotlinOptionsProvider.flatMap {
                    it.withGroovyBuilder { getProperty("apiVersion") as Property<*> }
                        .map { apiVersion -> apiVersion.withGroovyBuilder { getProperty("version") } }
                        .map { value -> value as String }
                })
                kotlinLanguageVersion.convention(kotlinOptionsProvider.flatMap {
                    it.withGroovyBuilder { getProperty("languageVersion") as Property<*> }
                        .map { languageVersion -> languageVersion.withGroovyBuilder { getProperty("version") } }
                        .map { value -> value as String }
                })
                kotlinVersion.convention(
                    project.extensions.getByName("kotlin")
                        .withGroovyBuilder { getProperty("coreLibrariesVersion") as String }
                )
                kotlinStdlibDefaultDependency.convention(
                    project.providers
                        .gradleProperty(GradleProperties.KOTLIN_STDLIB_DEFAULT_DEPENDENCY)
                        .map { it.toBoolean() }
                )
            }

            inputs.properties(
                "kotlinJvmTarget" to project.provider { kotlinJvmTarget.orNull.toString() },
                "kotlinApiVersion" to project.provider { kotlinApiVersion.orNull.toString() },
                "kotlinLanguageVersion" to project.provider { kotlinLanguageVersion.orNull.toString() },
                "kotlinVersion" to project.provider { kotlinVersion.orNull.toString() },
                "kotlinStdlibDefaultDependency" to project.provider { kotlinStdlibDefaultDependency.orNull.toString() },
            )
        }
    }
}

/**
 * Creates a specific sandbox directory using the [suffixProvider] and [name]
 * within the [sandboxContainer] container directory.
 *
 * @param sandboxContainer The sandbox container directory.
 * @param suffixProvider The suffix for the sandbox directory.
 * @param name The name for the sandbox directory.
 *
 * @see Sandbox
 * @see SandboxAware
 */
internal fun DirectoryProperty.configureSandbox(
    sandboxContainer: DirectoryProperty,
    suffixProvider: Provider<String>,
    name: String,
) {
    convention(sandboxContainer.zip(suffixProvider) { container, suffix ->
        container.dir(name + suffix)
    })
}

/**
 * An interface to unify how IntelliJ Platform Gradle Plugin tasks are registered.
 * Every task, when registered, can rely on resources resolved with the [project] instance.
 * If the current task depends on another task, make sure it is registered later.
 * The [register] method should most likely be combined with [Project.registerTask].
 */
internal interface Registrable {

    fun register(project: Project)
}
