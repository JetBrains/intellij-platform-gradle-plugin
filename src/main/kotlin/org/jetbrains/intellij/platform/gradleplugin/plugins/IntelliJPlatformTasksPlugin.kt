// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.register
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME
import org.jetbrains.intellij.platform.gradleplugin.checkGradleVersion
import org.jetbrains.intellij.platform.gradleplugin.info
import org.jetbrains.intellij.platform.gradleplugin.logCategory
import org.jetbrains.intellij.platform.gradleplugin.tasks.SetupDependenciesTask

abstract class IntelliJPlatformTasksPlugin : Plugin<Project> {

    private lateinit var context: String

    override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: org.jetbrains.intellij.platform.tasks")
        checkGradleVersion()

        project.applyPlugins()
        project.applyTasks()
    }

    private fun Project.applyPlugins() {
        plugins.apply(IntelliJPlatformBasePlugin::class)
    }

    private fun Project.applyTasks() {
        tasks.register<SetupDependenciesTask>(SETUP_DEPENDENCIES_TASK_NAME)
    }
}
