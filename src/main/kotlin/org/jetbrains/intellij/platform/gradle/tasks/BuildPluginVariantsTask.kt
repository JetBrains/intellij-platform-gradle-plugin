// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.intellij.platform.gradle.variants

/**
 * Builds all enabled OS- and architecture-specific plugin distributions.
 */
@DisableCachingByDefault(because = "No output state to track")
abstract class BuildPluginVariantsTask : DefaultTask() {

    init {
        group = Plugin.GROUP_NAME
        description = "Builds all OS- and architecture-specific plugin distributions."
    }

    companion object : Registrable {
        override fun register(project: Project) {
            val nativeVariantsEnabledProvider = project.extensionProvider.flatMap { it.nativeVariants.enabled }

            val variantTaskProviders = variants.map { variant ->
                val (os, arch) = variant
                val suffix = "_${os}_$arch"
                val preparePluginVariantTaskProvider = project.tasks.named<PreparePluginVariantTask>(Tasks.PREPARE_PLUGIN_VARIANT + suffix)

                project.tasks.register<BuildPluginTask>(Tasks.BUILD_PLUGIN_VARIANTS + suffix) {
                    archiveClassifier.convention("$os-$arch")

                    from(preparePluginVariantTaskProvider.flatMap { it.outputDirectory }) {
                        into(Sandbox.Plugin.LIB)
                    }
                    from(project.extensionProvider.map { it.nativeVariants[variant] })

                    onlyIf("native variants are enabled") {
                        nativeVariantsEnabledProvider.get()
                    }
                }
            }

            project.registerTask<BuildPluginVariantsTask>(Tasks.BUILD_PLUGIN_VARIANTS, configureWithType = false) {
                dependsOn(
                    nativeVariantsEnabledProvider.map { enabled ->
                        variantTaskProviders.takeIf { enabled }.orEmpty()
                    }
                )
            }
        }
    }
}
