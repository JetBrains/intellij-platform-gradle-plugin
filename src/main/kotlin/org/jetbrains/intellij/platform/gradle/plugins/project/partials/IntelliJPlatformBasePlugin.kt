// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project.partials

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.artifacts.transform.BundledPluginsListTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.CollectorTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.ExtractorTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.PluginVerifierIdeExtractorTransformer
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.plugins.checkGradleVersion
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.*
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories

abstract class IntelliJPlatformBasePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.BASE}")

        checkGradleVersion()

        val rootProjectDirectory = project.rootProject.rootDir.toPath().absolute()

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
                module.excludeDirs.add(rootProjectDirectory.resolve(CACHE_DIRECTORY).toFile())
            }
        }

        val extensionProvider = project.provider { project.the<IntelliJPlatformExtension>() }

        with(project.configurations) configurations@{
            val intellijPlatformDependencyConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
                description = "IntelliJ Platform dependency archive",
            )
            create(
                name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY_COLLECTOR,
                description = "IntelliJ Platform dependencies internal collector",
            ) {
                extendsFrom(intellijPlatformDependencyConfiguration)
            }
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
                        0 -> "No IntelliJ Platform dependency found."
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
            create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY_COLLECTOR,
                description = "IntelliJ Platform plugin dependencies internal collector",
            ) {
                extendsFrom(intellijPlatformPluginDependenciesConfiguration)
            }
            val intellijPlatformPluginLocalConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
                description = "IntelliJ Platform plugin local",
            )
            val intellijPlatformPluginConfiguration = create(
                name = Configurations.INTELLIJ_PLATFORM_PLUGIN,
                description = "IntelliJ Platform plugins",
            ) {
                attributes {
                    attribute(Attributes.extracted, true)
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

                incoming.beforeResolve {
                    if (dependencies.isEmpty()) {
                        throw GradleException(
                            """
                            No IDE provided for running verification with the IntelliJ Plugin Verifier.
                            Please ensure the `intellijPlatform.verifyPlugin.ides` extension block is configured.
                            See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-verifyPlugin-ides
                            """.trimIndent()
                        )
                    }
                }
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
                coordinates = project.provider {
                    mapOf(
                        Attributes.AttributeType.INTELLIJ_PLATFORM to Configurations.INTELLIJ_PLATFORM,
                        Attributes.AttributeType.INTELLIJ_PLATFORM_PLUGIN to Configurations.INTELLIJ_PLATFORM_PLUGIN,
                        Attributes.AttributeType.JETBRAINS_RUNTIME to Configurations.JETBRAINS_RUNTIME,
                    ).mapValues { project.configurations[it.value].allDependencies }
                },
                compileClasspathConfiguration = project.configurations[Configurations.External.COMPILE_CLASSPATH],
                testCompileClasspathConfiguration = project.configurations[Configurations.External.TEST_COMPILE_CLASSPATH],
            )
            CollectorTransformer.register(
                dependencies = this,
                compileClasspathConfiguration = project.configurations[Configurations.External.COMPILE_CLASSPATH],
                testCompileClasspathConfiguration = project.configurations[Configurations.External.TEST_COMPILE_CLASSPATH],
            )
            BundledPluginsListTransformer.register(
                dependencies = this
            )
            PluginVerifierIdeExtractorTransformer.register(
                dependencies = this,
                intellijPluginVerifierIdesDependencyConfiguration = project.configurations[Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY],
                downloadDirectoryProvider = extensionProvider.flatMap { it.verifyPlugin.downloadDirectory },
            )

            project.pluginManager.withPlugin(Plugins.External.JAVA_TEST_FIXTURES) {
                project.configurations[Configurations.TEST_FIXTURES_COMPILE_CLASSPATH]
                    .attributes
                    .attribute(Attributes.extracted, true)
                    .attribute(Attributes.collected, true)
            }
        }

        project.configureExtension<IntelliJPlatformExtension>(
            Extensions.INTELLIJ_PLATFORM,
            project.configurations,
            project.providers,
            rootProjectDirectory,
        ) {
            autoReload.convention(true)
            buildSearchableOptions.convention(true)
            instrumentCode.convention(true)
            projectName.convention(project.name)
            sandboxContainer.convention(project.layout.buildDirectory.dir(Sandbox.CONTAINER))
            splitMode.convention(false)

            configureExtension<IntelliJPlatformExtension.PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION) {
                version.convention(project.provider { project.version.toString() })

                configureExtension<IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)
                configureExtension<IntelliJPlatformExtension.PluginConfiguration.IdeaVersion>(Extensions.IDEA_VERSION) {
                    val buildVersion = extensionProvider.map {
                        it.runCatching { productInfo.buildNumber.toVersion() }.getOrDefault(Version())
                    }
                    sinceBuild.convention(buildVersion.map { "${it.major}.${it.minor}" })
                    untilBuild.convention(buildVersion.map { "${it.major}.*" })
                }
                configureExtension<IntelliJPlatformExtension.PluginConfiguration.Vendor>(Extensions.VENDOR)
            }

            configureExtension<IntelliJPlatformExtension.VerifyPlugin>(Extensions.VERIFY_PLUGIN) {
                homeDirectory.convention(
                    project.providers
                        .systemProperty("plugin.verifier.home.dir")
                        .flatMap { project.layout.dir(project.provider { Path(it).toFile() }) }
                        .orElse(
                            project.providers.environmentVariable("XDG_CACHE_HOME")
                                .map { Path(it, "pluginVerifier").toFile() }
                                .let { project.layout.dir(it) }
                        )
                        .orElse(
                            project.providers.systemProperty("user.home")
                                .map { Path(it, ".cache/pluginVerifier").toFile() }
                                .let { project.layout.dir(it) }
                        )
                        .orElse(
                            project.layout.buildDirectory.dir("tmp/pluginVerifier")
                        )
                )
                downloadDirectory.convention(homeDirectory.dir("ides").map {
                    it.apply { asPath.createDirectories() }
                })
                failureLevel.convention(listOf(VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS))
                verificationReportsDirectory.convention(project.layout.buildDirectory.dir("reports/pluginVerifier"))
                verificationReportsFormats.convention(
                    listOf(
                        VerifyPluginTask.VerificationReportsFormats.PLAIN,
                        VerifyPluginTask.VerificationReportsFormats.HTML,
                    )
                )
                teamCityOutputFormat.convention(false)
                subsystemsToCheck.convention(VerifyPluginTask.Subsystems.ALL)

                configureExtension<IntelliJPlatformExtension.VerifyPlugin.Ides>(
                    Extensions.IDES,
                    project.configurations,
                    project.dependencies,
                    downloadDirectory,
                    extensionProvider,
                    project.providers,
                    project.resources,
                    rootProjectDirectory,
                )
            }

            configureExtension<IntelliJPlatformExtension.Signing>(Extensions.SIGNING)

            configureExtension<IntelliJPlatformExtension.Publishing>(Extensions.PUBLISHING) {
                host.convention(Locations.JETBRAINS_MARKETPLACE)
                ideServices.convention(false)
                channels.convention(listOf("default"))
                hidden.convention(false)
            }
        }

        project.dependencies.configureExtension<IntelliJPlatformDependenciesExtension>(
            Extensions.INTELLIJ_PLATFORM,
            project.configurations,
            project.repositories,
            project.settings.dependencyResolutionManagement.repositories,
            project.dependencies,
            project.providers,
            project.resources,
            project.objects,
            project.layout,
            rootProjectDirectory,
        )

        project.repositories.configureExtension<IntelliJPlatformRepositoriesExtension>(
            Extensions.INTELLIJ_PLATFORM,
            project.repositories,
            project.providers,
            rootProjectDirectory,
        )

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
