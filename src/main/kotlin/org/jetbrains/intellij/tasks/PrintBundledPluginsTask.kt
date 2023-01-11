// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME

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
}
