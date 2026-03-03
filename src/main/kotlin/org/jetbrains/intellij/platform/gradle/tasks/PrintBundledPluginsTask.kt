// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.module.IdeModule
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.services.IdesManagerService
import org.jetbrains.intellij.platform.gradle.services.registerClassLoaderScopedBuildService
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware

/**
 * Prints the list of bundled plugins available within the currently targeted IntelliJ Platform.
 */
@UntrackedTask(because = "Prints output")
abstract class PrintBundledPluginsTask : DefaultTask(), IntelliJPlatformVersionAware {

    @get:Internal
    abstract val idesManager: Property<IdesManagerService>

    @TaskAction
    fun printBundledPlugins() {
        println("Bundled plugins for ${productInfo.name} ${productInfo.version} (${productInfo.buildNumber}):")
        val ide = idesManager.get().resolve(platformPath)
        val items = ide.bundledPlugins.asSequence()

        items.filterIsInstance<IdePlugin>()
            .minus(items.filterIsInstance<IdeModule>().toSet())
            .map { it.pluginId + it.pluginName?.run { " ($this)" }.orEmpty() }
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
                idesManager.convention(project.gradle.registerClassLoaderScopedBuildService(IdesManagerService::class))
            }
    }
}
