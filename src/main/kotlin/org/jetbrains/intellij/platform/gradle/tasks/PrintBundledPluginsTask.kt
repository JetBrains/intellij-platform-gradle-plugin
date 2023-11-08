// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks

/**
 * Prints the output produced by the [ListBundledPluginsTask] task.
 *
 * @see [ListBundledPluginsTask]
 */
@UntrackedTask(because = "Prints the output produced by the listBundledPlugins task")
abstract class PrintBundledPluginsTask : DefaultTask() {

    /**
     * Input from the [ListBundledPluginsTask].
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    init {
        group = PLUGIN_GROUP_NAME
        description = "Prints bundled plugins within the currently targeted IntelliJ-based IDE release."
    }

    @TaskAction
    fun printBundledPlugins() = println(inputFile.asFile.get().readText())

    companion object {
        fun register(project: Project) =
            project.configureTask<PrintBundledPluginsTask>(Tasks.PRINT_BUNDLED_PLUGINS) {
                val listBundledPluginsTaskProvider = project.tasks.named<ListBundledPluginsTask>(Tasks.LIST_BUNDLED_PLUGINS)

                inputFile.convention(listBundledPluginsTaskProvider.flatMap { listBundledPluginsTask ->
                    listBundledPluginsTask.outputFile
                })

                dependsOn(listBundledPluginsTaskProvider)
            }
    }
}
