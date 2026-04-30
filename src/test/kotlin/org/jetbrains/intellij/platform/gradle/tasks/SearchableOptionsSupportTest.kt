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

        assertTrue(pluginXml.hasSearchableOptionsContent())
    }

    @Test
    fun `detect qualified configurable EP`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <extensions>
                    <com.intellij.applicationConfigurable instance="example.Configurable" />
                  </extensions>
                </idea-plugin>
                """.trimIndent()
            )
        }

        assertTrue(pluginXml.hasSearchableOptionsContent())
    }

    @Test
    fun `detect custom configurable extension`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <extensions defaultExtensionNs="com.perl5">
                    <settings.configurable.extension implementation="example.ConfigurableExtension" />
                  </extensions>
                </idea-plugin>
                """.trimIndent()
            )
        }

        assertTrue(pluginXml.hasSearchableOptionsContent())
    }

    @Test
    fun `detect custom configurable extension point declaration`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <extensionPoints>
                    <extensionPoint qualifiedName="com.perl5.settings.configurable.extension"
                                    interface="example.SettingsConfigurableExtension" />
                  </extensionPoints>
                </idea-plugin>
                """.trimIndent()
            )
        }

        assertTrue(pluginXml.hasSearchableOptionsContent())
    }

    @Test
    fun `ignore action declarations`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <actions>
                    <action id="Example.Action"
                            class="example.Action"
                            text="Example Action"
                            description="Example action description" />
                  </actions>
                </idea-plugin>
                """.trimIndent()
            )
        }

        assertFalse(pluginXml.hasSearchableOptionsContent())
    }

    @Test
    fun `ignore plugin xml without searchable options content`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <actions />
                </idea-plugin>
                """.trimIndent()
            )
        }

        assertFalse(pluginXml.hasSearchableOptionsContent())
    }

    @Test
    fun `ignore empty xml files`() {
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText("")
        }

        assertFalse(pluginXml.hasSearchableOptionsContent())
    }
}
