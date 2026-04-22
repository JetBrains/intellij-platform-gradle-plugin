// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchableOptionsSupportTest {

    @Test
    fun `detect configurable EP in IntelliJ extensions namespace`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <extensions defaultExtensionNs="com.intellij">
                    <projectConfigurable instance="example.Configurable" />
                  </extensions>
                </idea-plugin>
                """.trimIndent()
            )
        }

        assertTrue(pluginXml.hasConfigurableExtensionPoint())
    }

    @Test
    fun `ignore plugin xml without configurable EPs`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <actions />
                </idea-plugin>
                """.trimIndent()
            )
        }

        assertFalse(pluginXml.hasConfigurableExtensionPoint())
    }

    @Test
    fun `ignore empty xml files`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText("")
        }

        assertFalse(pluginXml.hasConfigurableExtensionPoint())
    }
}
