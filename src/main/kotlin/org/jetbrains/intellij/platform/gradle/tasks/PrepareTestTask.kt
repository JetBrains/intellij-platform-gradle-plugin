// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.aware.SandboxAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware

/**
 * This is a task used to prepare an immutable `test` task and provide all necessary dependencies and configuration for a proper testing configuration.
 */
@CacheableTask
abstract class PrepareTestTask : DefaultTask(), TestableAware, SandboxAware {

    init {
        group = Plugin.GROUP_NAME
        description = "Configures the 'test' task to run tests from the current project."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PrepareTestTask>(Tasks.PREPARE_TEST) {
                val prepareTestSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_TEST_SANDBOX)
                applySandboxFrom(prepareTestSandboxTaskProvider)

                project.tasks.named<Test>(Tasks.External.TEST) {
                    dependsOn(this@registerTask)
                }
            }
    }
}
