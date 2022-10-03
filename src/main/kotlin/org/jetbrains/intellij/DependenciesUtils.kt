// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("DependenciesUtils")

package org.jetbrains.intellij

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternFilterable
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.tasks.SetupDependenciesTask

internal fun Project.intellij(): FileCollection = intellijBase()
internal fun Project.intellij(filter: Closure<*>): FileCollection = intellijBase().matching(filter)
internal fun Project.intellij(filter: Action<PatternFilterable>): FileCollection = intellijBase().matching(filter)
internal fun Project.intellij(filter: PatternFilterable): FileCollection = intellijBase().matching(filter)

private fun Project.intellijBase(): FileTree {
    val setupDependenciesTaskProvider = project.tasks.named<SetupDependenciesTask>(SETUP_DEPENDENCIES_TASK_NAME)
    val setupDependenciesTask = setupDependenciesTaskProvider.get()

    if (!state.executed) {
        throw GradleException("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
    }

    return files(setupDependenciesTask.idea.get().jarFiles).asFileTree
}

internal fun Project.intellijPlugin(plugin: String): FileCollection = intellijPluginBase(plugin)
internal fun Project.intellijPlugin(plugin: String, filter: Closure<*>): FileCollection = intellijPluginBase(plugin).matching(filter)
internal fun Project.intellijPlugin(plugin: String, filter: Action<PatternFilterable>): FileCollection = intellijPluginBase(plugin).matching(filter)
internal fun Project.intellijPlugin(plugin: String, filter: PatternFilterable): FileCollection = intellijPluginBase(plugin).matching(filter)

private fun Project.intellijPluginBase(plugin: String): FileTree {
    val extension = extensions.getByType<IntelliJPluginExtension>()

    if (!state.executed) {
        throw GradleException("intellij plugin '$plugin' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    val jarFiles = extension.getPluginDependenciesList(this).find { it.id == plugin }?.jarFiles
    if (jarFiles.isNullOrEmpty()) {
        throw GradleException("intellij plugin '$plugin' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    return files(jarFiles).asFileTree
}

internal fun Project.intellijPlugins(vararg plugins: String): FileCollection {
    val extension = extensions.getByType<IntelliJPluginExtension>()
    val selectedPlugins = mutableSetOf<PluginDependency>()
    val nonValidPlugins = mutableListOf<String>()
    plugins.forEach { pluginName ->
        val plugin = extension.getPluginDependenciesList(this).find { it.id == pluginName }
        if (plugin?.jarFiles == null || plugin.jarFiles.isEmpty()) {
            nonValidPlugins.add(pluginName)
        } else {
            selectedPlugins.add(plugin)
        }
    }

    if (nonValidPlugins.isNotEmpty()) {
        throw GradleException("The following plugins: $nonValidPlugins are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    return files(selectedPlugins.map { it.jarFiles })
}

internal fun Project.intellijExtra(extra: String): FileCollection = intellijExtraBase(extra)
internal fun Project.intellijExtra(extra: String, filter: Closure<*>): FileCollection = intellijExtraBase(extra).matching(filter)
internal fun Project.intellijExtra(extra: String, filter: Action<PatternFilterable>): FileCollection = intellijExtraBase(extra).matching(filter)
internal fun Project.intellijExtra(extra: String, filter: PatternFilterable): FileCollection = intellijExtraBase(extra).matching(filter)

private fun Project.intellijExtraBase(extra: String): FileTree {
    val setupDependenciesTaskProvider = project.tasks.named<SetupDependenciesTask>(SETUP_DEPENDENCIES_TASK_NAME)

    if (!state.executed) {
        throw GradleException("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
    }

    val setupDependenciesTask = setupDependenciesTaskProvider.get()
    val dependency = setupDependenciesTask.idea.get()
    val extraDependency = dependency.extraDependencies.find { it.name == extra }
    if (extraDependency?.jarFiles == null || extraDependency.jarFiles.isEmpty()) {
        throw GradleException("intellij extra artifact '$extra' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
    }

    return files(extraDependency.jarFiles).asFileTree
}
