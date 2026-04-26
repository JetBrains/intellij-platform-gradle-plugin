// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class IdeLayoutIndexTest {

    @Test
    fun `preserves first matching ids and module aliases`() {
        val first = IdeLayoutIndex.Entry(
            key = "entry-0",
            id = "com.intellij.example",
            definedModules = listOf("com.intellij.example", "example.alias"),
        )
        val second = IdeLayoutIndex.Entry(
            key = "entry-1",
            id = "com.intellij.example",
            definedModules = listOf("example.alias", "example.alias.2"),
        )
        val third = IdeLayoutIndex.Entry(
            key = "entry-2",
            id = "com.intellij.example.module",
            isModule = true,
            definedModules = listOf("com.intellij.example.module"),
        )

        val ideLayoutIndex = IdeLayoutIndex(
            fullVersion = "IU-999.0",
            entries = listOf(first, second, third),
        )

        assertSame(first, ideLayoutIndex.findById("com.intellij.example"))
        assertSame(first, ideLayoutIndex.findByModuleId("example.alias"))
        assertSame(second, ideLayoutIndex.findByModuleId("example.alias.2"))
        assertSame(third, ideLayoutIndex.findByKey("entry-2"))
    }

    @Test
    fun `bundled entry views split plugins and modules`() {
        val plugin = IdeLayoutIndex.Entry(
            key = "entry-0",
            id = "com.intellij.example",
        )
        val module = IdeLayoutIndex.Entry(
            key = "entry-1",
            id = "com.intellij.example.module",
            isModule = true,
        )

        val ideLayoutIndex = IdeLayoutIndex(
            fullVersion = "IU-999.0",
            entries = listOf(plugin, module),
        )

        assertEquals(listOf(plugin), ideLayoutIndex.bundledPlugins.toList())
        assertEquals(listOf(module), ideLayoutIndex.bundledModules.toList())
    }
}
