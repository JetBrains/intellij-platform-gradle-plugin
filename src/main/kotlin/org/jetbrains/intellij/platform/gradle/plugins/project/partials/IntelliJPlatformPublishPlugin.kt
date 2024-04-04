// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project.partials

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.tasks.PublishPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.SignPluginTask
import org.jetbrains.intellij.platform.gradle.utils.Logger

abstract class IntelliJPlatformPublishPlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.PUBLISH}")

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
            apply(IntelliJPlatformBuildPlugin::class)
        }

        listOf(
            SignPluginTask,
            PublishPluginTask,
        ).forEach {
            it.register(project)
        }
    }
}
