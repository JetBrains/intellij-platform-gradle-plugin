@file:JvmName("DependenciesUtils")
package org.jetbrains.intellij

import groovy.lang.Closure
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.util.PatternFilterable
import org.jetbrains.intellij.dependency.PluginDependency

fun intellij(project: Project): FileCollection = intellijBase(project)
fun intellij(project: Project, filter: Closure<*>): FileCollection = intellijBase(project).matching(filter)
fun intellij(project: Project, filter: Action<PatternFilterable>): FileCollection = intellijBase(project).matching(filter)
fun intellij(project: Project, filter: PatternFilterable): FileCollection = intellijBase(project).matching(filter)

private fun intellijBase(project: Project): FileTree {
    val extension = project.extensions.findByType(IntelliJPluginExtension::class.java)
        ?: throw GradleException("IntelliJPluginExtension cannot be resolved")

    if (!project.state.executed) {
        throw GradleException("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
    }

    return project.files(extension.getIdeaDependency(project).jarFiles).asFileTree
}

fun intellijPlugin(project: Project, plugin: String): FileCollection = intellijPluginBase(project, plugin)
fun intellijPlugin(project: Project, plugin: String, filter: Closure<*>): FileCollection = intellijPluginBase(project, plugin).matching(filter)
fun intellijPlugin(project: Project, plugin: String, filter: Action<PatternFilterable>): FileCollection = intellijPluginBase(project, plugin).matching(filter)
fun intellijPlugin(project: Project, plugin: String, filter: PatternFilterable): FileCollection = intellijPluginBase(project, plugin).matching(filter)

private fun intellijPluginBase(project: Project, plugin: String): FileTree {
    val extension = project.extensions.findByType(IntelliJPluginExtension::class.java)
        ?: throw GradleException("IntelliJPluginExtension cannot be resolved")

    if (!project.state.executed) {
        throw GradleException("intellij plugin '$plugin' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    val jarFiles = extension.getPluginDependenciesList(project).find { it.id == plugin }?.jarFiles
    if (jarFiles.isNullOrEmpty()) {
        throw GradleException("intellij plugin '$plugin' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    return project.files(jarFiles).asFileTree
}

fun intellijPlugins(project: Project, vararg plugins: String): FileCollection {
    val extension = project.extensions.findByType(IntelliJPluginExtension::class.java)
        ?: throw GradleException("IntelliJPluginExtension cannot be resolved")

    val selectedPlugins = HashSet<PluginDependency>()
    val nonValidPlugins = mutableListOf<String>()
    plugins.forEach { pluginName ->
        val plugin = extension.getPluginDependenciesList(project).find { it.id == pluginName }
        if (plugin?.jarFiles == null || plugin.jarFiles.isEmpty()) {
            nonValidPlugins.add(pluginName)
        } else {
            selectedPlugins.add(plugin)
        }
    }

    if (nonValidPlugins.isNotEmpty()) {
        throw GradleException("intellij plugins $nonValidPlugins are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
    }

    return project.files(selectedPlugins.map { it.jarFiles })
}

fun intellijExtra(project: Project, extra: String): FileCollection = intellijExtraBase(project, extra)
fun intellijExtra(project: Project, extra: String, filter: Closure<*>): FileCollection = intellijExtraBase(project, extra).matching(filter)
fun intellijExtra(project: Project, extra: String, filter: Action<PatternFilterable>): FileCollection = intellijExtraBase(project, extra).matching(filter)
fun intellijExtra(project: Project, extra: String, filter: PatternFilterable): FileCollection = intellijExtraBase(project, extra).matching(filter)

private fun intellijExtraBase(project: Project, extra: String): FileTree {
    val extension = project.extensions.findByType(IntelliJPluginExtension::class.java)
        ?: throw GradleException("IntelliJPluginExtension cannot be resolved")

    if (!project.state.executed) {
        throw GradleException("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
    }

    val dependency = extension.getIdeaDependency(project)
    val extraDep = dependency.extraDependencies.find { it.name == extra }
    if (extraDep?.jarFiles == null || extraDep.jarFiles.isEmpty()) {
        throw GradleException("intellij extra artifact '$extra' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
    }

    return project.files(extraDep.jarFiles).asFileTree
}
