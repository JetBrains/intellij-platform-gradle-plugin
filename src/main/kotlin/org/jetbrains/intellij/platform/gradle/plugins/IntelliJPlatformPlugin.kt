// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.checkGradleVersion

abstract class IntelliJPlatformPlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: $PLUGIN_ID")

        checkGradleVersion()
        project.applyPlugins()
    }

    private fun Project.applyPlugins() {
        plugins.apply(IntelliJPlatformBasePlugin::class)
        plugins.apply(IntelliJPlatformTasksPlugin::class)
    }
}
