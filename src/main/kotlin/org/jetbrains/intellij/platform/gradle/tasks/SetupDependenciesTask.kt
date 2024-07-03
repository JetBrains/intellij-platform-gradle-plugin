// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.Logger

/**
 * A deprecated method for setting up IntelliJ Platform dependencies.
 *
 * The `setupDependencies` task was automatically added to the ["After Sync" Gradle trigger](https://www.jetbrains.com/help/idea/work-with-gradle-tasks.html#config_triggers_gradle) to make the IntelliJ SDK dependency available for IntelliJ IDEA right after the Gradle synchronization.
 * With the IntelliJ Platform Gradle Plugin 2.0 release, this method is no longer needed as the native Gradle dependencies resolution is in use.
 *
 * To remove any references to this task, call the "Tasks Activation" action and remove the `setupDependencies` entry from the "After Sync" group.
 */
@DisableCachingByDefault(because = "No output state to track")
abstract class SetupDependenciesTask : DefaultTask() {

    @TaskAction
    fun setupDependencies() {
        Logger(javaClass).error(
            """
            The setupDependencies task is scheduled for removal.
            See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-migration.html#setupdependencies
            """.trimIndent()
        )
    }

    init {
        group = Plugin.GROUP_NAME
        description = "The setupDependencies task is scheduled for removal"
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<SetupDependenciesTask>(Tasks.SETUP_DEPENDENCIES)
    }
}
