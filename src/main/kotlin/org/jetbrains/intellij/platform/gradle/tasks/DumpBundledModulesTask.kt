// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.intellijPlatformIdeLayoutIndicesCachePath
import org.jetbrains.intellij.platform.gradle.services.IdeLayoutIndexService
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import org.jetbrains.intellij.platform.gradle.utils.writeTextIfChanged
import kotlin.io.path.createDirectories

/**
 * Dumps all known bundled modules to [outputFile].
 */
@UntrackedTask(because = "Should always dump bundled modules for Plugin DevKit purposes")
abstract class DumpBundledModulesTask : DefaultTask(), IntelliJPlatformVersionAware {

    @get:Internal
    internal abstract val ideLayoutIndexService: Property<IdeLayoutIndexService>

    @get:Internal
    abstract val ideLayoutIndexCacheDirectory: DirectoryProperty

    /**
     * The file to which bundled modules are written as tab-separated module id and name, one per line.
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun dumpBundledModules() {
        val ideLayoutIndex = ideLayoutIndexService.get().resolve(platformPath, ideLayoutIndexCacheDirectory.asPath)

        val content = ideLayoutIndex.bundledModules
            .map { "${it.id}\t${it.name.orEmpty()}" }
            .distinct()
            .sorted()
            .joinToString(System.lineSeparator(), postfix = System.lineSeparator())

        outputFile.asPath.parent.createDirectories()
        outputFile.asPath.writeTextIfChanged(content)
    }

    init {
        group = null
        description = "Dumps all known bundled modules to a file for Plugin DevKit plugin purposes."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<DumpBundledModulesTask>(Tasks.DUMP_BUNDLED_MODULES) {
                ideLayoutIndexService.convention(project.gradle.registerClassLoaderScopedBuildService(IdeLayoutIndexService::class))
                ideLayoutIndexCacheDirectory.convention(project.layout.dir(project.provider {
                    project.providers.intellijPlatformIdeLayoutIndicesCachePath(project.rootProjectPath).get().toFile()
                }))
                outputFile.convention(
                    project.layout.buildDirectory.file("tmp/$name/bundled-modules.txt"),
                )
            }
    }
}
