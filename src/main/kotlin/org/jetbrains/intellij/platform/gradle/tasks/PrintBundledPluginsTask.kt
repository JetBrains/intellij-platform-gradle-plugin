// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.intellijPlatformIdeLayoutIndicesCachePath
import org.jetbrains.intellij.platform.gradle.services.IdeLayoutIndexService
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath

/**
 * Prints the list of bundled plugins available within the currently targeted IntelliJ Platform.
 */
@UntrackedTask(because = "Prints output")
abstract class PrintBundledPluginsTask : DefaultTask(), IntelliJPlatformVersionAware {

    @get:Internal
    /**
     * Shared service that provides the cached serialized IDE layout index for the target platform.
     */
    internal abstract val ideLayoutIndexService: Property<IdeLayoutIndexService>

    @get:Internal
    /**
     * On-disk cache location for layout-index snapshots derived from extracted IDE distributions.
     */
    abstract val ideLayoutIndexCacheDirectory: DirectoryProperty

    @TaskAction
    fun printBundledPlugins() {
        println("Bundled plugins for ${productInfo.name} ${productInfo.version} (${productInfo.buildNumber}):")
        val ideLayoutIndex = ideLayoutIndexService.get().resolve(platformPath, ideLayoutIndexCacheDirectory.asPath)

        ideLayoutIndex.bundledPlugins
            .map { it.id + it.name?.run { " ($this)" }.orEmpty() }
            .toSet()
            .sorted()
            .forEach { println(it) }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Prints the list of bundled plugins available within the currently targeted IntelliJ Platform."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrintBundledPluginsTask>(Tasks.PRINT_BUNDLED_PLUGINS) {
                ideLayoutIndexService.convention(project.gradle.registerClassLoaderScopedBuildService(IdeLayoutIndexService::class))
                // Reuse the same cache location as dependency resolution so task output reflects the
                // same indexed view of the extracted IDE layout.
                ideLayoutIndexCacheDirectory.convention(project.layout.dir(project.provider {
                    project.providers.intellijPlatformIdeLayoutIndicesCachePath(project.rootProjectPath).get().toFile()
                }))
            }
    }
}
