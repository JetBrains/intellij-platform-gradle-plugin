// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.Incubating
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.dependency.BuiltinPluginsRegistry
import org.jetbrains.intellij.logCategory
import java.io.File

@Incubating
abstract class ListBundledPluginsTask : DefaultTask() {

    /**
     * The IDEA dependency sources path.
     * Configured automatically with the [org.jetbrains.intellij.tasks.SetupDependenciesTask.idea] dependency.
     *
     * Default value: `setupDependenciesTask.idea.get().classes.path`
     */
    @get:Input
    abstract val ideDir: Property<File>

    /**
     * Path to the file, where the output list will be stored.
     *
     * Default value: `File("${project.buildDir}/bundledPlugins.txt")`
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    private val context = logCategory()

    @TaskAction
    fun list() {
        outputFile.get().asFile.outputStream().use { os ->
            BuiltinPluginsRegistry
                .resolveBundledPlugins(ideDir.get(), context)
                .joinToString("\n")
                .let {
                    TeeOutputStream(System.out, os).write(it.toByteArray())
                }
        }
    }
}
