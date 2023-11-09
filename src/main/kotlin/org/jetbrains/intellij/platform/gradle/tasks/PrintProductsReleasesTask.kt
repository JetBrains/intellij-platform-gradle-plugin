// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.asPath
import kotlin.io.path.readText

/**
 * Prints the output produced by the [ListProductsReleasesTask] task.
 *
 * @see [ListProductsReleasesTask]
 */
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
    fun printProductsReleases() = println(inputFile.asPath.readText())

    companion object {
        fun register(project: Project) =
            project.configureTask<PrintProductsReleasesTask>(Tasks.PRINT_PRODUCTS_RELEASES) {
                val listProductsReleasesTaskProvider = project.tasks.named<ListProductsReleasesTask>(Tasks.LIST_PRODUCTS_RELEASES)

                inputFile.convention(listProductsReleasesTaskProvider.flatMap {
                    it.outputFile
                })

                dependsOn(listProductsReleasesTaskProvider)
            }
    }
}
