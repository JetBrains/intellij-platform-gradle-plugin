// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.compaion.JarCompanion
import org.jetbrains.intellij.platform.gradle.tasks.compaion.TestCompanion
import org.jetbrains.intellij.platform.gradle.utils.Logger

@Suppress("unused")
abstract class IntelliJPlatformModulePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.MODULE}")

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        IntelliJPlatformTestingExtension.register(project, target = project)

        listOf(
            // Build Module
            GenerateManifestTask,
            JarCompanion,
            InstrumentCodeTask,
            InstrumentedJarTask,
            ComposedJarTask,
            PrepareSandboxTask,

            // Test Module
            PrepareTestTask,
            TestCompanion,
            TestIdeTask,

            // Verify Module
            VerifyPluginProjectConfigurationTask,
        ).forEach {
            it.register(project)
        }
    }
}
