// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.workers

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.io.ByteArrayOutputStream
import javax.inject.Inject

/**
 * A `WorkAction` provided by the Gradle Worker API that executes the Plugin Verifier
 * tool to check the compatibility of a plugin against specified IDE builds.
 *
 * @param execOperations The Gradle `ExecOperations` service for executing external processes.
 * @param objectFactory The Gradle `ObjectFactory` service for creating domain objects.
 * @see org.gradle.workers.WorkAction
 * @see VerifyPluginWorkParameters
 * @see org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
 */
internal abstract class VerifyPluginWorkAction @Inject constructor(
    private val execOperations: ExecOperations,
    private val objectFactory: ObjectFactory
) : WorkAction<VerifyPluginWorkAction.VerifyPluginWorkParameters> {

    /**
     * Defines the parameters for the [VerifyPluginWorkAction].
     *
     * This interface is used to provide the necessary inputs to the work action
     *
     * @see org.gradle.workers.WorkParameters
     */
    interface VerifyPluginWorkParameters : WorkParameters {
        /**
         * The command-line arguments to be passed to the Plugin Verifier.
         *
         * These arguments control the verification process, such as specifying which IDE versions to check against.
         */
        val getArgs: ListProperty<String>

        /**
         * The path to the Plugin Verifier executable
         */
        val getPluginVerifierPath: Property<String>

        /**
         * The list of failure levels that will cause the verification task to fail the build.
         *
         * @see org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
         */
        val getFailureLevel: ListProperty<VerifyPluginTask.FailureLevel>
    }

    private val log = Logger(javaClass)

    override fun execute() {
        ByteArrayOutputStream().use { os ->
            val outputStream = TeeOutputStream(System.out, os)

            execOperations.javaexec {
                mainClass.set("com.jetbrains.pluginverifier.PluginVerifierMain")

                classpath = objectFactory.fileCollection().from(
                    parameters.getPluginVerifierPath.get()
                )

                standardOutput = outputStream

                args(parameters.getArgs.get())
            }

            verifyOutput(os.toString(), parameters.getFailureLevel)
        }
    }

    /**
     * @throws org.gradle.api.GradleException
     */
    @Throws(GradleException::class)
    private fun verifyOutput(output: String, failureLevel: ListProperty<VerifyPluginTask.FailureLevel>, ) {
        log.debug("Current failure levels: ${VerifyPluginTask.FailureLevel.values().joinToString(", ")}")

        val invalidFilesMessage = "The following files specified for the verification are not valid plugins:"
        if (output.contains(invalidFilesMessage)) {
            val errorMessage = output.lines()
                .dropWhile { it != invalidFilesMessage }
                .dropLastWhile { !it.startsWith(" ") }
                .joinToString("\n")

            throw GradleException(errorMessage)
        }

        VerifyPluginTask.FailureLevel.values().forEach { level ->
            if (failureLevel.get().contains(level) && output.contains(level.sectionHeading)) {
                log.debug("Failing task on '$failureLevel' failure level")
                throw GradleException(
                    "$level: ${level.message} Check Plugin Verifier report for more details.\n" +
                            "Incompatible API Changes: https://jb.gg/intellij-api-changes"
                )
            }
        }
    }
}