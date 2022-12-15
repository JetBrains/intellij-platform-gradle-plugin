// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask

@Suppress("UnstableApiUsage")
@UntrackedTask(because = "Prints the output produced by the listBundledPlugins task")
abstract class PrintBundledPluginsTask : DefaultTask() {

    /**
     * Input form the [ListBundledPluginsTask].
     */
    @get:InputFile
    abstract val inputFile: RegularFileProperty

    @TaskAction
    fun print() = println(inputFile.asFile.get().readText())
}
