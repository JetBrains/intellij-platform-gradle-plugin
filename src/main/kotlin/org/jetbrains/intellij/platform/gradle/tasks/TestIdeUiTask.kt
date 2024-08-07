// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware

/**
 * Runs the IDE instance with the developed plugin and Starter framework for UI testing.
 */
@Incubating
@UntrackedTask(because = "Should always run")
abstract class TestIdeUiTask : Test(), TestableAware {

    /**
     * Specifies the archive file representing the input file to be tested.
     *
     * Default value: [BuildPluginTask.archiveFile]
     */
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
        description = "Runs the IDE instance with the developed plugin and Starter framework for UI testing."
    }

    /**
     * TODO: Make sure it relies on sandbox.
     */
    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<TestIdeUiTask>(configureWithType = false) {
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
