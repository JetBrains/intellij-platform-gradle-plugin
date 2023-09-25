// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.register
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradleplugin.checkGradleVersion
import org.jetbrains.intellij.platform.gradleplugin.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradleplugin.info
import org.jetbrains.intellij.platform.gradleplugin.logCategory
import org.jetbrains.intellij.platform.gradleplugin.tasks.base.IdeVersionAwareTask

abstract class IntelliJPlatformAbstractProjectPlugin(val pluginId: String) : Plugin<Project> {

    protected lateinit var context: String

    final override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: $pluginId")
        checkGradleVersion()

        project.configure()
    }

    protected abstract fun Project.configure()

    protected inline fun <reified T : Task> TaskContainer.configureTask(name: String, noinline configuration: T.() -> Unit = {}) {
        info(context, "Configuring task: $name")
        val task = findByName(name) as? T ?: register<T>(name).get()

        with(task) {
            if (this is IdeVersionAwareTask) {
                intelliJPlatformArtifacts = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM_BUILD_NUMBER)
            }
            configuration()
        }
    }

    protected inline fun <reified T : Any> Any.configureExtension(name: String, noinline configuration: T.() -> Unit = {}) {
        info(context, "Configuring extension: $name")
        with((this as ExtensionAware).extensions) {
            val extension = findByName(name) as? T ?: create<T>(name)
            extension.configuration()
        }
    }

    protected val Project.intelliJPlatformExtension
        get() = extensions.getByName<IntelliJPlatformExtension>(Extensions.INTELLIJ_PLATFORM)
}
