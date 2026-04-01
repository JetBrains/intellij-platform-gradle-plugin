// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlin.test.Test
import kotlin.test.assertEquals

class ModuleDescriptorTest {

    private val input = """
        <?xml version="1.0" encoding="UTF-8"?>
        <module name="intellij.kotlin.jupyter.plots" namespace="jetbrains" visibility="public">
          <dependencies>
            <module name="intellij.kotlin.jupyter.core"/>
            <module name="intellij.platform.codeStyle" namespace="jps"/>
            <module name="intellij.platform.core" visibility="public"/>
          </dependencies>
          <resources>
            <resource-root path="../plugins/kotlin-jupyter-plugin/lib/modules/intellij.kotlin.jupyter.plots.jar"/>
          </resources>
        </module>
    """.trimIndent()

    @Test
    fun `decode module descriptor with additional dependency attributes`() {
        val result = decode<ModuleDescriptor>(input)

        assertEquals("intellij.kotlin.jupyter.plots", result.name)
        assertEquals("jetbrains", result.namespace)
        assertEquals("public", result.visibility)
        assertEquals(3, result.dependencies.size)
        assertEquals("intellij.platform.codeStyle", result.dependencies[1].name)
        assertEquals("intellij.platform.core", result.dependencies[2].name)
        assertEquals("plugins/kotlin-jupyter-plugin/lib/modules/intellij.kotlin.jupyter.plots.jar", result.path)
    }
}
