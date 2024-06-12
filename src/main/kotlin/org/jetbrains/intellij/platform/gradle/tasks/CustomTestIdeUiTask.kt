// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.platform.gradle.Constants.Plugin

/**
 * Runs the IDE instance with the developed plugin and robot-server installed and ready for UI testing.
 *
 * @see <a href="https://github.com/JetBrains/intellij-ui-test-robot>IntelliJ UI Test Robot</a>
 *
 * @see JavaExec
 */
@UntrackedTask(because = "Should always run")
abstract class CustomTestIdeUiTask : TestIdeUiTask() {

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the custom IDE instance with the developed plugin and robot-server installed and ready for UI testing."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<CustomTestIdeUiTask>(configuration = configuration)
    }
}
