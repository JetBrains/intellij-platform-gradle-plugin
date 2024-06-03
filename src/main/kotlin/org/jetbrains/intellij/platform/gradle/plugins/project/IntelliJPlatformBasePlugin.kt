// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.artifacts.transform.*
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension.*
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension.PluginConfiguration.*
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.plugins.checkGradleVersion
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.create
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath

abstract class IntelliJPlatformBasePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.BASE}")

        checkGradleVersion()

        with(project.plugins) {
            apply(JavaPlugin::class)
            apply(IdeaPlugin::class)
        }

        /**
         * Configure the [IdeaPlugin] to:
         * - set the `idea.module.downloadSources` flag to `true` to tell IDE that sources are required when working with IntelliJ Platform Gradle Plugin
         * - exclude the [CACHE_DIRECTORY] from the IDEA module
         */
        project.pluginManager.withPlugin("idea") {
            project.extensions.configure<IdeaModel>("idea") {
                module.isDownloadSources = BuildFeature.DOWNLOAD_SOURCES.isEnabled(project.providers).get()
                module.excludeDirs.add(project.rootProjectPath.resolve(CACHE_DIRECTORY).toFile())
            }
        }

        with(project.configurations) configurations@{
            val intellijPlatformDependencyConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
                description = "IntelliJ Platform dependency archive",
            )
            val intellijPlatformLocalConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_LOCAL,
                description = "IntelliJ Platform local",
            ) {
                attributes {
                    attribute(Attributes.extracted, true)
                }
            }

            val intellijPlatformConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM,
                description = "IntelliJ Platform",
            ) {
                attributes {
                    attribute(Attributes.extracted, true)
                }

                extendsFrom(
                    intellijPlatformDependencyConfiguration,
                    intellijPlatformLocalConfiguration,
                )

                incoming.beforeResolve {
                    val message = when (dependencies.size) {
                        0 -> null
                        1 -> null
                        else -> "More than one IntelliJ Platform dependencies found."
                    } ?: return@beforeResolve

                    throw GradleException(
                        """
                        $message
                        Please ensure there is a single IntelliJ Platform dependency defined in your project and that the necessary repositories, where it can be located, are added.
                        See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
                        """.trimIndent()
                    )
                }
            }

            val intellijPlatformPluginDependenciesConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY,
                description = "IntelliJ Platform plugin dependencies",
            )
            val intellijPlatformPluginLocalConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
                description = "IntelliJ Platform plugin local",
            ) {
                attributes {
                    attribute(Attributes.localPluginsNormalized, false)
                }
            }
            val intellijPlatformPluginModuleConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_MODULE,
                description = "IntelliJ Platform plugin module",
            ) {
                isTransitive = false
            }
            create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY_COLLECTOR,
                description = "IntelliJ Platform plugin dependencies internal collector",
            ) {
                extendsFrom(intellijPlatformPluginDependenciesConfiguration)
                extendsFrom(intellijPlatformPluginLocalConfiguration)
            }
            val intellijPlatformPluginConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN,
                description = "IntelliJ Platform plugins",
            ) {
                attributes {
                    attribute(Attributes.extracted, true)
                    attribute(Attributes.localPluginsNormalized, true)
                }

                extendsFrom(intellijPlatformPluginDependenciesConfiguration)
                extendsFrom(intellijPlatformPluginLocalConfiguration)
            }

            val intellijPlatformBundledPluginsConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
                description = "IntelliJ Platform bundled plugins",
            )

            create(
                name = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST,
                description = "IntelliJ Platform bundled plugins list",
            ) {
                attributes {
                    attribute(Attributes.bundledPluginsList, true)
                }

                extendsFrom(intellijPlatformConfiguration)
            }

            val jetbrainsRuntimeDependencyConfiguration = create(
                name = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
                description = "JetBrains Runtime dependency archive",
            ) {
                attributes {
                    attribute(Attributes.extracted, false)
                }
            }

            val jetbrainsRuntimeLocalConfiguration = create(
                name = Configurations.JETBRAINS_RUNTIME_LOCAL_INSTANCE,
                description = "JetBrains Runtime local instance",
            ) {
                attributes {
                    attribute(Attributes.extracted, true)
                }
            }

            create(
                name = Configurations.JETBRAINS_RUNTIME,
                description = "JetBrains Runtime",
            ) {
                attributes {
                    attribute(Attributes.extracted, true)
                }

                extendsFrom(jetbrainsRuntimeDependencyConfiguration)
                extendsFrom(jetbrainsRuntimeLocalConfiguration)
            }

            create(
                name = Configurations.INTELLIJ_PLUGIN_VERIFIER,
                description = "IntelliJ Plugin Verifier",
            )

            val intellijPluginVerifierIdesDependencyConfiguration = create(
                name = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                description = "IntelliJ Plugin Verifier IDE dependencies",
            ) {
                attributes {
                    attribute(Attributes.binaryReleaseExtracted, false)
                }
            }
            val intellijPluginVerifierIdesLocalConfiguration = create(
                name = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE,
                description = "IntelliJ Plugin Verifier IDE local",
            ) {
                attributes {
                    attribute(Attributes.binaryReleaseExtracted, true)
                }
            }

            create(
                name = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES,
                description = "IntelliJ Plugin Verifier IDEs",
            ) {
                attributes {
                    attribute(Attributes.binaryReleaseExtracted, true)
                }

                extendsFrom(intellijPluginVerifierIdesDependencyConfiguration)
                extendsFrom(intellijPluginVerifierIdesLocalConfiguration)
            }

            create(
                name = Configurations.MARKETPLACE_ZIP_SIGNER,
                description = "Marketplace ZIP Signer",
            )

            val intellijPlatformDependenciesConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
                description = "IntelliJ Platform extra dependencies",
            ) {
                extendsFrom(
                    intellijPlatformPluginConfiguration,
                    intellijPlatformPluginModuleConfiguration,
                    intellijPlatformBundledPluginsConfiguration,
                )
            }

            create(
                name = Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER,
                description = "Java Compiler used by Ant tasks",
            )

            val intellijPlatformTestDependenciesConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
                description = "IntelliJ Platform Test Dependencies"
            )

            this@configurations[Configurations.External.COMPILE_ONLY].extendsFrom(
                intellijPlatformConfiguration,
                intellijPlatformDependenciesConfiguration,
            )
            this@configurations[Configurations.External.TEST_COMPILE_ONLY].extendsFrom(
                intellijPlatformConfiguration,
                intellijPlatformDependenciesConfiguration,
                intellijPlatformTestDependenciesConfiguration,
            )
            project.pluginManager.withPlugin(Plugins.External.JAVA_TEST_FIXTURES) {
                this@configurations[Configurations.TEST_FIXTURES_COMPILE_ONLY].extendsFrom(
                    intellijPlatformConfiguration,
                    intellijPlatformDependenciesConfiguration,
                    intellijPlatformTestDependenciesConfiguration,
                )
            }
        }

        with(project.dependencies) {
            attributesSchema {
                attribute(Attributes.bundledPluginsList)
                attribute(Attributes.collected)
                attribute(Attributes.extracted)
            }

            ExtractorTransformer.register(
                dependencies = this,
                compileClasspathConfiguration = project.configurations[Configurations.External.COMPILE_CLASSPATH],
                testCompileClasspathConfiguration = project.configurations[Configurations.External.TEST_COMPILE_CLASSPATH],
            )
            CollectorTransformer.register(
                dependencies = this,
                compileClasspathConfiguration = project.configurations[Configurations.External.COMPILE_CLASSPATH],
                testCompileClasspathConfiguration = project.configurations[Configurations.External.TEST_COMPILE_CLASSPATH],
                intellijPlatformConfiguration = project.configurations[Configurations.INTELLIJ_PLATFORM],
            )
            BundledPluginsListTransformer.register(
                dependencies = this
            )
            LocalPluginsNormalizationTransformers.register(
                dependencies = this
            )
            PluginVerifierIdeExtractorTransformer.register(
                dependencies = this,
                intellijPluginVerifierIdesDependencyConfiguration = project.configurations[Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY],
                downloadDirectoryProvider = project.extensionProvider.flatMap { it.verifyPlugin.downloadDirectory },
            )

            project.pluginManager.withPlugin(Plugins.External.JAVA_TEST_FIXTURES) {
                project.configurations[Configurations.TEST_FIXTURES_COMPILE_CLASSPATH]
                    .attributes
                    .attribute(Attributes.extracted, true)
                    .attribute(Attributes.collected, true)
            }
        }

        IntelliJPlatformExtension.register(project, target = project).let { intelliJPlatform ->
            PluginConfiguration.register(project, target = intelliJPlatform).let { pluginConfiguration ->
                ProductDescriptor.register(project, target = pluginConfiguration)
                IdeaVersion.register(project, target = pluginConfiguration)
                Vendor.register(project, target = pluginConfiguration)
            }

            VerifyPlugin.register(project, target = intelliJPlatform).let { verifyPlugin ->
                VerifyPlugin.Ides.register(project, target = verifyPlugin)
            }

            Signing.register(project, target = intelliJPlatform)
            Publishing.register(project, target = intelliJPlatform)
        }

        IntelliJPlatformDependenciesExtension.register(project, target = project.dependencies)
        IntelliJPlatformRepositoriesExtension.register(project, target = project.repositories)

        project.tasks.whenIntelliJPlatformTaskAdded {
            project.preconfigureTask(this)
        }

        InitializeIntelliJPlatformPluginTask.register(project)

        val initializeIntelliJPlatformPluginTaskProvider =
            project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

        project.tasks.whenIntelliJPlatformTaskAdded {
            dependsOn(initializeIntelliJPlatformPluginTaskProvider)
        }

        listOf(
            PrintBundledPluginsTask,
            PrintProductsReleasesTask,
            SetupDependenciesTask,
        ).forEach {
            it.register(project)
        }
    }

    @Suppress("KotlinConstantConditions")
    private fun TaskContainer.whenIntelliJPlatformTaskAdded(action: Task.() -> Unit) =
        whenTaskAdded {
            when (this) {
                is AutoReloadAware,
                is CoroutinesJavaAgentAware,
                is CustomIntelliJPlatformVersionAware,
                is IntelliJPlatformVersionAware,
                is JavaCompilerAware,
                is KotlinMetadataAware,
                is PluginAware,
                is PluginVerifierAware,
                is RunnableIdeAware,
                is RuntimeAware,
                is SandboxAware,
                is SandboxProducerAware,
                is SigningAware,
                is SplitModeAware,
                is TestableAware,
                    -> action()
            }
        }
}
