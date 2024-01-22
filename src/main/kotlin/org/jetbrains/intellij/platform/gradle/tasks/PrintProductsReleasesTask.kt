// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.provider.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.tasks.aware.PlatformVersionAware

@UntrackedTask(because = "Prints the output produced by the listProductsReleases task")
abstract class PrintProductsReleasesTask : DefaultTask(), PlatformVersionAware {

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
                        project.provider { productInfo },
                    )
                )
            }
    }
}
