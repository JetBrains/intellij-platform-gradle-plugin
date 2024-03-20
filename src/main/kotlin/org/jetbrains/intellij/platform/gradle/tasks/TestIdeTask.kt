// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.tasks.aware.*

/**
 * Runs plugin tests against the currently selected IntelliJ Platform with the built plugin loaded.
 * It directly extends the [Test] Gradle task, which allows for an extensive configuration (system properties, memory management, etc.).
 *
 * This task class also inherits from [CustomIntelliJPlatformVersionAware],
 * which makes it possible to create `testIde`-like tasks using custom IntelliJ Platform versions:
 *
 * ```
 * import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
 * import org.jetbrains.intellij.platform.gradle.tasks.TestIdeTask
 *
 * tasks {
 *   val testPhpStorm by registering(TestIdeTask::class) {
 *     type = IntelliJPlatformType.PhpStorm
 *     version = "2023.2.2"
 *   }
 *
 *   val testLocalIde by registering(TestIdeTask::class) {
 *     localPath = file("/Users/hsz/Applications/Android Studio.app")
 *   }
 * }
 * ```
 */
@UntrackedTask(because = "Should always run")
abstract class TestIdeTask : Test(), CoroutinesJavaAgentAware, CustomIntelliJPlatformVersionAware, PluginAware, RuntimeAware, SandboxAware {

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    @TaskAction
    override fun executeTests() {
        validateIntelliJPlatformVersion()

        super.executeTests()
    }
}
