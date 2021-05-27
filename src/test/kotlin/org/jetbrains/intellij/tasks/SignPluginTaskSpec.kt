package org.jetbrains.intellij.tasks

import org.gradle.util.VersionNumber
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import org.junit.Assume.assumeFalse
import kotlin.test.Test
import kotlin.test.assertTrue

class SignPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `reuse configuration cache`() {
        assumeFalse("Feature supported since Gradle 6.6", VersionNumber.parse(gradleVersion) < VersionNumber.parse("6.6"))

        build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
