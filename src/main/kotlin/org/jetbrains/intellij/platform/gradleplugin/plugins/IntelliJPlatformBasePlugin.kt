// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.EXTENSION_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INTELLIJ_PLATFORM_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INTELLIJ_PLATFORM_SOURCES_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.JAVA_TEST_FIXTURES_PLUGIN_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_BASE_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.TEST_FIXTURES_COMPILE_ONLY_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.artifacts.transform.applyIntellijPlatformCollectorTransformer
import org.jetbrains.intellij.platform.gradleplugin.artifacts.transform.applyIntellijPlatformExtractTransformer
import org.jetbrains.intellij.platform.gradleplugin.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradleplugin.repositories.applyIntelliJPlatformSettings
import javax.inject.Inject

abstract class IntelliJPlatformBasePlugin @Inject constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
) : IntelliJPlatformAbstractPlugin(PLUGIN_BASE_ID) {

    override fun configure(project: Project) {
        project.applyIntellijPlatformExtractTransformer()
        project.applyIntellijPlatformCollectorTransformer()
        project.repositories.applyIntelliJPlatformSettings(objects, providers)
    }

    override fun PluginContainer.applyPlugins(project: Project) {
        apply(JavaPlugin::class)
    }

    override fun ConfigurationContainer.applyConfigurations(project: Project) {
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
        project.pluginManager.withPlugin(JAVA_TEST_FIXTURES_PLUGIN_ID) {
            getByName(TEST_FIXTURES_COMPILE_ONLY_CONFIGURATION_NAME).extend()
        }
    }

    override fun ExtensionContainer.applyExtension(project: Project) {
        create<IntelliJPlatformExtension>(EXTENSION_NAME)
    }
}
