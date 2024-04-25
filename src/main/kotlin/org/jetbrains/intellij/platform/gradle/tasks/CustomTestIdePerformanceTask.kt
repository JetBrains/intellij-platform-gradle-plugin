// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.aware.CustomIntelliJPlatformVersionAware

/**
 * Runs performance tests on the IDE with the developed plugin installed.
 *
 * The [CustomTestIdePerformanceTask] task extends the [RunIdeBase] task, so all configuration attributes of [JavaExec] and [RunIdeTask] tasks can be used in the [CustomTestIdePerformanceTask] as well.
 * See [RunIdeTask] task for more details.
 *
 * Currently, the task is under adaptation; more documentation will be added in the future.
 *
 * @see RunIdeTask
 * @see JavaExec
 */
@Deprecated(message = "CHECK")
@Incubating
@UntrackedTask(because = "Should always run")
abstract class CustomTestIdePerformanceTask : TestIdePerformanceTask(), CustomIntelliJPlatformVersionAware {

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<CustomTestIdePerformanceTask>(configuration = configuration)
    }
}
