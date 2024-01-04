// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.checkGradleVersion
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.info
import org.jetbrains.intellij.platform.gradle.logCategory

abstract class IntelliJPlatformAbstractProjectPlugin(val pluginId: String) : Plugin<Project> {

    protected lateinit var context: String

    protected val Project.extensionProvider
        get() = project.provider { project.the<IntelliJPlatformExtension>() }

    final override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: $pluginId")
        checkGradleVersion()

        project.configure()
    }

    protected abstract fun Project.configure()
}
