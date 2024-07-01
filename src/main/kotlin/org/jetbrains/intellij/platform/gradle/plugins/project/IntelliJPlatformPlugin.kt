// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.Constants.Plugin.ID
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.compaion.ProcessResourcesCompanion
import org.jetbrains.intellij.platform.gradle.utils.Logger

@Suppress("unused")
abstract class IntelliJPlatformPlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: $ID")

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
            apply(IntelliJPlatformModulePlugin::class)
        }

        listOf(
            // Build
            PatchPluginXmlTask,
            ProcessResourcesCompanion,
            BuildSearchableOptionsTask,
            JarSearchableOptionsTask,
            BuildPluginTask,

            // Test
            TestIdePerformanceTask,
            TestIdeUiTask,

            // Run
            RunIdeTask,

            // Verify
            VerifyPluginTask,
            VerifyPluginSignatureTask,
            VerifyPluginStructureTask,

            // Sign
            SignPluginTask,
            PublishPluginTask,
        ).forEach {
            it.register(project)
        }
    }
}
