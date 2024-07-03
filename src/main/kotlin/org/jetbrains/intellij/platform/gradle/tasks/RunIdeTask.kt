// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.named
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import kotlin.io.path.pathString

/**
 * Runs the IDE instance using the currently selected IntelliJ Platform with the built plugin loaded.
 * It directly extends the [JavaExec] Gradle task, which allows for an extensive configuration (system properties, memory management, etc.).
 *
 * This task runs against the IntelliJ Platform and plugins specified in project dependencies.
 * To register a customized task, use [IntelliJPlatformTestingExtension.runIde] instead.
 */
@UntrackedTask(because = "Should always run")
abstract class RunIdeTask : JavaExec(), RunnableIdeAware, SplitModeAware, IntelliJPlatformVersionAware {

    /**
     * Executes the task, configures and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        validateIntelliJPlatformVersion()
        validateSplitModeSupport()

        workingDir = platformPath.toFile()

        if (splitMode.get()) {
            environment("JETBRAINS_CLIENT_JDK", runtimeDirectory.asPath.pathString)
            environment("JETBRAINS_CLIENT_PROPERTIES", splitModeFrontendProperties.asPath.pathString)

            if (args.isNotEmpty()) {
                throw InvalidUserDataException("Passing arguments directly is not supported in Split Mode. Use `argumentProviders` instead.")
            }
        }

        systemPropertyDefault("idea.auto.reload.plugins", autoReload.get())

        super.exec()
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IDE instance with the developed plugin installed."
    }

    companion object : Registrable {
        override fun register(project: Project) {
            project.registerTask<RunIdeTask>(Tasks.RUN_IDE, configureWithType = false) {
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
                applySandboxFrom(prepareSandboxTaskProvider)
            }

            project.registerTask<RunIdeTask> {
                systemPropertyDefault("idea.classpath.index.enabled", false)
                systemPropertyDefault("idea.is.internal", true)
                systemPropertyDefault("idea.plugin.in.sandbox.mode", true)
                systemPropertyDefault("idea.vendor.name", "JetBrains")
                systemPropertyDefault("ide.no.platform.update", false)
                systemPropertyDefault("jdk.module.illegalAccess.silent", true)

                with(OperatingSystem.current()) {
                    when {
                        isMacOsX -> {
                            systemPropertyDefault("idea.smooth.progress", false)
                            systemPropertyDefault("apple.laf.useScreenMenuBar", true)
                            systemPropertyDefault("apple.awt.fileDialogForDirectories", true)
                        }

                        isUnix -> {
                            systemPropertyDefault("sun.awt.disablegrab", true)
                        }
                    }
                }
            }
        }

        internal fun JavaForkOptions.systemPropertyDefault(name: String, defaultValue: Any) {
            if (!systemProperties.containsKey(name)) {
                systemProperty(name, defaultValue)
            }
        }
    }
}
