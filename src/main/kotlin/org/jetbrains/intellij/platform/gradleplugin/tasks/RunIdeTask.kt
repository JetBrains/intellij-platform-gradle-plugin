// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradleplugin.asFile
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.model.getBootClasspath
import org.jetbrains.intellij.platform.gradleplugin.or
import org.jetbrains.intellij.platform.gradleplugin.tasks.base.*
import java.io.File
import kotlin.io.path.absolutePathString

/**
 * Runs the IDE instance with the developed plugin installed.
 *
 * `runIde` task extends the [JavaExec] Gradle task – all properties available in the [JavaExec] as well as the following ones can be used to configure the [RunIdeTask] task.
 *
 * @see [RunIdeBase]
 * @see [JavaExec]
 */
@UntrackedTask(because = "Should always run guest IDE")
abstract class RunIdeTask : JavaExec(), CoroutinesJavaAgentAware, CustomPlatformAware, JetBrainsRuntimeAware, PlatformVersionAware, SandboxAware {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        workingDir = intellijPlatformDirectory.dir("bin").asFile

        configureClasspath()

        super.exec()
    }

    override fun getExecutable(): String = jetbrainsRuntimeExecutable.asPath.absolutePathString()

    /**
     * Prepares the classpath for the IDE based on the IDEA version.
     */
    private fun configureClasspath() {
        executable
            .takeUnless(String?::isNullOrEmpty)
            ?.let {
                resolveToolsJar(it)
                    .takeIf(File::exists)
                    .or(Jvm.current().toolsJar)
            }
            ?.let {
                classpath += objectFactory.fileCollection().from(it)
            }

        classpath += objectFactory.fileCollection().from(
            productInfo.getBootClasspath(intellijPlatformDirectory.asPath)
        )
    }

    /**
     * Resolves the path to the `tools.jar` library.
     */
    private fun resolveToolsJar(javaExec: String): File {
        val binDir = File(javaExec).parent
        val path = when {
            OperatingSystem.current().isMacOsX -> "../../lib/tools.jar"
            else -> "../lib/tools.jar"
        }
        return File(binDir, path)
    }
}
