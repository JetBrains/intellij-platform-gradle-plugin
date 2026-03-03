// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import nl.adaptivity.xmlutil.serialization.XML
import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleDescriptorTest {

    private val input = """
        <?xml version="1.0" encoding="UTF-8"?>
        <module name="intellij.kotlin.jupyter.plots" namespace="jetbrains" visibility="public">
          <dependencies>
            <module name="intellij.kotlin.jupyter.core"/>
            <module name="intellij.platform.core"/>
          </dependencies>
          <resources>
            <resource-root path="../plugins/kotlin-jupyter-plugin/lib/modules/intellij.kotlin.jupyter.plots.jar"/>
          </resources>
        </module>
    """.trimIndent()

    @Test
    fun `decode module descriptor with namespace attribute`() {
        val result = XML.decodeFromString<ModuleDescriptor>(input)

        assertEquals("intellij.kotlin.jupyter.plots", result.name)
        assertEquals("jetbrains", result.namespace)
        assertEquals("public", result.visibility)
        assertEquals(2, result.dependencies.size)
        assertEquals("plugins/kotlin-jupyter-plugin/lib/modules/intellij.kotlin.jupyter.plots.jar", result.path)
    }
}
