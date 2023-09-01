// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INTELLIJ_PLATFORM_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INTELLIJ_PLATFORM_SOURCES_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.JAVA_TEST_FIXTURES_PLUGIN_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_BASE_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.TEST_FIXTURES_COMPILE_ONLY_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.artifacts.transform.applyIntellijPlatformCollectorTransformer
import org.jetbrains.intellij.platform.gradleplugin.artifacts.transform.applyIntellijPlatformExtractTransformer
import org.jetbrains.intellij.platform.gradleplugin.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradleplugin.repositories.applyIntelliJPlatformSettings

abstract class IntelliJPlatformBasePlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_BASE_ID) {

    override fun Project.configure() {

        with(plugins) {
            apply(JavaPlugin::class)
        }

        configureExtension<IntelliJPlatformExtension>(Extensions.INTELLIJ_PLATFORM) {
            configureExtension<IntelliJPlatformExtension.PluginConfiguration>(Extensions.PLUGIN_CONFIGURATION) {
                configureExtension<IntelliJPlatformExtension.PluginConfiguration.ProductDescriptor>(Extensions.PRODUCT_DESCRIPTOR)
            }
        }

        with(repositories) {
            applyIntelliJPlatformSettings(objects, providers)
        }

        with(configurations) {
            val intellijPlatformConfiguration = maybeCreate(INTELLIJ_PLATFORM_CONFIGURATION_NAME)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform dependency"
                }

            maybeCreate(INTELLIJ_PLATFORM_SOURCES_CONFIGURATION_NAME)
                .apply {
                    isVisible = false
                    isCanBeConsumed = false
                    isCanBeResolved = true
                    description = "IntelliJ Platform Sources to be attached to the IntelliJ Platform dependency"
                }

            fun Configuration.extend() = extendsFrom(intellijPlatformConfiguration)

            getByName(COMPILE_ONLY_CONFIGURATION_NAME).extend()
            getByName(TEST_COMPILE_ONLY_CONFIGURATION_NAME).extend()
            pluginManager.withPlugin(JAVA_TEST_FIXTURES_PLUGIN_ID) {
                getByName(TEST_FIXTURES_COMPILE_ONLY_CONFIGURATION_NAME).extend()
            }

        }

        applyIntellijPlatformExtractTransformer()
        applyIntellijPlatformCollectorTransformer()
    }
}
