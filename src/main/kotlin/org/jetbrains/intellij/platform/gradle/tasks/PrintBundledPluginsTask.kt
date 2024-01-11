// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.base.PlatformVersionAware

/**
 * Prints bundled plugins within the currently targeted IntelliJ-based IDE release.
 */
@UntrackedTask(because = "Prints output")
abstract class PrintBundledPluginsTask : DefaultTask(), PlatformVersionAware {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Prints bundled plugins within the currently targeted IntelliJ-based IDE release."
    }

    @TaskAction
    fun printBundledPlugins() = productInfo.bundledPlugins.forEach {
        println(it)
    }

    companion object {
        fun register(project: Project) =
            project.registerTask<PrintBundledPluginsTask>(Tasks.PRINT_BUNDLED_PLUGINS)
    }
}
