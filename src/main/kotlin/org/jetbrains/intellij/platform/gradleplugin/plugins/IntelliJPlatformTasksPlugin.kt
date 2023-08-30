// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.register
import org.jetbrains.intellij.platform.gradleplugin.BuildFeature.SELF_UPDATE_CHECK
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.platform.gradleplugin.checkGradleVersion
import org.jetbrains.intellij.platform.gradleplugin.info
import org.jetbrains.intellij.platform.gradleplugin.isBuildFeatureEnabled
import org.jetbrains.intellij.platform.gradleplugin.logCategory
import org.jetbrains.intellij.platform.gradleplugin.tasks.InitializeIntelliJPluginTask
import org.jetbrains.intellij.platform.gradleplugin.tasks.SetupDependenciesTask
import java.time.LocalDate

abstract class IntelliJPlatformTasksPlugin : Plugin<Project> {

    private lateinit var context: String

    override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: org.jetbrains.intellij.platform.tasks")
        checkGradleVersion()

        with(project.plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        with(project.tasks) {
            configureTask<SetupDependenciesTask>(SETUP_DEPENDENCIES_TASK_NAME)

            configureTask<InitializeIntelliJPluginTask>(INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME) {
                offline.convention(project.gradle.startParameter.isOffline)
                selfUpdateCheck.convention(project.isBuildFeatureEnabled(SELF_UPDATE_CHECK))
                lockFile.convention(project.provider {
                    temporaryDir.resolve(LocalDate.now().toString())
                })

                onlyIf { !lockFile.get().exists() }
            }

            // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
            (TASKS - INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME).forEach {
//                named(it) { dependsOn(INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME) }
            }
        }
    }

    private inline fun <reified T : Task> TaskContainer.configureTask(name: String, noinline configuration: T.() -> Unit = {}) {
        info(context, "Configuring task: $name")
        val task = findByName(name) as? T ?: register<T>(name).get()
        task.configuration()
    }
}
