// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.initialization.resolve.RulesMode
import org.gradle.api.plugins.JavaLibraryPlugin
import org.gradle.kotlin.dsl.all
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.artifacts.transform.CollectorTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.ExtractorTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.LocalIvyArtifactPathComponentMetadataRule
import org.jetbrains.intellij.platform.gradle.artifacts.transform.LocalPluginsNormalizationTransformers
import org.jetbrains.intellij.platform.gradle.extensions.*
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension.*
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension.PluginConfiguration.*
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.plugins.checkGradleVersion
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.*
import kotlin.io.path.absolute
import kotlin.io.path.invariantSeparatorsPathString

abstract class IntelliJPlatformBasePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.BASE}")

        checkGradleVersion()

        with(project.plugins) {
            // https://docs.gradle.org/current/userguide/java_plugin.html
            // https://docs.gradle.org/current/userguide/java_library_plugin.html
            apply(JavaLibraryPlugin::class)
            // https://docs.gradle.org/current/userguide/idea_plugin.html
            apply(IdeaPlugin::class)
        }

        val dependenciesHelper = with(project) {
            IntelliJPlatformDependenciesHelper(configurations, dependencies, layout, objects, providers, resources, rootProjectPath, project.settings.dependencyResolutionManagement.rulesMode)
        }

        /**
         * Configure the [IdeaPlugin] to:
         * - set the `idea.module.downloadSources` flag to `true` to tell IDE that sources are required when working with IntelliJ Platform Gradle Plugin
         * - exclude the [CACHE_DIRECTORY] from the IDEA module
         */
        project.pluginManager.withPlugin(Plugins.External.IDEA) {
            project.extensions.configure<IdeaModel>(Plugins.External.IDEA) {
                module.isDownloadSources = project.providers[GradleProperties.DownloadSources].get()
                module.excludeDirs.add(project.rootProjectPath.resolve(CACHE_DIRECTORY).toFile())
            }
        }

        with(project.configurations) configurations@{
            val intellijPlatformDependencyConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY_ARCHIVE,
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
                name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
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

            /** Registers LocalIvyArtifactPathComponentMetadataRule */
            intellijPlatformConfiguration.incoming.afterResolve {
                val ruleName = LocalIvyArtifactPathComponentMetadataRule::class.simpleName
                // Intentionally delaying the check just in case if it changes somehow late in the lifecycle.
                val rulesMode = project.settings.dependencyResolutionManagement.rulesMode.get()

                if (RulesMode.PREFER_PROJECT == rulesMode) {
                    if (intellijPlatformConfiguration.resolvedConfiguration.hasError()) {
                        log.warn("Configuration '$intellijPlatformConfiguration' has some resolution errors. $ruleName will not be registered.")
                    } else if (intellijPlatformConfiguration.isEmpty) {
                        log.warn("Configuration '$intellijPlatformConfiguration' is empty. $ruleName will not be registered.")
                    } else {
                        val platformPath = intellijPlatformConfiguration.platformPath()
                        val artifactLocationPath = platformPath.absolute().normalize().invariantSeparatorsPathString
                        val ivyLocationPath = project.providers.localPlatformArtifactsPath(project.rootProjectPath).absolute().normalize().invariantSeparatorsPathString

                        project.dependencies.components.all<LocalIvyArtifactPathComponentMetadataRule> {
                            params(artifactLocationPath, ivyLocationPath)
                        }

                        log.info("$ruleName has been registered.")
                    }
                } else {
                    log.info("$ruleName can not be registered because '${rulesMode}' mode is used in settings.")
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
                    attribute(Attributes.extracted, false)
                }
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
            val intellijPlatformBundledModulesConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_BUNDLED_MODULES,
                description = "IntelliJ Platform bundled modules",
            )

            val jetbrainsRuntimeDependencyConfiguration = create(
                name = Configurations.JETBRAINS_RUNTIME_DEPENDENCY,
                description = "JetBrains Runtime dependency archive",
            ) {
                attributes {
                    attribute(Attributes.extracted, false)
                }

                defaultDependencies {
                    addLater(dependenciesHelper.obtainJetBrainsRuntimeVersion().map { version ->
                        dependenciesHelper.createJetBrainsRuntime(version)
                    })
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
            ) {
                defaultDependencies {
                    add(dependenciesHelper.createPluginVerifier())
                }
            }

            val intellijPluginVerifierIdesDependencyConfiguration = create(
                name = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY,
                description = "IntelliJ Plugin Verifier IDE dependencies",
            ) {
                attributes {
                    attribute(Attributes.extracted, false)
                }
            }
            val intellijPluginVerifierIdesLocalConfiguration = create(
                name = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_LOCAL_INSTANCE,
                description = "IntelliJ Plugin Verifier IDE local",
            ) {
                attributes {
                    attribute(Attributes.extracted, true)
                }
            }

            create(
                name = Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES,
                description = "IntelliJ Plugin Verifier IDEs",
            ) {
                attributes {
                    attribute(Attributes.extracted, true)
                }

                extendsFrom(intellijPluginVerifierIdesDependencyConfiguration)
                extendsFrom(intellijPluginVerifierIdesLocalConfiguration)
            }

            create(
                name = Configurations.MARKETPLACE_ZIP_SIGNER,
                description = "Marketplace ZIP Signer",
            ) {
                defaultDependencies {
                    add(dependenciesHelper.createMarketplaceZipSigner())
                }
            }

            val intellijPlatformDependenciesConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_DEPENDENCIES,
                description = "IntelliJ Platform extra dependencies",
            ) {
                extendsFrom(
                    intellijPlatformPluginConfiguration,
                    intellijPlatformBundledPluginsConfiguration,
                    intellijPlatformBundledModulesConfiguration,
                )
            }

            create(
                name = Configurations.INTELLIJ_PLATFORM_JAVA_COMPILER,
                description = "Java Compiler used by Ant tasks",
            ) {
                defaultDependencies {
                    add(dependenciesHelper.createJavaCompiler())
                }
            }

            val intellijPlatformTestDependenciesConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES,
                description = "IntelliJ Platform Test Dependencies"
            )
            create(
                name = Configurations.INTELLIJ_PLATFORM_CLASSPATH,
                description = "IntelliJ Platform Classpath resolvable configuration"
            )
            create(
                name = Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH,
                description = "IntelliJ Platform Test Classpath resolvable configuration"
            )
            create(
                name = Configurations.INTELLIJ_PLATFORM_RUNTIME_CLASSPATH,
                description = "IntelliJ Platform Runtime Classpath resolvable configuration"
            ) {
                attributes {
                    attributes.attribute(Attributes.kotlinJPlatformType, "jvm")
                }
            }

            this@configurations[Configurations.External.COMPILE_ONLY].extendsFrom(
                intellijPlatformConfiguration,
                intellijPlatformDependenciesConfiguration,
            )
            this@configurations[Configurations.External.TEST_IMPLEMENTATION].extendsFrom(
                intellijPlatformConfiguration,
                intellijPlatformDependenciesConfiguration,
                intellijPlatformTestDependenciesConfiguration,
            )
            this@configurations[Configurations.INTELLIJ_PLATFORM_CLASSPATH].extendsFrom(
                intellijPlatformConfiguration,
                intellijPlatformDependenciesConfiguration,
            )
            this@configurations[Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH].extendsFrom(
                intellijPlatformConfiguration,
                intellijPlatformDependenciesConfiguration,
                intellijPlatformTestDependenciesConfiguration,
            )
            this@configurations[Configurations.INTELLIJ_PLATFORM_RUNTIME_CLASSPATH].extendsFrom(
                this@configurations[Configurations.External.RUNTIME_CLASSPATH],
            )
            project.pluginManager.withPlugin(Plugins.External.JAVA_TEST_FIXTURES) {
                this@configurations[Configurations.External.TEST_FIXTURES_COMPILE_ONLY].extendsFrom(
                    intellijPlatformConfiguration,
                    intellijPlatformDependenciesConfiguration,
                    intellijPlatformTestDependenciesConfiguration,
                )
            }
        }

        with(project.dependencies) {
            attributesSchema {
                attribute(Attributes.collected)
                attribute(Attributes.extracted)
            }

            Attributes.ArtifactType.Archives.forEach {
                artifactTypes.maybeCreate(it.toString())
                    .attributes
                    .attribute(Attributes.extracted, false)
                    .attribute(Attributes.collected, false)
            }
            artifactTypes.maybeCreate(Attributes.ArtifactType.DIRECTORY.toString())
                .attributes
                .attribute(Attributes.extracted, true)
                .attribute(Attributes.collected, false)

            listOf(
                project.configurations[Configurations.External.COMPILE_CLASSPATH],
                project.configurations[Configurations.External.RUNTIME_CLASSPATH],
                project.configurations[Configurations.External.TEST_COMPILE_CLASSPATH],
                project.configurations[Configurations.External.TEST_RUNTIME_CLASSPATH],
                project.configurations[Configurations.INTELLIJ_PLATFORM_CLASSPATH],
                project.configurations[Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH],
            ).forEach {
                it.attributes
                    .attribute(Attributes.extracted, true)
                    .attribute(Attributes.collected, true)
            }

            ExtractorTransformer.register(this)
            CollectorTransformer.register(this, project.configurations[Configurations.INTELLIJ_PLATFORM_DEPENDENCY])
            LocalPluginsNormalizationTransformers.register(this)

            project.pluginManager.withPlugin(Plugins.External.JAVA_TEST_FIXTURES) {
                project.configurations[Configurations.External.TEST_FIXTURES_COMPILE_CLASSPATH]
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

            PluginVerification.register(project, target = intelliJPlatform).let { pluginVerification ->
                PluginVerification.Ides.register(dependenciesHelper, project.extensionProvider, target = pluginVerification)
            }

            Signing.register(project, target = intelliJPlatform)
            Publishing.register(project, target = intelliJPlatform)
        }

        IntelliJPlatformDependenciesExtension.register(dependenciesHelper, target = project.dependencies)
        IntelliJPlatformRepositoriesExtension.register(project, target = project.repositories)

        project.tasks.matching {
            when (it) {
                is AutoReloadAware,
                is CoroutinesJavaAgentAware,
                is IntelliJPlatformVersionAware,
                is JavaCompilerAware,
                is KotlinMetadataAware,
                is PluginAware,
                is PluginVerifierAware,
                is RunnableIdeAware,
                is RuntimeAware,
                is SandboxAware,
                is SigningAware,
                is SplitModeAware,
                is TestableAware,
                    -> true

                else -> false
            }
        }.configureEach(project::preconfigureTask)

        listOf(
            InitializeIntelliJPlatformPluginTask,
            PrintBundledPluginsTask,
            PrintProductsReleasesTask,
            SetupDependenciesTask,
        ).forEach {
            it.register(project)
        }
    }
}
