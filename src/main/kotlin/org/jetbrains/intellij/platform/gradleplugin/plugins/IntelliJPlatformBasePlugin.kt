// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.EXTENSION_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INTELLIJ_PLATFORM_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.artifacts.applyIntellijPlatformCollectorTransformer
import org.jetbrains.intellij.platform.gradleplugin.artifacts.applyIntellijPlatformExtractTransformer
import org.jetbrains.intellij.platform.gradleplugin.checkGradleVersion
import org.jetbrains.intellij.platform.gradleplugin.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradleplugin.info
import org.jetbrains.intellij.platform.gradleplugin.logCategory
import org.jetbrains.intellij.platform.gradleplugin.repositories.applyIntelliJPlatformSettings
import javax.inject.Inject

abstract class IntelliJPlatformBasePlugin @Inject constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
) : Plugin<Project> {

    private lateinit var context: String

    override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: org.jetbrains.intellij.platform.base")
        checkGradleVersion()

        val intellijPlatformConfiguration = project.configurations.create(INTELLIJ_PLATFORM_CONFIGURATION_NAME)
            .setVisible(false)
            .apply {
                isCanBeConsumed = false
                isCanBeResolved = true
            }

        project.repositories.applyIntelliJPlatformSettings(objects, providers)
        project.applyIntellijPlatformExtractTransformer()
        project.applyIntellijPlatformCollectorTransformer()
        project.applyExtension()
        project.applyTasks()
    }

    private fun Project.applyExtension() {
        extensions.create<IntelliJPlatformExtension>(EXTENSION_NAME)
    }

    private fun Project.applyTasks() {

    }
}
