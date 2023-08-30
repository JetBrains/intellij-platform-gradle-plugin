// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.register
import org.jetbrains.intellij.platform.gradleplugin.checkGradleVersion
import org.jetbrains.intellij.platform.gradleplugin.info
import org.jetbrains.intellij.platform.gradleplugin.logCategory

abstract class IntelliJPlatformAbstractPlugin(val pluginId: String) : Plugin<Project> {

    protected lateinit var context: String

    final override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: $pluginId")
        checkGradleVersion()

        configure(project)

        project.plugins.applyPlugins(project)
        project.configurations.applyConfigurations(project)
        project.extensions.applyExtension(project)
        project.tasks.applyTasks(project)
    }

    protected inline fun <reified T : Task> TaskContainer.configureTask(name: String, noinline configuration: T.() -> Unit = {}) {
        info(context, "Configuring task: $name")
        val task = findByName(name) as? T ?: register<T>(name).get()
        task.configuration()
    }

    protected abstract fun configure(project: Project)
    protected abstract fun PluginContainer.applyPlugins(project: Project)
    protected abstract fun ConfigurationContainer.applyConfigurations(project: Project)
    protected abstract fun ExtensionContainer.applyExtension(project: Project)
    protected abstract fun TaskContainer.applyTasks(project: Project)
}
