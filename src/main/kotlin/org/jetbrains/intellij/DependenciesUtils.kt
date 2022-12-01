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

fun Project.intellij() = intellijBase()
fun Project.intellij(filter: Closure<*>) = intellijBase().matching(filter)
fun Project.intellij(filter: Action<PatternFilterable>) = intellijBase().matching(filter)
fun Project.intellij(filter: PatternFilterable) = intellijBase().matching(filter)

private fun Project.intellijBase(): FileTree {
    val setupDependenciesTaskProvider = project.tasks.named<SetupDependenciesTask>(SETUP_DEPENDENCIES_TASK_NAME)
    val ideaProvider = setupDependenciesTaskProvider.flatMap { setupDependenciesTask ->
        setupDependenciesTask.idea.map {
            files(it.jarFiles).asFileTree
        }
    }

    if (!state.executed) {
        throw GradleException("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
    }

    return ideaProvider.get()
}

fun Project.intellijPlugin(plugin: String) = intellijPluginBase(plugin)
fun Project.intellijPlugin(plugin: String, filter: Closure<*>) = intellijPluginBase(plugin).matching(filter)
fun Project.intellijPlugin(plugin: String, filter: Action<PatternFilterable>) = intellijPluginBase(plugin).matching(filter)
fun Project.intellijPlugin(plugin: String, filter: PatternFilterable) = intellijPluginBase(plugin).matching(filter)

private fun Project.intellijPluginBase(plugin: String): FileTree {
    val extension = extensions.getByType<IntelliJPluginExtension>()

    if (!state.executed) {
        throw GradleException("intellij plugin '$plugin' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    val dependency = extension.getPluginDependenciesList(this)
        .find { it.id == plugin && it.jarFiles.isNotEmpty() }
        ?: throw GradleException("intellij plugin '$plugin' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")

    return files(dependency.jarFiles).asFileTree
}

fun Project.intellijPlugins(vararg plugins: String): FileCollection {
    val extension = extensions.getByType<IntelliJPluginExtension>()
    val selectedPlugins = mutableSetOf<PluginDependency>()
    val nonValidPlugins = mutableListOf<String>()

    plugins.forEach { pluginName ->
        extension
            .getPluginDependenciesList(this)
            .find { it.id == pluginName && it.jarFiles.isNotEmpty() }
            .ifNull { nonValidPlugins.add(pluginName) }
            ?.let { selectedPlugins.add(it) }
    }

    if (nonValidPlugins.isNotEmpty()) {
        throw GradleException("The following plugins: $nonValidPlugins are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    return files(selectedPlugins.map { it.jarFiles })
}

fun Project.intellijExtra(extra: String) = intellijExtraBase(extra)
fun Project.intellijExtra(extra: String, filter: Closure<*>) = intellijExtraBase(extra).matching(filter)
fun Project.intellijExtra(extra: String, filter: Action<PatternFilterable>) = intellijExtraBase(extra).matching(filter)
fun Project.intellijExtra(extra: String, filter: PatternFilterable) = intellijExtraBase(extra).matching(filter)

private fun Project.intellijExtraBase(extra: String): FileTree {
    val setupDependenciesTaskProvider = project.tasks.named<SetupDependenciesTask>(SETUP_DEPENDENCIES_TASK_NAME)

    if (!state.executed) {
        throw GradleException("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
    }

    val setupDependenciesTask = setupDependenciesTaskProvider.get()
    val dependency = setupDependenciesTask.idea.get()
    val extraDependency = dependency.extraDependencies
        .find { it.name == extra && it.jarFiles.isNotEmpty() }
        ?: throw GradleException("intellij extra artifact '$extra' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")

    return files(extraDependency.jarFiles).asFileTree
}
