// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Unit tests for [VerifyPluginTask.parseNotDynamicReasons], extracting the dynamic plugin eligibility status from the
 * per-IDE Markdown report of the IntelliJ Plugin Verifier — the only report it persists the status into. See: #2255
 */
class VerifyPluginTaskNotDynamicReasonsTest {

    @Test
    fun `reasons of a non-dynamic plugin are collected`() {
        val report = """
            # Plugin com.example:1.0.0 against IU-261.27258.48

            Compatible. 1 usage of internal API

            ## Internal API usages (1)

            ### Internal method com.intellij.Foo.bar() invocation

            * Internal method com.intellij.Foo.bar() is invoked in App.main(). This method is internal.

            ## Dynamic Plugin Status

            Plugin probably cannot be enabled or disabled without IDE restart

            * Declares non-dynamic extensions: `com.intellij.nonDynamicEP`
            * Declares application components: `App`

        """.trimIndent()

        assertEquals(
            listOf(
                "Declares non-dynamic extensions: `com.intellij.nonDynamicEP`",
                "Declares application components: `App`",
            ),
            VerifyPluginTask.parseNotDynamicReasons(report),
        )
    }

    @Test
    fun `reasons of multiple plugins and Windows line endings are not mixed up`() {
        val report = listOf(
            "# Plugin com.example:1.0.0 against IU-261.27258.48",
            "",
            "Compatible",
            "",
            "## Dynamic Plugin Status",
            "",
            "Plugin probably cannot be enabled or disabled without IDE restart",
            "",
            "* Declares application components: `App`",
            "",
            "# Plugin com.example.other:1.0.0 against IU-261.27258.48",
            "",
            "Compatible",
            "",
            "## Plugin structure warnings (1)",
            "",
            "* Not a dynamic plugin restriction",
            "",
            "## Dynamic Plugin Status",
            "",
            "Plugin can probably be enabled or disabled without IDE restart",
            "",
        ).joinToString("\r\n")

        assertEquals(
            listOf("Declares application components: `App`"),
            VerifyPluginTask.parseNotDynamicReasons(report),
        )
    }

    @Test
    fun `dynamic plugins report no reasons`() {
        val report = """
            # Plugin com.example:1.0.0 against IU-261.27258.48

            Compatible

            ## Dynamic Plugin Status

            Plugin can probably be enabled or disabled without IDE restart

        """.trimIndent()

        assertEquals(emptyList(), VerifyPluginTask.parseNotDynamicReasons(report))
    }

    @Test
    fun `reports without the dynamic plugin status are distinguished from dynamic plugins`() {
        val report = """
            # Plugin com.example:1.0.0 against IU-241.14494.240

            Compatible

            ## Deprecated API usages (1)

            ### Deprecated class com.intellij.Foo reference

            * Deprecated class com.intellij.Foo is referenced in App.main()

        """.trimIndent()

        assertNull(VerifyPluginTask.parseNotDynamicReasons(report))
    }
}
