// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.io.path.readText
import kotlin.test.*

class IntelliJPlatformDiscoverTaskTest : IntelliJPluginTestBase() {

    override val enableIntelliJPlatformCache = true

    private fun report() = Json.parseToJsonElement(dir.resolve("build/reports/intellij-platform/discover.json").readText()).jsonObject
    private fun data() = report().getValue("data").jsonObject

    @Test
    fun `plugins are filtered by query and preserve exact ids`() {
        val result = build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=plugins", "--query=java")
        assertEquals(listOf(":initializeIntellijPlatformPlugin", ":intellijPlatformDiscover"), result.tasks.map { it.path })
        assertEquals("complete", report().getValue("status").jsonPrimitive.content)
        assertEquals("IU", data().getValue("targetIde").jsonObject.getValue("productCode").jsonPrimitive.content)
        val items = data().getValue("items").jsonArray.map { it.jsonObject }
        assertTrue(items.size > 1)
        assertTrue(items.all { item -> listOf("id", "name", "aliases").any { "java" in item.getValue(it).toString().lowercase() } })
        assertTrue(items.any { it.getValue("id").jsonPrimitive.content == "com.intellij.java" })
    }

    @Test
    fun `modules expose aliases dependencies and deterministic config cache reports`() {
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=modules", args = listOf("--offline"))
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=modules", args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":initializeIntellijPlatformPlugin", ":intellijPlatformDiscover"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val modules = data().getValue("items").jsonArray.map { it.jsonObject }
        assertTrue(modules.isNotEmpty())
        assertTrue(modules.all { it.getValue("kind").jsonPrimitive.content == "module" })
        assertTrue(modules.any { it.getValue("aliases").jsonArray.isNotEmpty() })
        assertTrue(modules.all { it.containsKey("dependencies") })
        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=modules", "--query=there-is-no-such-module-9385")
        assertEquals("complete", report().getValue("status").jsonPrimitive.content)
        assertTrue(data().getValue("items").jsonArray.isEmpty())
    }
}
