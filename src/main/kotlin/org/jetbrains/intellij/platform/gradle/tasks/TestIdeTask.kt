// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.asPath
import org.jetbrains.intellij.platform.gradle.tasks.base.*
import kotlin.io.path.absolutePathString

/**
 * Runs the IDE instance with the developed plugin installed.
 *
 * `runIde` task extends the [JavaExec] Gradle task – all properties available in the [JavaExec] as well as the following ones can be used to configure the [TestIdeTask] task.
 *
 * @see [RunIdeBase]
 * @see [JavaExec]
 */
@UntrackedTask(because = "Should always run guest IDE")
abstract class TestIdeTask : Test(), CoroutinesJavaAgentAware, CustomPlatformVersionAware, JetBrainsRuntimeAware, SandboxAware {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    @TaskAction
    override fun executeTests() {
        assertPlatformVersion()

        super.executeTests()
    }

    override fun getExecutable(): String = jetbrainsRuntimeExecutable.asPath.absolutePathString()
}
