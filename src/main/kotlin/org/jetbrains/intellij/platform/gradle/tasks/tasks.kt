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
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.argumentProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.PluginArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SandboxArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SplitModeArgumentProvider
import org.jetbrains.intellij.platform.gradle.artifacts.transform.ExtractorTransformer
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformPluginsExtension
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.launchFor
import org.jetbrains.intellij.platform.gradle.providers.JavaRuntimeMetadataValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.path.IntelliJPluginVerifierPathResolver
import org.jetbrains.intellij.platform.gradle.resolvers.path.JavaRuntimePathResolver
import org.jetbrains.intellij.platform.gradle.resolvers.path.MarketplaceZipSignerPathResolver
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.SplitModeTarget
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
        /**
         * The suffix used to build unique names for configurations and tasks for [CustomIntelliJPlatformVersionAware] purposes
         *
         * @see CustomIntelliJPlatformVersionAware
         */
        val suffix = when {
            this is CustomIntelliJPlatformVersionAware -> name
            else -> name.substringAfter('_', "")
        }.let { "_$it" }.trimEnd('_')

        val extension = project.the<IntelliJPlatformExtension>()

        /**
         * Applies the base [Configurations.INTELLIJ_PLATFORM] configuration to [IntelliJPlatformVersionAware] tasks so they can access details of the used IntelliJ
         * Platform, such as [ProductInfo] or its root directory location.
         *
         * @see IntelliJPlatformVersionAware
         */
        if (this is IntelliJPlatformVersionAware) {
            intelliJPlatformConfiguration = configurations.maybeCreate(Configurations.INTELLIJ_PLATFORM + suffix).asLenient
            intelliJPlatformPluginConfiguration = configurations.maybeCreate(Configurations.INTELLIJ_PLATFORM_PLUGIN + suffix).asLenient
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
         * The concept of [CustomIntelliJPlatformVersionAware] lets a task accept and use a custom
         * IntelliJ Platform other than the one used to build the project
         *
         * @see CustomIntelliJPlatformVersionAware
         */
        if (this is CustomIntelliJPlatformVersionAware) {
            val dependenciesExtension = dependencies.the<IntelliJPlatformDependenciesExtension>()
            val baseIntelliJPlatformLocalConfiguration = configurations[Configurations.INTELLIJ_PLATFORM_LOCAL]

            with(configurations) {
                val customIntelliJPlatformLocalConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM_LOCAL + suffix,
                    description = "Custom IntelliJ Platform local",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    dependenciesExtension.local(
                        localPath = localPath,
                        configurationName = name,
                    )
                }
                val customIntelliJPlatformDependencyConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY + suffix,
                    description = "Custom IntelliJ Platform dependency archive",
                ) {
                    dependenciesExtension.customCreate(
                        type = type,
                        version = version,
                        configurationName = name,
                    )
                }

                /**
                 * Override the default [Configurations.INTELLIJ_PLATFORM] with a custom IntelliJ Platform configuration so the current task can refer to it.
                 * Depending on whether [CustomIntelliJPlatformVersionAware.localPath] or any of [CustomIntelliJPlatformVersionAware.type]
                 * and [CustomIntelliJPlatformVersionAware.version] is set, a custom configuration is picked as a replacement.
                 * Otherwise, refer to the base IntelliJ Platform — useful, i.e., when we want to execute a regular [RunIdeTask] using defaults.
                 */
                intelliJPlatformConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM + suffix,
                    description = "Custom IntelliJ Platform",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    defaultDependencies {
                        addAllLater(provider {
                            listOf(
                                customIntelliJPlatformLocalConfiguration,
                                customIntelliJPlatformDependencyConfiguration,
                                baseIntelliJPlatformLocalConfiguration,
                            ).flatMap { it.dependencies }.take(1)
                        })
                    }
                    defaultDependencies {
                        addAllLater(provider {
                            customIntelliJPlatformDependencyConfiguration.dependencies
                        })
                    }
                }

                /**
                 * Creates a custom [Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY]
                 * to hold additional plugins to be added to the [CustomIntelliJPlatformVersionAware] task.
                 *
                 * This configuration is also added to the global [Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY_COLLECTOR] registry
                 * so the [ExtractorTransformer] could track and extract custom plugin artifacts.
                 */
                val intellijPlatformPluginDependencyConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY + suffix,
                    description = "Custom IntelliJ Platform plugin dependencies",
                ) {
                    configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY_COLLECTOR].extendsFrom(this)
                    extendsFrom(configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY])
                }

                /**
                 * Creates a custom [Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL]
                 * to hold additional local plugins to be added to the [CustomIntelliJPlatformVersionAware] task.
                 */
                val intellijPlatformPluginLocalConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL + suffix,
                    description = "Custom IntelliJ Platform plugin local",
                ) {
                    extendsFrom(configurations[Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL])
                }

                /**
                 * A custom general [Configurations.INTELLIJ_PLATFORM_PLUGIN] configuration that collects custom plugin local and remote dependencies.
                 * This configuration also inherits from the general configurations to align the build setup.
                 */
                val intellijPlatformPluginConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM_PLUGIN + suffix,
                    description = "Custom IntelliJ Platform plugins",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    extendsFrom(intellijPlatformPluginDependencyConfiguration)
                    extendsFrom(intellijPlatformPluginLocalConfiguration)
                }

                intelliJPlatformPluginConfiguration.apply {
                    setFrom(intellijPlatformPluginConfiguration)
                    finalizeValueOnRead()
                }

                IntelliJPlatformPluginsExtension.register(project, target = this@task).let {
                    it.intellijPlatformPluginDependencyConfigurationName = intellijPlatformPluginDependencyConfiguration.name
                    it.intellijPlatformPluginLocalConfigurationName = intellijPlatformPluginLocalConfiguration.name
                }
            }
        }

        /**
         * It lets tasks use the sandbox directories, i.e., to run a guest IDE instance or execute various tests.
         */
        if (this is SandboxAware) {
            /**
             * multiple [PrepareSandboxTask] tasks may be registered for different purposes — running tests or IDE.
             * To keep sandboxes separated, we introduce sandbox suffixes.
             */
            sandboxSuffix.convention(suffix)
            sandboxDirectory.convention(extension.sandboxContainer.map { container ->
                container.dir("${productInfo.productCode}-${productInfo.version}")
            })
            sandboxConfigDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.CONFIG)
            sandboxPluginsDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.PLUGINS)
            sandboxSystemDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.SYSTEM)
            sandboxLogDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.LOG)

            /**
             * Some tasks are designed to work with the sandbox, so we explicitly make them depend on the [PrepareSandboxTask] task.
             * This also handles [CustomIntelliJPlatformVersionAware] tasks, for which we refer to the `suffix` variable.
             * No suffix is used if a task is a base task provided with the IntelliJ Platform Gradle Plugin as a default.
             */
            if (this !is SandboxProducerAware) {
                fun createTask(target: SplitModeTarget): PrepareSandboxTask {
                    val taskSubject = when {
                        this is TestableAware -> "Test"
                        else -> ""
                    }
                    val splitModeVariant = when (target) {
                        SplitModeTarget.FRONTEND -> "Frontend"
                        SplitModeTarget.BACKEND -> ""
                        SplitModeTarget.BOTH -> ""
                    }
                    val taskName = "prepare" + taskSubject + splitModeVariant + "Sandbox" + suffix

                    return tasks.maybeCreate<PrepareSandboxTask>(taskName).also { task ->
                        val taskSubjectSuffix = "-$taskSubject".lowercase().trimEnd('-') + suffix
                        task.sandboxSuffix.convention(taskSubjectSuffix)
                        sandboxSuffix.convention(taskSubjectSuffix)
                        sandboxDirectory.convention(task.sandboxDirectory)

                        if (this is CustomIntelliJPlatformVersionAware) {
                            task.disabledPlugins = the<IntelliJPlatformPluginsExtension>().disabled
                        }

                        if (this is SplitModeAware) {
                            task.sandboxSuffix = "-$taskSubject".lowercase().trimEnd('-') + "-$splitModeVariant".lowercase().trimEnd('-') + suffix
                            task.splitMode = splitMode
                            task.splitModeTarget = splitModeTarget
                            task.splitModeCurrentTarget = target
                        }

                        task.onlyIf {
                            it as PrepareSandboxTask
                            val isSplitMode = it.splitMode.get()
                            val currentTarget = task.splitModeCurrentTarget.get()

                            isSplitMode || currentTarget != SplitModeTarget.FRONTEND
                        }

                        dependsOn(task)
                    }
                }

                /**
                 * Every [SandboxAware] task is supposed to refer to the [PrepareSandboxTask] task.
                 * However, tasks like [RunnableIdeAware] or [TestableAware] should not share the same sandboxes.
                 * The same applies to the customized tasks – a custom suffix is added to the task name.
                 */
                when {
                    this is SplitModeAware -> {
                        val backendTask = createTask(SplitModeTarget.BACKEND)
                        val frontendTask = createTask(SplitModeTarget.FRONTEND)

                        backendTask.dependsOn(frontendTask)
                    }

                    else -> {
                        createTask(SplitModeTarget.BOTH)
                    }
                }
            }
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
                localPath = extension.verifyPlugin.cliPath,
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
                localPath = extension.signing.cliPath,
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
            javaCompilerConfiguration = configurations[Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER].asLenient
        }

        /**
         * The [SplitModeAware] allows to run IDE in Split Mode.
         */
        if (this is SplitModeAware) {
            splitMode.convention(extension.splitMode)
            splitModeTarget.convention(extension.splitModeTarget)
        }

        /**
         * The [SplitModeAware] allows to auto-reload plugin when run in the IDE.
         */
        if (this is AutoReloadAware) {
            autoReload.convention(extension.autoReload)
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
            kotlinxCoroutinesLibraryPresent.convention(project.provider {
                listOf(
                    Configurations.External.IMPLEMENTATION,
                    Configurations.External.COMPILE_ONLY,
                ).any { configurationName ->
                    project.configurations[configurationName].dependencies.any {
                        it.group == "org.jetbrains.kotlinx" && it.name.startsWith("kotlinx-coroutines")
                    }
                }
            })

            kotlinPluginAvailable.convention(project.provider {
                project.pluginManager.hasPlugin(Plugins.External.KOTLIN)
            })

            project.pluginManager.withPlugin(Plugins.External.KOTLIN) {
                val kotlinOptionsProvider = project.tasks.named(Tasks.External.COMPILE_KOTLIN).apply {
                    configure {
                        this@task.dependsOn(this)
                    }
                }.map {
                    it.withGroovyBuilder { getProperty("kotlinOptions") }
                        .withGroovyBuilder { getProperty("options") }
                }

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
                kotlinVersion.convention(project.provider {
                    project.extensions.getByName("kotlin")
                        .withGroovyBuilder { getProperty("coreLibrariesVersion") as String }
                })
                kotlinStdlibDefaultDependency.convention(
                    project.providers
                        .gradleProperty(GradleProperties.KOTLIN_STDLIB_DEFAULT_DEPENDENCY)
                        .map { it.toBoolean() }
                )
            }
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
