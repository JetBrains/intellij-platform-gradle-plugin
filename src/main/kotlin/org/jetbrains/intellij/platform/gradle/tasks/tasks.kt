// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.process.JavaExecSpec
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.argumentProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SandboxArgumentProvider
import org.jetbrains.intellij.platform.gradle.executableResolver.IntelliJPluginVerifierResolver
import org.jetbrains.intellij.platform.gradle.executableResolver.JetBrainsRuntimeResolver
import org.jetbrains.intellij.platform.gradle.executableResolver.MarketplaceZipSignerResolver
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.provider.ProductInfoValueSource
import org.jetbrains.intellij.platform.gradle.tasks.base.*
import java.util.*
import kotlin.io.path.createDirectories

internal inline fun <reified T : Task> Project.registerTask(vararg names: String, noinline configuration: T.() -> Unit = {}) {
    names.forEach { name ->
        info(null, "Configuring task: $name")
        tasks.maybeCreate<T>(name)
    }

    tasks.withType<T> {
        val extension = project.the<IntelliJPlatformExtension>()
        val suffix = UUID.randomUUID().toString().substring(0, 8)

        if (this is PlatformVersionAware) {
            intelliJPlatform.from(configurations.getByName(Configurations.INTELLIJ_PLATFORM))

            productInfo.set(providers.of(ProductInfoValueSource::class) {
                parameters.productInfoConfiguration.from(configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO))
            })
        }

        if (this is CoroutinesJavaAgentAware) {
            val initializeIntelliJPlatformPluginTaskProvider = tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

            coroutinesJavaAgentFile.convention(initializeIntelliJPlatformPluginTaskProvider.flatMap {
                it.coroutinesJavaAgent
            })

            dependsOn(initializeIntelliJPlatformPluginTaskProvider)
        }

        if (this is CustomPlatformVersionAware) {
            val defaultTypeProvider = provider {
                val productInfo = configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
                    .singleOrNull()
                    .throwIfNull { GradleException("IntelliJ Platform is not specified.") }
                    .toPath()
                    .productInfo()
                IntelliJPlatformType.fromCode(productInfo.productCode)
            }
            val defaultVersionProvider = provider {
                val productInfo = configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
                    .singleOrNull()
                    .throwIfNull { GradleException("IntelliJ Platform is not specified.") }
                    .toPath()
                    .productInfo()
                IdeVersion.createIdeVersion(productInfo.version).toString()
            }
// TODO: test
//            val productInfoProvider = provider {
//                configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
//                    .singleOrNull()
//                    .throwIfNull { GradleException("IntelliJ Platform is not specified.") }
//                    .toPath()
//                    .productInfo()
//            }
//            val defaultTypeProvider = productInfoProvider.map {
//                IntelliJPlatformType.fromCode(it.productCode)
//            }
//            val defaultVersionProvider = productInfoProvider.map {
//                IdeVersion.createIdeVersion(it.version).toString()
//            }
            val intellijPlatformDependencyConfiguration =
                configurations.create("${Configurations.INTELLIJ_PLATFORM_DEPENDENCY}_$suffix") {
                    // TODO: use ConfigurationContainer.create
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true

                    this@registerTask.dependencies
                        .the<IntelliJPlatformDependenciesExtension>()
                        .create(type.orElse(defaultTypeProvider), version.orElse(defaultVersionProvider), configurationName = name)
                }
            val intellijPlatformConfiguration = configurations.create("${Configurations.INTELLIJ_PLATFORM}_$suffix") {
                isVisible = false
                isCanBeConsumed = false
                isCanBeResolved = true

                attributes {
                    attribute(Attributes.extracted, true)
                }

                extendsFrom(intellijPlatformDependencyConfiguration)
            }
            val intellijPlatformProductInfoConfiguration =
                configurations.create("${Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO}_$suffix") {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true

                    attributes {
                        attribute(Attributes.productInfo, true)
                    }

                    extendsFrom(intellijPlatformConfiguration)
                }

            intelliJPlatform.from(provider {
                when {
                    type.isSpecified() || version.isSpecified() -> intellijPlatformConfiguration
                    else -> configurations.getByName(Configurations.INTELLIJ_PLATFORM)
                }
            })

            productInfo.set(providers.of(ProductInfoValueSource::class) {
                parameters.productInfoConfiguration.from(
                    when {
                        type.isSpecified() || version.isSpecified() -> intellijPlatformProductInfoConfiguration
                        else -> configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
                    }
                )
            })
        }

        if (this is SandboxAware) {
            val sandboxDirectoryProvider = extension.sandboxContainer.flatMap { container ->
                productInfo.map {
                    container
                        .dir("${it.productCode}-${it.version}")
                        .apply { asPath.createDirectories() }
                }
            }

            sandboxSuffix.convention(
                when {
                    this is PrepareSandboxTask -> when (name.substringBefore("_")) {
                        Tasks.PREPARE_TESTING_SANDBOX -> "-test"
                        Tasks.PREPARE_UI_TESTING_SANDBOX -> "-uiTest"
                        Tasks.PREPARE_SANDBOX -> ""
                        else -> ""
                    }

                    else -> ""
                }
            )
            sandboxDirectory.convention(sandboxDirectoryProvider)
            sandboxConfigDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.CONFIG)
            sandboxPluginsDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.PLUGINS)
            sandboxSystemDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.SYSTEM)
            sandboxLogDirectory.configureSandbox(sandboxDirectory, sandboxSuffix, Sandbox.LOG)

//            sandboxConfigDirectory.convention(sandboxDirectory.zip(sandboxSuffix) { container, sandboxSuffix ->
//                container.dir(Sandbox.CONFIG + sandboxSuffix).apply { asPath.createDirectories() }
//            })
//            sandboxPluginsDirectory.convention(sandboxDirectory.zip(sandboxSuffix) { container, sandboxSuffix ->
//                container.dir(Sandbox.PLUGINS + sandboxSuffix).apply { asPath.createDirectories() }
//            })
//            sandboxSystemDirectory.convention(sandboxDirectory.zip(sandboxSuffix) { container, sandboxSuffix ->
//                container.dir(Sandbox.SYSTEM + sandboxSuffix).apply { asPath.createDirectories() }
//            })
//            sandboxLogDirectory.convention(sandboxDirectory.zip(sandboxSuffix) { container, sandboxSuffix ->
//                container.dir(Sandbox.LOG + sandboxSuffix).apply { asPath.createDirectories() }
//            })

            if (this !is PrepareSandboxTask) {
                val isBuiltInTask = Tasks::class.java.declaredFields.any { it.get(null) == name }
                val prepareSandboxTaskName = when (this) {
                    is RunIdeTask -> Tasks.PREPARE_SANDBOX
                    is TestIdeTask -> Tasks.PREPARE_TESTING_SANDBOX
//                is TestUiIdeTask -> Tasks.PREPARE_UI_TESTING_SANDBOX
                    else -> Tasks.PREPARE_SANDBOX
                } + "_$suffix".takeUnless { isBuiltInTask }.orEmpty()

                val prepareSandboxTask = tasks.maybeCreate<PrepareSandboxTask>(prepareSandboxTaskName)
                dependsOn(prepareSandboxTask)
            }
        }

        if (this is JetBrainsRuntimeAware) {
            val jbrResolver = JetBrainsRuntimeResolver(
                jetbrainsRuntime = configurations.getByName(Configurations.JETBRAINS_RUNTIME),
                intellijPlatform = intelliJPlatform,
                javaToolchainSpec = project.the<JavaPluginExtension>().toolchain,
                javaToolchainService = project.serviceOf<JavaToolchainService>(),
            )

            jetbrainsRuntimeDirectory.convention(layout.dir(provider {
                jbrResolver.resolveDirectory()?.toFile()
            }))
            jetbrainsRuntimeExecutable.convention(layout.file(provider {
                jbrResolver.resolveExecutable()?.toFile()
            }))
        }

        if (this is TestIdeTask) {
            executable(jetbrainsRuntimeExecutable)
        }

        if (this is PluginVerifierAware) {
            // TODO: test if no PV dependency is added to the project
            val pluginVerifierResolver = IntelliJPluginVerifierResolver(
                intellijPluginVerifier = configurations.getByName(Configurations.INTELLIJ_PLUGIN_VERIFIER),
                localPath = extension.pluginVerifier.cliPath,
            )

            pluginVerifierDirectory.convention(layout.dir(provider {
                pluginVerifierResolver.resolveDirectory().toFile()
            }))
            pluginVerifierExecutable.convention(layout.file(provider {
                pluginVerifierResolver.resolveExecutable().toFile()
            }))
        }

        if (this is SigningAware) {
            // TODO: test if no ZIP Signer dependency is added to the project
            val marketplaceZipSignerResolver = MarketplaceZipSignerResolver(
                marketplaceZipSigner = configurations.getByName(Configurations.MARKETPLACE_ZIP_SIGNER),
                localPath = extension.signing.cliPath,
            )

            zipSignerDirectory.convention(layout.dir(provider {
                marketplaceZipSignerResolver.resolveDirectory()?.toFile()
            }))
            zipSignerExecutable.convention(layout.file(provider {
                marketplaceZipSignerResolver.resolveExecutable()?.toFile()
            }))
        }

        if (this is RunnableIdeAware) {
            enableAssertions = true

            jvmArgumentProviders.add(IntelliJPlatformArgumentProvider(intelliJPlatform, coroutinesJavaAgentFile, this))

            jvmArgumentProviders.add(
                SandboxArgumentProvider(
                    sandboxConfigDirectory,
                    sandboxPluginsDirectory,
                    sandboxSystemDirectory,
                    sandboxLogDirectory,
                )
            )


//                outputs.dir(sandboxSystemDirectory)
//                    .withPropertyName("System directory")
//                inputs.dir(sandboxConfigDirectory)
//                    .withPropertyName("Config Directory")
//                    .withPathSensitivity(PathSensitivity.RELATIVE)
//                inputs.files(sandboxPluginsDirectory)
//                    .withPropertyName("Plugins directory")
//                    .withPathSensitivity(PathSensitivity.RELATIVE)
//                    .withNormalizer(ClasspathNormalizer::class)

            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")

            if (this is JavaExecSpec) {
                mainClass.set("com.intellij.idea.Main")
            }
        }
    }

    tasks.withType<T>(configuration)
}

// TODO: migrate to `project.resources.binary` whenever it's available. Ref: https://github.com/gradle/gradle/issues/25237
internal fun Project.resolveResourceFromUrl(url: String) =
    resources.text
        .fromUri(url)
        .runCatching { asFile("UTF-8") }
        .onFailure { logger.error("Cannot resolve product releases", it) }
        .getOrDefault("<products />")

internal fun JavaForkOptions.systemPropertyDefault(name: String, defaultValue: Any) {
    if (!systemProperties.containsKey(name)) {
        systemProperty(name, defaultValue)
    }
}

internal fun Directory.create() = also { asPath.createDirectories() }


internal fun DirectoryProperty.configureSandbox(directory: DirectoryProperty, suffix: Provider<String>, path: String) {
    this.convention(directory.zip(suffix) { container, sfx ->
        container.dir(path + sfx).apply { asPath.createDirectories() }
    })
}
