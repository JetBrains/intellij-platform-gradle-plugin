// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.*
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.JAVA_TEST_FIXTURES_PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_BASE_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.artifacts.transform.*
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.model.ProductRelease
import org.jetbrains.intellij.platform.gradle.provider.ProductInfoValueSource
import org.jetbrains.intellij.platform.gradle.provider.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.tasks.RunPluginVerifierTask
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.toVersion
import java.util.*
import kotlin.io.path.Path

abstract class IntelliJPlatformBasePlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_BASE_ID) {

    override fun Project.configure() {

        with(plugins) {
            apply(JavaPlugin::class)
        }

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

                extendsFrom(intellijPlatformDependencyConfiguration, intellijPlatformLocalConfiguration)

                incoming.beforeResolve {
                    if (dependencies.isEmpty()) {
                        throw GradleException("No IntelliJ Platform dependency found")
                    }

                    val identifiers = IntelliJPlatformType.values().map { it.dependency }.map { "${it.group}:${it.name}" }
                    val matched = dependencies.filter { identifiers.contains("${it.group}:${it.name}") }
                    if (matched.size > 1) {
                        throw GradleException("Conflicting dependencies detected: \n${matched.joinToString("\n")}")
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

            create(
                name = Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO,
                description = "IntelliJ Platform product info",
            ) {
                attributes {
                    attribute(Attributes.productInfo, true)
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
                description = "JetBrains Runtime local instance"
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

            fun Configuration.extend() = extendsFrom(
                intellijPlatformConfiguration,
                intellijPlatformDependenciesConfiguration,
            )

            getByName(COMPILE_ONLY_CONFIGURATION_NAME).extend()
            getByName(TEST_COMPILE_ONLY_CONFIGURATION_NAME).extend()
            pluginManager.withPlugin(JAVA_TEST_FIXTURES_PLUGIN_ID) {
                getByName(Configurations.TEST_FIXTURES_COMPILE_ONLY).extend()
            }
        }

        with(dependencies) {
            attributesSchema {
                attribute(Attributes.bundledPluginsList)
                attribute(Attributes.collected)
                attribute(Attributes.extracted)
                attribute(Attributes.productInfo)
            }

            applyExtractorTransformer(
                configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(Configurations.INTELLIJ_PLATFORM_DEPENDENCY),
                configurations.getByName(Configurations.JETBRAINS_RUNTIME_DEPENDENCY),
            )
            applyCollectorTransformer(
                configurations.getByName(COMPILE_CLASSPATH_CONFIGURATION_NAME),
                configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME),
            )
            applyProductInfoTransformer()
            applyBundledPluginsListTransformer()
            applyPluginVerifierIdeExtractorTransformer(
                configurations.getByName(Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES_DEPENDENCY),
                providers.provider {
                    project.the<IntelliJPlatformExtension>().pluginVerifier.downloadDirectory.get()
                },
            )
        }

        configureExtension<IntelliJPlatformExtension>(Extensions.INTELLIJ_PLATFORM) {
            val productInfoValueProvider = providers.of(ProductInfoValueSource::class.java) {
                with(parameters) {
                    productInfoConfiguration = configurations.getByName(Configurations.INTELLIJ_PLATFORM_PRODUCT_INFO)
                }
            }

            instrumentCode.convention(true)
            sandboxContainer.convention(project.layout.buildDirectory.dir(Sandbox.CONTAINER))

            configureExtension<IntelliJPlatformExtension.PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION) {
                name.convention(project.provider { project.name })
                version.convention(project.provider { project.version.toString() })

                configureExtension<IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)
                configureExtension<IntelliJPlatformExtension.PluginConfiguration.IdeaVersion>(Extensions.IDEA_VERSION) {
                    val buildVersion = productInfoValueProvider.map { it.buildNumber.toVersion() }
                    sinceBuild.convention(buildVersion.map { "${it.major}.${it.minor}" })
                    untilBuild.convention(buildVersion.map { "${it.major}.*" })
                }
                configureExtension<IntelliJPlatformExtension.PluginConfiguration.Vendor>(Extensions.VENDOR)
            }

            configureExtension<IntelliJPlatformExtension.PluginVerifier>(Extensions.PLUGIN_VERIFIER) {
                homeDirectory.convention(
                    providers.systemProperty("plugin.verifier.home.dir")
                        .flatMap { layout.dir(provider { Path(it).toFile() }) }
                        .orElse(layout.dir(providers.environmentVariable("XDG_CACHE_HOME").map {
                            Path(it, "pluginVerifier").toFile()
                        }))
                        .orElse(layout.dir(providers.systemProperty("user.home").map {
                            Path(it, ".cache/pluginVerifier").toFile()
                        }))
                        .orElse(project.layout.buildDirectory.dir("tmp/pluginVerifier"))
                )
                downloadDirectory.convention(homeDirectory.dir("ides"))
                failureLevel.convention(EnumSet.of(RunPluginVerifierTask.FailureLevel.COMPATIBILITY_PROBLEMS))
                verificationReportsDirectory.convention(project.layout.buildDirectory.dir("reports/pluginVerifier"))
                verificationReportsFormats.convention(
                    EnumSet.of(
                        RunPluginVerifierTask.VerificationReportsFormats.PLAIN,
                        RunPluginVerifierTask.VerificationReportsFormats.HTML,
                    )
                )
                teamCityOutputFormat.convention(false)
                subsystemsToCheck.convention("all")

                val productReleasesValueProvider = providers.of(ProductReleasesValueSource::class.java) {
                    fun String.resolve() = resources.text
                        .fromUri(this)
                        .runCatching { asFile("UTF-8") }
                        .onFailure { logger.error("Cannot resolve product releases", it) }
                        .getOrThrow()

                    with(parameters) {
                        jetbrainsIdes.set(Locations.PRODUCTS_RELEASES_JETBRAINS_IDES.resolve())
                        androidStudio.set(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO.resolve())
                        channels.set(ProductRelease.Channel.values().toList())

                        val extension = project.the<IntelliJPlatformExtension>()
                        extension.pluginConfiguration.ideaVersion.let { ideaVersion ->
                            sinceBuild.set(ideaVersion.sinceBuild)
                            untilBuild.set(ideaVersion.untilBuild)
                            type.set(productInfoValueProvider.map { it.productCode.toIntelliJPlatformType() })
                        }
                    }
                }

                configureExtension<IntelliJPlatformExtension.PluginVerifier.Ides>(
                    Extensions.IDES,
                    dependencies,
                    providers,
                    tasks,
                    gradle,
                    downloadDirectory,
                    productReleasesValueProvider,
                )
            }

            configureExtension<IntelliJPlatformExtension.Signing>(Extensions.SIGNING)
        }

        dependencies.configureExtension<IntelliJPlatformDependenciesExtension>(
            Extensions.INTELLIJ_PLATFORM,
            configurations,
            repositories,
            dependencies,
            providers,
            gradle,
        )

        repositories.configureExtension<IntelliJPlatformRepositoriesExtension>(
            Extensions.INTELLIJ_PLATFORM,
            repositories,
            providers,
        )
    }
}
