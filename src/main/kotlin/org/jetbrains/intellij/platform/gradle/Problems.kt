package org.jetbrains.intellij.platform.gradle

import org.gradle.api.Action
import org.gradle.api.problems.ProblemGroup
import org.gradle.api.problems.ProblemId
import org.gradle.api.problems.ProblemReporter
import org.gradle.api.problems.ProblemSpec
import org.gradle.api.problems.Severity
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import java.lang.RuntimeException

/**
 * Contains problem groups to be used with the Problems API for the IntelliJ Platform Gradle plugin.
 *
 * @see org.gradle.api.problems.ProblemGroup
 * @see org.gradle.api.problems.Problems
 */
internal object ProblemGroups {
    val IntelliJPlatformPlugin = ProblemGroup.create(
        "intellij-platform-plugin-group",
        "IntelliJ Platform Plugin Problems"
    )
}

/**
 * Contains problem IDs to be used when reporting a problem with the Problems API
 * for the IntelliJ Platform Gradle plugin.
 *
 * @see org.gradle.api.problems.ProblemId
 * @see org.gradle.api.problems.Problems
 */
internal object ProblemIds {

    object VerifyPluginTaskIds {
        val InvalidPlugin = intelliJPlatformPluginId(
            "invalid-plugin",
            "Invalid Plugin"
        )
        val InvalidPluginVerifier = intelliJPlatformPluginId(
            "invalid-plugin-verifier",
            "Invalid Plugin Verifer"
        )

        val InvalidIDEs = intelliJPlatformPluginId(
            "no-ides-found",
            "No IDEs Found"
        )

        val FailingTaskOnFailureLevel = { level: VerifyPluginTask.FailureLevel ->
            intelliJPlatformPluginId(
                "failing-task-on-${level}",
                level.sectionHeading
            )
        }
    }

    private fun intelliJPlatformPluginId(name: String, displayName: String): ProblemId {
        return ProblemId.create(name, displayName, ProblemGroups.IntelliJPlatformPlugin)
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