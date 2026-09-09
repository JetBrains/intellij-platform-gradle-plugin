// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.FailureLevel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for the pure verdict parser [VerifyPluginTask.parseVerdict], covering every [FailureLevel] against
 * verdict strings mirroring the IntelliJ Plugin Verifier `verification-verdict.txt` output, so a broken mapping
 * cannot silently pass. See: #1739
 */
class VerifyPluginTaskFailureLevelTest {

    /**
     * A realistic per-category verdict fragment, matching the phrasing produced by the Plugin Verifier's
     * `PluginVerificationResult.verificationVerdict`. [FailureLevel.NOT_DYNAMIC] is intentionally absent because the
     * verifier does not persist the dynamic plugin eligibility status.
     */
    private val verdictSamples = mapOf(
        FailureLevel.MISSING_DEPENDENCIES to "1 missing mandatory dependency",
        FailureLevel.COMPATIBILITY_PROBLEMS to "3 compatibility problems",
        FailureLevel.COMPATIBILITY_WARNINGS to "2 compatibility warnings",
        FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES to "1 usage of scheduled for removal API",
        FailureLevel.DEPRECATED_API_USAGES to "4 usages of deprecated API",
        FailureLevel.EXPERIMENTAL_API_USAGES to "2 usages of experimental API",
        FailureLevel.INTERNAL_API_USAGES to "1 usage of internal API",
        FailureLevel.NON_EXTENDABLE_API_USAGES to "1 non-extendable API usage violation",
        FailureLevel.OVERRIDE_ONLY_API_USAGES to "1 override-only API usage violation",
        FailureLevel.PLUGIN_STRUCTURE_WARNINGS to "2 plugin configuration defects",
        FailureLevel.INVALID_PLUGIN to "Plugin is invalid: META-INF/plugin.xml is not found",
    )

    @Test
    fun `every persisted failure level is detected from its verdict fragment`() {
        verdictSamples.forEach { (level, verdict) ->
            val parsed = VerifyPluginTask.parseVerdict(verdict)
            assertEquals(
                setOf(level),
                parsed,
                "Verdict '$verdict' should map exactly to $level but produced $parsed",
            )
        }
    }

    @Test
    fun `every failure level except NOT_DYNAMIC declares a verdict marker, and NOT_DYNAMIC does not`() {
        FailureLevel.values().forEach { level ->
            when (level) {
                FailureLevel.NOT_DYNAMIC -> assertTrue(
                    level.verdictMarker == null,
                    "$level must not declare a verdict marker (the verifier does not persist it)",
                )

                else -> {
                    assertTrue(
                        level.verdictMarker != null,
                        "$level must declare a verdict marker so it can be detected from the verdict file",
                    )
                    assertTrue(
                        verdictSamples.containsKey(level),
                        "$level is missing a realistic verdict sample in this test",
                    )
                }
            }
        }
    }

    @Test
    fun `NOT_DYNAMIC is never produced by the pure parser`() {
        // Even if the plain-output dynamic status message leaks into the text, the parser must not report it,
        // because the verifier never writes it into the verdict file.
        val verdict = "Compatible. ${FailureLevel.NOT_DYNAMIC.message}"
        assertFalse(FailureLevel.NOT_DYNAMIC in VerifyPluginTask.parseVerdict(verdict))
    }

    @Test
    fun `a compatible verdict reports no failure levels`() {
        assertEquals(emptySet(), VerifyPluginTask.parseVerdict("Compatible"))
    }

    @Test
    fun `a mixed verdict reports every contained failure level`() {
        val verdict = buildString {
            append("1 missing mandatory dependency")
            append(". 2 compatibility problems, some of which may be caused by absence of dependency")
            append(". 1 compatibility warning")
            append(". 1 usage of scheduled for removal API and 3 usages of deprecated API")
            append(". 2 usages of experimental API")
            append(". 1 usage of internal API")
            append(". 1 non-extendable API usage violation")
            append(". 1 override-only API usage violation")
            append(". 2 plugin configuration defects")
        }

        val expected = setOf(
            FailureLevel.MISSING_DEPENDENCIES,
            FailureLevel.COMPATIBILITY_PROBLEMS,
            FailureLevel.COMPATIBILITY_WARNINGS,
            FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            FailureLevel.DEPRECATED_API_USAGES,
            FailureLevel.EXPERIMENTAL_API_USAGES,
            FailureLevel.INTERNAL_API_USAGES,
            FailureLevel.NON_EXTENDABLE_API_USAGES,
            FailureLevel.OVERRIDE_ONLY_API_USAGES,
            FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
        )

        assertEquals(expected, VerifyPluginTask.parseVerdict(verdict))
    }

    @Test
    fun `scheduled-for-removal and deprecated verdicts are distinguished`() {
        assertEquals(
            setOf(FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES),
            VerifyPluginTask.parseVerdict("1 usage of scheduled for removal API"),
        )
        assertEquals(
            setOf(FailureLevel.DEPRECATED_API_USAGES),
            VerifyPluginTask.parseVerdict("2 usages of deprecated API"),
        )
    }
}
