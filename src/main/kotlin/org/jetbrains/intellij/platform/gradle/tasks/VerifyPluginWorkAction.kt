// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.GradleException
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.process.ExecOperations
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.io.ByteArrayOutputStream
import javax.inject.Inject

internal abstract class VerifyPluginWorkAction @Inject constructor(
    private val execOperations: ExecOperations,
    private val objectFactory: ObjectFactory
) :  WorkAction<VerifyPluginWorkAction.VerifyPluginWorkParameters> {

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
     * @throws GradleException
     */
    @Throws(GradleException::class)
    private fun verifyOutput(output: String, failureLevel: ListProperty<FailureLevel>, ) {
        log.debug("Current failure levels: ${FailureLevel.values().joinToString(", ")}")

        val invalidFilesMessage = "The following files specified for the verification are not valid plugins:"
        if (output.contains(invalidFilesMessage)) {
            val errorMessage = output.lines()
                .dropWhile { it != invalidFilesMessage }
                .dropLastWhile { !it.startsWith(" ") }
                .joinToString("\n")

            throw GradleException(errorMessage)
        }

        FailureLevel.values().forEach { level ->
            if (failureLevel.get().contains(level) && output.contains(level.sectionHeading)) {
                log.debug("Failing task on '$failureLevel' failure level")
                throw GradleException(
                    "$level: ${level.message} Check Plugin Verifier report for more details.\n" +
                            "Incompatible API Changes: https://jb.gg/intellij-api-changes"
                )
            }
        }
    }

    interface VerifyPluginWorkParameters : WorkParameters {
        val getArgs: ListProperty<String>
        val getPluginVerifierPath: Property<String>
        val getFailureLevel: ListProperty<FailureLevel>
    }
}




