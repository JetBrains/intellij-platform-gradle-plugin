// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Project
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradleplugin.BuildFeature.SELF_UPDATE_CHECK
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_TASKS_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradleplugin.isBuildFeatureEnabled
import org.jetbrains.intellij.platform.gradleplugin.tasks.InitializeIntelliJPluginTask
import org.jetbrains.intellij.platform.gradleplugin.tasks.SetupDependenciesTask
import java.time.LocalDate
import kotlin.io.path.exists

abstract class IntelliJPlatformTasksPlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_TASKS_ID) {

    override fun Project.configure() {
        with(plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        with(tasks) {
            configureTask<SetupDependenciesTask>(Tasks.SETUP_DEPENDENCIES)

            configureTask<InitializeIntelliJPluginTask>(INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME) {
                offline.convention(project.gradle.startParameter.isOffline)
                selfUpdateCheck.convention(project.isBuildFeatureEnabled(SELF_UPDATE_CHECK))
                selfUpdateLockPath.convention(project.provider {
                    temporaryDir.toPath().resolve(LocalDate.now().toString())
                })
                coroutinesJavaAgentPath.convention(project.provider {
                    temporaryDir.toPath().resolve("coroutines-javaagent.jar")
                })

                onlyIf {
                    !selfUpdateLockPath.get().exists() || !coroutinesJavaAgentPath.get().exists()
                }
            }
        }

        // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
        (TASKS - INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME).forEach {
            // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
            (TASKS - INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME).forEach {
//                named(it) { dependsOn(INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME) }
            }
        }
    }
}

