// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradleplugin.checkGradleVersion
import org.jetbrains.intellij.platform.gradleplugin.info
import org.jetbrains.intellij.platform.gradleplugin.logCategory

abstract class IntelliJPlatformTasksPlugin : Plugin<Project> {

    private lateinit var context: String

    override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: org.jetbrains.intellij.platform.tasks")
        checkGradleVersion()

        applyPlugins(project)
    }

    private fun applyPlugins(project: Project) {
        project.plugins.apply(IntelliJPlatformBasePlugin::class)
    }
}
