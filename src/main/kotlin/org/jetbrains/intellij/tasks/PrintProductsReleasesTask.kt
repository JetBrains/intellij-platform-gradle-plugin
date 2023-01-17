// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME

@UntrackedTask(because = "Prints the output produced by the listProductsReleases task")
abstract class PrintProductsReleasesTask : DefaultTask() {

    /**
     * Input from the [ListProductsReleasesTask].
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    init {
        group = PLUGIN_GROUP_NAME
        description = "Prints all available IntelliJ-based IDE releases with their updates."
    }

    @TaskAction
    fun printProductsReleases() = println(inputFile.asFile.get().readText())
}
