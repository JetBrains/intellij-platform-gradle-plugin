// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.provider.ProductReleasesValueSource

/**
 * Prints the list of binary product releases that, by default, match the currently selected IntelliJ Platform along
 * with [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild]
 * and [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.untilBuild] properties.
 */
@UntrackedTask(because = "Prints output")
abstract class PrintProductsReleasesTask : DefaultTask() {

    /**
     * Property holds the list of product releases to print.
     */
    @get:Input
    abstract val productsReleases: ListProperty<String>

    init {
        group = PLUGIN_GROUP_NAME
        description = "Prints all available IntelliJ-based IDE releases with their updates."
    }

    @TaskAction
    fun printProductsReleases() = productsReleases.get().forEach {
        println(it)
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrintProductsReleasesTask>(Tasks.PRINT_PRODUCTS_RELEASES) {
                productsReleases.convention(
                    ProductReleasesValueSource(
                        project.providers,
                        project.resources,
                        project.provider { project.the<IntelliJPlatformExtension>() },
                    )
                )
            }
    }
}
