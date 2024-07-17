// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
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
abstract class TestIdeUiTask : Test(), TestableAware {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveFile: RegularFileProperty

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun executeTests() {
        validateIntelliJPlatformVersion()

        systemProperty("path.to.build.plugin", archiveFile.get())

        super.executeTests()
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IDE instance with the developed plugin and robot-server installed and ready for UI testing."
    }

    /**
     * TODO: Make sure it relies on sandbox.
     */
    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<TestIdeUiTask>(Tasks.TEST_IDE_UI, configureWithType = false) {
                val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)
//                val prepareTestIdeUiSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_TEST_IDE_UI_SANDBOX)

//                applySandboxFrom(prepareTestIdeUiSandboxTaskProvider)
                archiveFile.convention(buildPluginTaskProvider.flatMap { it.archiveFile })

//                jvmArgumentProviders.add(
//                    SandboxArgumentProvider(
//                        sandboxConfigDirectory,
//                        sandboxPluginsDirectory,
//                        sandboxSystemDirectory,
//                        sandboxLogDirectory,
//                    )
//                )
            }
    }
}
