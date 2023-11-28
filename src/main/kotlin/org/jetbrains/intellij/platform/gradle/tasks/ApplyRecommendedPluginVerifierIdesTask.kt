// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import kotlin.io.path.readLines

// TODO: check if needed
// @UntrackedTask(because = "Should always run Plugin Verifier")
abstract class ApplyRecommendedPluginVerifierIdesTask : DefaultTask() {

    init {
        group = IntelliJPluginConstants.PLUGIN_GROUP_NAME
        description = "Applies recommended IDE list for the Plugin Verifier task"
    }

    @get:Internal
    abstract val apply: Property<Boolean>

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productsReleasesFile: RegularFileProperty

    @TaskAction
    fun apply() {
        val extension = project.the<IntelliJPlatformExtension>()

        productsReleasesFile.asPath
            .readLines()
            .map { extension.pluginVerifier.ides.ide(it) }
    }

    companion object {
        fun register(project: Project) =
            project.registerTask<ApplyRecommendedPluginVerifierIdesTask>(Tasks.APPLY_RECOMMENDED_PLUGIN_VERIFIER_IDES) {
                val listProductsReleasesTaskProvider = project.tasks.named<ListProductsReleasesTask>(Tasks.LIST_PRODUCTS_RELEASES)

                productsReleasesFile.convention(listProductsReleasesTaskProvider.flatMap {
                    it.outputFile
                })
                apply.convention(false)

                onlyIf { apply.get() }

                dependsOn(listProductsReleasesTaskProvider)
            }
    }
}
