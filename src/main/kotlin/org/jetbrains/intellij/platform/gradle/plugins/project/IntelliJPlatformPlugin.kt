// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.utils.Logger

@Suppress("unused")
abstract class IntelliJPlatformPlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: $PLUGIN_ID")

        with(project.plugins) {
            apply(IntelliJPlatformCorePlugin::class)
            apply(IntelliJPlatformTasksPlugin::class)
        }
    }
}
