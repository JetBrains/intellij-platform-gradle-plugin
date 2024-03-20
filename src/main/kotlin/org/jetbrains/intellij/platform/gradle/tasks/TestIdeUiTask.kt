// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.jetbrains.intellij.platform.gradle.tasks.aware.CustomIntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.absolutePathString

/**
 * Runs the IDE instance with the developed plugin and robot-server installed and ready for UI testing.
 *
 * @see <a href="https://github.com/JetBrains/intellij-ui-test-robot>IntelliJ UI Test Robot</a>
 *
 * @see RunIdeBase
 * @see JavaExec
 */
@Deprecated(message = "CHECK")
@UntrackedTask(because = "Should always run")
abstract class TestIdeUiTask : JavaExec(), RunnableIdeAware, CustomIntelliJPlatformVersionAware {

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IDE instance with the developed plugin and robot-server installed and ready for UI testing."
    }

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        validateIntelliJPlatformVersion()

        workingDir = platformPath.toFile()

        super.exec()
    }

    override fun getExecutable() = runtimeDirectory.asPath.resolveJavaRuntimeExecutable().absolutePathString()

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<TestIdeUiTask>(Tasks.TEST_IDE_UI) {
            }
    }
}
