@file:JvmName("DependenciesUtils")
@file:Suppress("unused")

package org.jetbrains.intellij

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.tasks.SetupDependenciesTask

fun Project.intellij(): FileCollection = intellijBase()
fun Project.intellij(filter: Closure<*>): FileCollection = intellijBase().matching(filter)
fun Project.intellij(filter: Action<PatternFilterable>): FileCollection = intellijBase().matching(filter)
fun Project.intellij(filter: PatternFilterable): FileCollection = intellijBase().matching(filter)

private fun Project.intellijBase(): FileTree {
    val setupDependenciesTaskProvider = project.tasks.named(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
    val setupDependenciesTask = setupDependenciesTaskProvider.get() as SetupDependenciesTask

    if (!state.executed) {
        throw GradleException("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
    }

    return files(setupDependenciesTask.idea.get().jarFiles).asFileTree
}

fun Project.intellijPlugin(plugin: String): FileCollection = intellijPluginBase(plugin)
fun Project.intellijPlugin(plugin: String, filter: Closure<*>): FileCollection = intellijPluginBase(plugin).matching(filter)
fun Project.intellijPlugin(plugin: String, filter: Action<PatternFilterable>): FileCollection = intellijPluginBase(plugin).matching(filter)
fun Project.intellijPlugin(plugin: String, filter: PatternFilterable): FileCollection = intellijPluginBase(plugin).matching(filter)

private fun Project.intellijPluginBase(plugin: String): FileTree {
    val extension = extensions.getByType(IntelliJPluginExtension::class.java)

    if (!state.executed) {
        throw GradleException("intellij plugin '$plugin' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    val jarFiles = extension.getPluginDependenciesList(this).find { it.id == plugin }?.jarFiles
    if (jarFiles.isNullOrEmpty()) {
        throw GradleException("intellij plugin '$plugin' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    return files(jarFiles).asFileTree
}

fun Project.intellijPlugins(vararg plugins: String): FileCollection {
    val extension = extensions.getByType(IntelliJPluginExtension::class.java)
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

fun Project.intellijExtra(extra: String): FileCollection = intellijExtraBase(extra)
fun Project.intellijExtra(extra: String, filter: Closure<*>): FileCollection = intellijExtraBase(extra).matching(filter)
fun Project.intellijExtra(extra: String, filter: Action<PatternFilterable>): FileCollection = intellijExtraBase(extra).matching(filter)
fun Project.intellijExtra(extra: String, filter: PatternFilterable): FileCollection = intellijExtraBase(extra).matching(filter)

private fun Project.intellijExtraBase(extra: String): FileTree {
    val setupDependenciesTaskProvider = project.tasks.named(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
    val setupDependenciesTask = setupDependenciesTaskProvider.get() as SetupDependenciesTask

    if (!state.executed) {
        throw GradleException("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
    }

    val dependency = setupDependenciesTask.idea.get()
    val extraDependency = dependency.extraDependencies.find { it.name == extra }
    if (extraDependency?.jarFiles == null || extraDependency.jarFiles.isEmpty()) {
        throw GradleException("intellij extra artifact '$extra' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
    }

    return files(extraDependency.jarFiles).asFileTree
}
