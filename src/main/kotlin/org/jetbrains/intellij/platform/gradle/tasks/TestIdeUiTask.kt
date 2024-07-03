// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware

/**
 * Runs the IDE instance with the developed plugin and robot-server installed and ready for UI testing.
 *
 * This task runs against the IntelliJ Platform and plugins specified in project dependencies.
 * To register a customized task, use [IntelliJPlatformTestingExtension.testIdeUi] instead.
 *
 * @see <a href="https://github.com/JetBrains/intellij-ui-test-robot>IntelliJ UI Test Robot</a>
 * @see JavaExec
 */
@UntrackedTask(because = "Should always run")
@Deprecated("Should not be used for testing with the Robot Server Plugin; a placeholder for the new implementation")
abstract class TestIdeUiTask : JavaExec(), RunnableIdeAware, TestableAware, IntelliJPlatformVersionAware {

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        validateIntelliJPlatformVersion()

        workingDir = platformPath.toFile()

        super.exec()
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IDE instance with the developed plugin and robot-server installed and ready for UI testing."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<TestIdeUiTask>(Tasks.TEST_IDE_UI, configureWithType = false) {}
    }
}
