// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Project
import org.gradle.api.plugins.PluginContainer
import org.gradle.api.tasks.TaskContainer
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradleplugin.BuildFeature.SELF_UPDATE_CHECK
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INTELLIJ_PLATFORM_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_TASKS_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.platform.gradleplugin.isBuildFeatureEnabled
import org.jetbrains.intellij.platform.gradleplugin.tasks.InitializeIntelliJPluginTask
import org.jetbrains.intellij.platform.gradleplugin.tasks.SetupDependenciesTask
import java.time.LocalDate

abstract class IntelliJPlatformTasksPlugin : IntelliJPlatformAbstractPlugin(PLUGIN_TASKS_ID) {

    override fun PluginContainer.applyPlugins(project: Project) {
        apply(IntelliJPlatformBasePlugin::class)
    }

    override fun TaskContainer.applyTasks(project: Project) {
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
        // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
        (TASKS - INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME).forEach {
//                named(it) { dependsOn(INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME) }
        }
    }
}
