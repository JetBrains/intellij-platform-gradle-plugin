// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.compaion.JarCompanion
import org.jetbrains.intellij.platform.gradle.tasks.compaion.ProcessResourcesCompanion
import org.jetbrains.intellij.platform.gradle.tasks.compaion.TestCompanion
import org.jetbrains.intellij.platform.gradle.utils.ALL_TASKS
import org.jetbrains.intellij.platform.gradle.utils.Logger

abstract class IntelliJPlatformTasksPlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Constants.Plugins.TASKS}")

        with(project) {
            plugins.apply(IntelliJPlatformBasePlugin::class)

            listOf(
                InitializeIntelliJPlatformPluginTask,
                PatchPluginXmlTask,
                VerifyPluginProjectConfigurationTask,
                PrintBundledPluginsTask,
                PrintProductsReleasesTask,
                ProcessResourcesCompanion,
                JarCompanion,
                InstrumentCodeTask,
                PrepareSandboxTask,
                BuildSearchableOptionsTask,
                JarSearchableOptionsTask,
                BuildPluginTask,
                SignPluginTask,
                VerifyPluginTask,
                VerifyPluginSignatureTask,
                VerifyPluginStructureTask,
                PublishPluginTask,
                RunIdeTask,
                TestCompanion,
                TestIdePerformanceTask,
                TestIdeUiTask,
            ).forEach {
                it.register(project)
            }

            /**
             * Make all tasks depend on [Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN]
             */
            ALL_TASKS.minus(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN).forEach {
                tasks.named(it) { dependsOn(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) }
            }
        }
    }
}
