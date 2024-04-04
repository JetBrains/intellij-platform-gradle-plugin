// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.plugins.project.partials.IntelliJPlatformBasePlugin
import org.jetbrains.intellij.platform.gradle.plugins.project.partials.IntelliJPlatformBuildPlugin
import org.jetbrains.intellij.platform.gradle.plugins.project.partials.IntelliJPlatformTestPlugin
import org.jetbrains.intellij.platform.gradle.plugins.project.partials.IntelliJPlatformVerifyPlugin
import org.jetbrains.intellij.platform.gradle.utils.Logger

@Suppress("unused")
abstract class IntelliJPlatformModulePlugin : Plugin<Project> {

    private val log = Logger(javaClass)

    override fun apply(project: Project) {
        log.info("Configuring plugin: ${Plugins.MODULE}")

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
            apply(IntelliJPlatformBuildPlugin::class)
            apply(IntelliJPlatformTestPlugin::class)
            apply(IntelliJPlatformVerifyPlugin::class)
        }
    }
}
