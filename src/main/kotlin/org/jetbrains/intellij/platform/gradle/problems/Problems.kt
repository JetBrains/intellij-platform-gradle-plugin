package org.jetbrains.intellij.platform.gradle.problems

import org.gradle.api.Action
import org.gradle.api.problems.*
import org.jetbrains.intellij.platform.gradle.Constants.Plugin.ID
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask

/**
 * Contains problem IDs to be used when reporting a problem with the Problems API
 * for the IntelliJ Platform Gradle Plugin.
 *
 * @see org.gradle.api.problems.ProblemId
 * @see org.gradle.api.problems.Problems
 */
@Suppress("UnstableApiUsage")
internal object Problems {

    /**
     * Contains [ProblemGroup]s to be used with the Problems API for the IntelliJ Platform Gradle Plugin.
     */
    private object Groups {
        val IntelliJPlatformPlugin = ProblemGroup.create(
            "intellij-platform-plugin-group",
            "IntelliJ Platform Plugin Problems"
        )

        val VerifyPlugin = ProblemGroup.create("$ID.verify-plugin", "Verify Plugin")
        val VerifyPluginProjectConfiguration = ProblemGroup.create(
            "$ID.verify-plugin-project-configuration",
            "Verify Plugin Project Configuration",
        )
    }

    object VerifyPlugin {
        val InvalidPlugin = ProblemId.create(
            "invalid-plugin",
            "Invalid Plugin",
            Groups.VerifyPlugin,
        )
        val InvalidPluginVerifier = ProblemId.create(
            "invalid-plugin-verifier",
            "Invalid Plugin Verifer",
            Groups.VerifyPlugin,
        )

        val InvalidIDEs = ProblemId.create(
            "no-ides-found",
            "No IDEs Found",
            Groups.VerifyPlugin,
        )

        val VerificationFailure = { level: VerifyPluginTask.FailureLevel ->
            ProblemId.create(
                "verification-failure-${level}",
                level.sectionHeading,
                Groups.VerifyPlugin,
            )
        }
    }

    object VerifyPluginProjectConfiguration {
        val ConfigurationIssue = ProblemId.create(
            "configuration-issue",
            "Plugin Configuration Issue",
            Groups.VerifyPluginProjectConfiguration,
        )
    }
}

/**
 * Helper function to report an error using the Problems API.
 *
 * @param exception The exception to report. It is suppressed to allow a wrapper exception be thrown with an updated message.
 * @param problemId The ID of the problem to report.
 * @param problemsReportUrl Optional URL used to include the report file's path to the exception's message.
 * Expected value: [org.gradle.api.file.ProjectLayout.getBuildDirectory]/reports/problems/problems-report.html
 * @param spec An action that further configures the problem specification.
 * @return A RuntimeException that includes the original exception and the problem details.
 */
@Suppress("UnstableApiUsage")
internal fun ProblemReporter.reportError(
    exception: Exception,
    problemId: ProblemId,
    problemsReportUrl: String?,
    spec: Action<ProblemSpec>
): RuntimeException {

    val message = buildString {
        append(exception.message)
        if (problemsReportUrl != null) {
            append("${System.lineSeparator()}[Incubating] See full report here: $problemsReportUrl")
        }
    }

    return throwing(RuntimeException(message).also { it.addSuppressed(exception) }, problemId){
        spec.execute(this)

        withException(exception)
        severity(Severity.ERROR)
    }
}
