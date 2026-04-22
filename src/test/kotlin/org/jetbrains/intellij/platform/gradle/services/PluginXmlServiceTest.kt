// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.services.BuildServiceParameters
import org.jetbrains.intellij.platform.gradle.tasks.aware.parsePluginXml
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class PluginXmlServiceTest {

    @Test
    fun `reuse parsed plugin xml for unchanged file`() {
        val service = object : PluginXmlService() {
            override fun getParameters() = BuildServiceParameters.None::class.java
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        }
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <id>example.plugin</id>
                  <name>Example</name>
                </idea-plugin>
                """.trimIndent()
            )
        }
        var parses = 0

        val first = service.resolve(pluginXml) {
            parses++
            it.parsePluginXml()
        }
        val second = service.resolve(pluginXml) {
            parses++
            it.parsePluginXml()
        }

        assertEquals("example.plugin", first.id)
        assertSame(first, second)
        assertEquals(1, parses)
    }

    @Test
    fun `invalidate cache when plugin xml content changes`() {
        val service = object : PluginXmlService() {
            override fun getParameters() = BuildServiceParameters.None::class.java
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        }
        val pluginXml = createTempFile("plugin", ".xml").apply {
            writeText(
                """
                <idea-plugin>
                  <id>example.plugin</id>
                  <name>Example</name>
                </idea-plugin>
                """.trimIndent()
            )
        }
        var parses = 0

        service.resolve(pluginXml) {
            parses++
            it.parsePluginXml()
        }
        pluginXml.writeText(
            """
            <idea-plugin>
              <id>example.plugin.updated</id>
              <name>Example</name>
            </idea-plugin>
            """.trimIndent()
        )
        val updated = service.resolve(pluginXml) {
            parses++
            it.parsePluginXml()
        }

        assertEquals("example.plugin.updated", updated.id)
        assertEquals(2, parses)
    }
}
