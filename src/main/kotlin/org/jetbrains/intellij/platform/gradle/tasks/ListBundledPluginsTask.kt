// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.utils.writeText
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.tasks.base.PlatformVersionAware

/**
 * Lists all IDs of plugins bundled within the currently targeted IDE.
 *
 * This can be used to determine Plugin ID for setting up [Plugin Dependencies](https://plugins.jetbrains.com/docs/intellij/plugin-dependencies.html).
 *
 * @see [PrintBundledPluginsTask]
 */
@CacheableTask
abstract class ListBundledPluginsTask : DefaultTask(), PlatformVersionAware {

    /**
     * Path to the file, where the output list will be stored.
     *
     * Default value: `File("${project.buildDir}/bundledPlugins.txt")`
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        group = PLUGIN_GROUP_NAME
        description = "List bundled plugins within the currently targeted IntelliJ-based IDE release."
    }

    @TaskAction
    fun listBundledPlugins() {
        outputFile.asPath.writeText(
            productInfo.bundledPlugins.joinToString(separator = "\n")
        )
    }

    companion object {
        fun register(project: Project) =
            project.registerTask<ListBundledPluginsTask>(Tasks.LIST_BUNDLED_PLUGINS) {
                outputFile.convention(
                    project.layout.buildDirectory.file("${Tasks.LIST_BUNDLED_PLUGINS}.txt")
                )
            }
    }
}
