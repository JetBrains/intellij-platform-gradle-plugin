// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.tasks.aware.CustomPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import kotlin.io.path.absolutePathString

/**
 * Runs the IDE instance with the developed plugin installed.
 *
 * `runIde` task extends the [JavaExec] Gradle task – all properties available in the [JavaExec] as well as the following ones can be used to configure the [RunIdeTask] task.
 *
 * @see [RunIdeBase]
 * @see [JavaExec]
 */
@Deprecated(message = "CHECK")
@UntrackedTask(because = "Should always run guest IDE")
abstract class RunIdeTask : JavaExec(), RunnableIdeAware, CustomPlatformVersionAware {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        assertPlatformVersion()

        workingDir = platformPath.toFile()

        super.exec()
    }

    override fun getExecutable() = runtimeExecutable.asPath.absolutePathString()

    companion object {
        // TODO: define `inputs.property` for tasks to consider system properties in terms of the configuration cache
        //       see: https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks/-task-inputs/prop  erty.html
        fun register(project: Project) =
            project.registerTask<RunIdeTask>(Tasks.RUN_IDE) {
                systemPropertyDefault("idea.auto.reload.plugins", true)
                systemPropertyDefault("idea.classpath.index.enabled", false)
                systemPropertyDefault("idea.is.internal", true)
                systemPropertyDefault("idea.plugin.in.sandbox.mode", true)
                systemPropertyDefault("idea.vendor.name", "JetBrains")
                systemPropertyDefault("ide.no.platform.update", false)
                systemPropertyDefault("jdk.module.illegalAccess.silent", true)

                val os = OperatingSystem.current()
                when {
                    os.isMacOsX -> {
                        systemPropertyDefault("idea.smooth.progress", false)
                        systemPropertyDefault("apple.laf.useScreenMenuBar", true)
                        systemPropertyDefault("apple.awt.fileDialogForDirectories", true)
                    }

                    os.isUnix -> {
                        systemPropertyDefault("sun.awt.disablegrab", true)
                    }
                }

//            finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)
            }
    }
}
