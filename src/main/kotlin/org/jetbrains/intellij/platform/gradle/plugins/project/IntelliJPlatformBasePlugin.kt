// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.*
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.the
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.JAVA_TEST_FIXTURES_PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.PLUGIN_BASE_ID
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.artifacts.transform.applyBundledPluginsListTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.applyCollectorTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.applyExtractorTransformer
import org.jetbrains.intellij.platform.gradle.artifacts.transform.applyPluginVerifierIdeExtractorTransformer
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.isBuildFeatureEnabled
import org.jetbrains.intellij.platform.gradle.plugins.checkGradleVersion
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.create
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

abstract class IntelliJPlatformBasePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: $PLUGIN_BASE_ID")

        checkGradleVersion()

        val rootProjectDirectory = project.rootProject.rootDir.toPath()

        with(project) {
            with(plugins) {
                apply(JavaPlugin::class)
                apply(IdeaPlugin::class)
            }

            /**
             * Configure the [IdeaPlugin] to:
             * - set the `idea.module.downloadSources` flag to `true` to tell IDE that sources are required when working with IntelliJ Platform Gradle Plugin
             * - exclude the [CACHE_DIRECTORY] from the IDEA module
             */
            pluginManager.withPlugin("idea") {
                project.extensions.configure<IdeaModel>("idea") {
                    module.isDownloadSources = isBuildFeatureEnabled(BuildFeature.DOWNLOAD_SOURCES).get()
                    module.excludeDirs.add(rootProjectDirectory.resolve(CACHE_DIRECTORY).toFile())
                }
            }

            val extensionProvider = provider { project.the<IntelliJPlatformExtension>() }

            with(configurations) {
                val intellijPlatformDependencyConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM_DEPENDENCY,
                    description = "IntelliJ Platform dependency archive",
                )

                val intellijPlatformLocalConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM_LOCAL_INSTANCE,
                    description = "IntelliJ Platform local instance",
                )

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
                        if (dependencies.isEmpty()) {
                            throw GradleException("No IntelliJ Platform dependency found")
                        }

                        val identifiers = IntelliJPlatformType.values().mapNotNull { it.dependency?.toString() }
                        val matched = dependencies.filter { identifiers.contains(it.toString()) }
                        if (matched.size > 1) {
                            throw GradleException(
                                matched.joinToString(
                                    prefix = "Conflicting dependencies detected: \n",
                                    separator = "\n",
                                )
                            )
                        }
                    }
                }

                val intellijPlatformPluginsConfiguration = create(
                    name = Configurations.INTELLIJ_PLATFORM_PLUGINS,
                    description = "IntelliJ Platform plugins",
                )
                val intellijPlatformPluginsConfigurationExtracted = create(
                    name = Configurations.INTELLIJ_PLATFORM_PLUGINS_EXTRACTED,
                    description = "IntelliJ Platform plugins extracted",
                ) {
                    attributes {
                        attribute(Attributes.extracted, true)
                    }

                    extendsFrom(intellijPlatformPluginsConfiguration)
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
                    name = Configurations.JETBRAINS_RUNTIME_LOCAL_INSTANCE, description = "JetBrains Runtime local instance"
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
                        intellijPlatformPluginsConfigurationExtracted,
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

                configurations[COMPILE_ONLY_CONFIGURATION_NAME].extendsFrom(
                    intellijPlatformConfiguration,
                    intellijPlatformDependenciesConfiguration,
                )
                configurations[TEST_COMPILE_ONLY_CONFIGURATION_NAME].extendsFrom(
                    intellijPlatformConfiguration,
                    intellijPlatformDependenciesConfiguration,
                    intellijPlatformTestDependenciesConfiguration,
                )
                pluginManager.withPlugin(JAVA_TEST_FIXTURES_PLUGIN_ID) {
                    configurations[Configurations.TEST_FIXTURES_COMPILE_ONLY].extendsFrom(
                        intellijPlatformConfiguration,
                        intellijPlatformDependenciesConfiguration,
                        intellijPlatformTestDependenciesConfiguration,
                    )
                }
            }

            with(dependencies) {
                attributesSchema {
                    attribute(Attributes.bundledPluginsList)
                    attribute(Attributes.collected)
                    attribute(Attributes.extracted)
                }

                applyExtractorTransformer(
                    configurations[COMPILE_CLASSPATH_CONFIGURATION_NAME],
                    configurations[TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME],
                    configurations[Configurations.INTELLIJ_PLATFORM_DEPENDENCY],
                    configurations[Configurations.JETBRAINS_RUNTIME_DEPENDENCY],
                    configurations[Configurations.INTELLIJ_PLATFORM_PLUGINS],
                )
                applyCollectorTransformer(
                    configurations[COMPILE_CLASSPATH_CONFIGURATION_NAME],
                    configurations[TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME],
                )
                applyBundledPluginsListTransformer()
                applyPluginVerifierIdeExtractorTransformer(
                    configurations[Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY],
                    extensionProvider.flatMap { it.verifyPlugin.downloadDirectory },
                )

                pluginManager.withPlugin(JAVA_TEST_FIXTURES_PLUGIN_ID) {
                    configurations[Configurations.TEST_FIXTURES_COMPILE_CLASSPATH]
                        .attributes
                        .attribute(Attributes.extracted, true)
                        .attribute(Attributes.collected, true)
                }
            }

            configureExtension<IntelliJPlatformExtension>(
                Extensions.INTELLIJ_PLATFORM,
                project.configurations,
                project.providers,
                rootProjectDirectory,
            ) {
                buildSearchableOptions.convention(true)
                instrumentCode.convention(true)
                projectName.convention(project.name)
                sandboxContainer.convention(project.layout.buildDirectory.dir(Sandbox.CONTAINER))

                configureExtension<IntelliJPlatformExtension.PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION) {
                    version.convention(project.provider { project.version.toString() })

                    configureExtension<IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)
                    configureExtension<IntelliJPlatformExtension.PluginConfiguration.IdeaVersion>(Extensions.IDEA_VERSION) {
                        val buildVersion = extensionProvider.map {
                            it.productInfo.buildNumber.toVersion()
                        }
                        sinceBuild.convention(buildVersion.map { "${it.major}.${it.minor}" })
                        untilBuild.convention(buildVersion.map { "${it.major}.*" })
                    }
                    configureExtension<IntelliJPlatformExtension.PluginConfiguration.Vendor>(Extensions.VENDOR)
                }

                configureExtension<IntelliJPlatformExtension.VerifyPlugin>(Extensions.VERIFY_PLUGIN) {
                    homeDirectory.convention(
                        providers
                            .systemProperty("plugin.verifier.home.dir")
                            .flatMap { layout.dir(provider { Path(it).toFile() }) }
                            .orElse(
                                providers.environmentVariable("XDG_CACHE_HOME")
                                    .map { Path(it, "pluginVerifier").toFile() }
                                    .let { layout.dir(it) }
                            )
                            .orElse(
                                providers.systemProperty("user.home")
                                    .map { Path(it, ".cache/pluginVerifier").toFile() }
                                    .let { layout.dir(it) }
                            )
                            .orElse(
                                layout.buildDirectory.dir("tmp/pluginVerifier")
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
                        configurations,
                        dependencies,
                        downloadDirectory,
                        extensionProvider,
                        providers,
                        resources,
                        rootProjectDirectory,
                    )
                }

                configureExtension<IntelliJPlatformExtension.Signing>(Extensions.SIGNING)

                configureExtension<IntelliJPlatformExtension.Publishing>(Extensions.PUBLISHING) {
                    host.convention(Locations.MARKETPLACE)
                    toolboxEnterprise.convention(false)
                    channels.convention(listOf("default"))
                    hidden.convention(false)
                }
            }

            dependencies.configureExtension<IntelliJPlatformDependenciesExtension>(
                Extensions.INTELLIJ_PLATFORM,
                configurations,
                repositories,
                dependencies,
                providers,
                layout,
                rootProjectDirectory,
            )

            repositories.configureExtension<IntelliJPlatformRepositoriesExtension>(
                Extensions.INTELLIJ_PLATFORM,
                repositories,
                providers,
                rootProjectDirectory,
            )
        }
    }
}
