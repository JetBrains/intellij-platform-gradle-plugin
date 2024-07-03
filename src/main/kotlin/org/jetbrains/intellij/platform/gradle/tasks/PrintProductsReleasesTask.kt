// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.assign
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.intellij.platform.gradle.utils.resolve

/**
 * Prints the list of binary product releases that, by default, match the currently selected IntelliJ Platform along
 * with [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.sinceBuild]
 * and [IntelliJPlatformExtension.PluginConfiguration.IdeaVersion.untilBuild] properties.
 *
 * The filer used for retrieving the release list can be customized by using properties provided with [ProductReleasesValueSource.FilterParameters].
 */
@UntrackedTask(because = "Prints output")
abstract class PrintProductsReleasesTask : DefaultTask(), ProductReleasesValueSource.FilterParameters {

    /**
     * Property holds the list of product releases to print.
     *
     * Can be used to retrieve the result list.
     */
    @get:Input
    abstract val productsReleases: ListProperty<String>

    @TaskAction
    fun printProductsReleases() = productsReleases.get().forEach {
        println(it)
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Prints all available IntelliJ-based IDE releases with their updates."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrintProductsReleasesTask>(Tasks.PRINT_PRODUCTS_RELEASES) {
                val ideaVersionProvider = project.extensionProvider.map { it.pluginConfiguration.ideaVersion }

                productsReleases.convention(
                    project.providers.of(ProductReleasesValueSource::class.java) {
                        parameters.jetbrainsIdes.set(project.resources.resolve(Locations.PRODUCTS_RELEASES_JETBRAINS_IDES))
                        parameters.androidStudio.set(project.resources.resolve(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO))

                        parameters.sinceBuild = this@registerTask.sinceBuild
                        parameters.untilBuild = this@registerTask.untilBuild
                        parameters.types = this@registerTask.types
                        parameters.channels = this@registerTask.channels
                    }
                )

                channels.convention(project.provider { ProductRelease.Channel.values().toList() })
                types.convention(project.extensionProvider.map {
                    listOf(it.productInfo.productCode.toIntelliJPlatformType())
                })
                sinceBuild.convention(ideaVersionProvider.flatMap { it.sinceBuild })
                untilBuild.convention(ideaVersionProvider.flatMap { it.untilBuild })
            }
    }
}
