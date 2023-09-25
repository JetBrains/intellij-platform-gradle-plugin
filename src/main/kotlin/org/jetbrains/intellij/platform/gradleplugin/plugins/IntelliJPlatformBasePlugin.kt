// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.JAVA_TEST_FIXTURES_PLUGIN_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_BASE_ID
import org.jetbrains.intellij.platform.gradleplugin.artifacts.transform.applyIntellijPlatformBuildNumberTransformer
import org.jetbrains.intellij.platform.gradleplugin.artifacts.transform.applyIntellijPlatformCollectorTransformer
import org.jetbrains.intellij.platform.gradleplugin.artifacts.transform.applyIntellijPlatformExtractTransformer
import org.jetbrains.intellij.platform.gradleplugin.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradleplugin.repositories.applyIntelliJPlatformSettings

abstract class IntelliJPlatformBasePlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_BASE_ID) {

    override fun Project.configure() {

        with(plugins) {
            apply(JavaPlugin::class)
        }

        with(repositories) {
            applyIntelliJPlatformSettings(objects, providers)
        }

        with(configurations) {
            val intellijPlatformConfiguration = maybeCreate(Configurations.INTELLIJ_PLATFORM)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform dependency"
                }

            val intellijPlatformBuildNumberConfiguration = maybeCreate(Configurations.INTELLIJ_PLATFORM_BUILD_NUMBER)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform dependency build number"

                    attributes {
                        attribute(Configurations.Attributes.extracted, true)
//                        attribute(Configurations.Attributes.collected, true)
                        attribute(Configurations.Attributes.buildNumber, true)
                    }

                    extendsFrom(intellijPlatformConfiguration)
                }

            val intellijPlatformDependenciesConfiguration = maybeCreate(Configurations.INTELLIJ_PLATFORM_DEPENDENCIES)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform Dependencies dependency"
                }

            maybeCreate(Configurations.INTELLIJ_PLATFORM_SOURCES)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform Sources to be attached to the IntelliJ Platform dependency"
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

            val identifiers = IntelliJPlatformType.values().map { "${it.groupId}:${it.artifactId}" }
            all {
                incoming.beforeResolve {
                    val matched = dependencies.filter { identifiers.contains("${it.group}:${it.name}") }
                    if (matched.size > 1) {
                        throw GradleException("Conflicting dependencies detected: \n${matched.joinToString("\n")}")
                    }
                }
            }
        }

        with(IntelliJPluginConstants.Extensions) {
            this@configure.configureExtension<IntelliJPlatformExtension>(INTELLIJ_PLATFORM) {
                configureExtension<IntelliJPlatformExtension.PluginConfiguration>(PLUGIN_CONFIGURATION) {
                    configureExtension<IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor>(PRODUCT_DESCRIPTOR)
                    configureExtension<IntelliJPlatformExtension.PluginConfiguration.IdeaVersion>(IDEA_VERSION)
                    configureExtension<IntelliJPlatformExtension.PluginConfiguration.Vendor>(VENDOR)
                }
            }
        }

        applyIntellijPlatformExtractTransformer()
        applyIntellijPlatformCollectorTransformer()
        applyIntellijPlatformBuildNumberTransformer()
    }
}
