// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AgentReportsContractTest : IntelliJPlatformTestBase() {

    private val metadataTasks = listOf(
        Tasks.INTELLIJ_PLATFORM_INFO,
        Tasks.INTELLIJ_PLATFORM_PROJECT_GRAPH,
        Tasks.INTELLIJ_PLATFORM_DISTRIBUTION,
        Tasks.INTELLIJ_PLATFORM_DEPENDENCY_GRAPH,
        Tasks.INTELLIJ_PLATFORM_TEST_MATRIX,
        Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX,
    )

    private fun reports() = metadataTasks.associateWith { task ->
        dir.resolve("build/reports/intellij-platform/${task.removePrefix("intellijPlatform").lowercase()}.json").readText()
    }

    private fun checkTaskGroups(plugin: String) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"report-groups\"")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("$plugin") }
            java { toolchain.languageVersion = JavaLanguageVersion.of(17) }
            configurations.configureEach {
                incoming.beforeResolve { error("Task listing unexpectedly resolves " + name) }
            }
            """.trimIndent(),
        )

        val groups = mapOf(
            "intellij platform reports" to metadataTasks + listOf(
                Tasks.INTELLIJ_PLATFORM_DOCTOR,
                Tasks.INTELLIJ_PLATFORM_DISCOVER,
                Tasks.INTELLIJ_PLATFORM_SDK,
                Tasks.INTELLIJ_PLATFORM_SOURCES,
            ),
            "intellij platform info" to listOf(
                Tasks.PRINT_BUNDLED_MODULES,
                Tasks.PRINT_BUNDLED_PLUGINS,
                Tasks.PRINT_PRODUCTS_RELEASES,
            ),
        )
        groups.forEach { (group, expectedTasks) ->
            buildWithConfigurationCache("tasks", args = listOf("--group=$group", "--offline", "--console=plain")) {
                val listedTasks = Regex("^(\\w+) - .+$", RegexOption.MULTILINE).findAll(output)
                    .map { it.groupValues[1] }.toSet()
                assertEquals(expectedTasks.toSet(), listedTasks)
                assertEquals(listOf(":tasks"), tasks.map { it.path })
            }
        }
        assertFalse(dir.resolve("build/reports/intellij-platform").exists())
        assertFalse(dir.resolve(".intellijPlatform/sources").exists())
    }

    private fun checkContract(plugin: String, platformVersion: String? = null) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"metadata-contract\"")
        dir.resolve("build.gradle.kts").writeText(
            """
            plugins { id("$plugin") }
            ${platformVersion?.let { "dependencies { intellijPlatform { create(\"IU\", \"$it\") } }" }
                ?: "java { toolchain.languageVersion = JavaLanguageVersion.of(17) }"}
            configurations.configureEach {
                incoming.beforeResolve { error("Metadata report unexpectedly resolves " + name) }
            }
            """.trimIndent(),
        )

        val sourceCache = dir.resolve("unpopulated cache with spaces")
        val properties = mapOf(
            "publishToken" to "secret-not-for-reports",
            "org.jetbrains.intellij.platform.intellijPlatformCache" to sourceCache.toString(),
        )
        buildWithConfigurationCache(*metadataTasks.toTypedArray(), args = listOf("--offline"), projectProperties = properties) {
            assertEquals(metadataTasks.map { ":$it" }.toSet(), tasks.map { it.path }.toSet())
        }
        val first = reports()
        first.values.forEach { text ->
            val report = Json.parseToJsonElement(text).jsonObject
            assertEquals(1, report.getValue("schemaVersion").jsonPrimitive.int)
            assertEquals(":", report.getValue("projectPath").jsonPrimitive.content)
            assertTrue(report.getValue("reportType").jsonPrimitive.content.isNotBlank())
            assertNotEquals("failed", report.getValue("status").jsonPrimitive.content)
            assertTrue(report.getValue("data") is JsonObject)
            assertTrue(report.getValue("issues") is JsonArray)
            assertTrue(report.getValue("unavailable") is JsonArray)
            assertFalse(text.contains("secret-not-for-reports"))
        }
        val context = Json.parseToJsonElement(first.getValue(Tasks.INTELLIJ_PLATFORM_INFO)).jsonObject.getValue("data").jsonObject
        val sourcesTask = ":${Tasks.INTELLIJ_PLATFORM_SOURCES}"
        assertTrue(context.getValue("commands").jsonArray.any { it.jsonPrimitive.content == sourcesTask })
        val sourcesCapability = context.getValue("capabilities").jsonArray.map { it.jsonObject }
            .single { it.getValue("task").jsonPrimitive.content == sourcesTask }
        assertEquals(listOf("resolution"), sourcesCapability.getValue("executionCost").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(sourcesCapability.getValue("prerequisites").jsonArray.isNotEmpty())
        assertFalse(sourceCache.resolve("sources").exists())
        if (platformVersion != null) {
            val info = Json.parseToJsonElement(first.getValue(Tasks.INTELLIJ_PLATFORM_INFO)).jsonObject.getValue("data").jsonObject
            assertEquals(platformVersion, info.getValue("requestedIde").jsonObject.getValue("version").jsonPrimitive.content)
        }

        buildWithConfigurationCache(*metadataTasks.toTypedArray(), args = listOf("--offline"), projectProperties = properties) {
            assertConfigurationCacheReused()
        }
        assertEquals(first, reports())
        assertFalse(sourceCache.resolve("sources").exists())
    }

    @Test
    fun `plugin report tasks have a separate discoverable group`() {
        checkTaskGroups("org.jetbrains.intellij.platform")
    }

    @Test
    fun `module report tasks have a separate discoverable group`() {
        checkTaskGroups("org.jetbrains.intellij.platform.module")
    }

    @Test
    fun `plugin metadata reports preserve the common offline configuration cache contract`() {
        checkContract("org.jetbrains.intellij.platform")
    }

    @Test
    fun `module metadata reports preserve the common offline configuration cache contract`() {
        checkContract("org.jetbrains.intellij.platform.module")
    }

    @Test
    fun `metadata reports preserve latest without resolving IDE derived compiler conventions`() {
        checkContract("org.jetbrains.intellij.platform", "latest")
    }
}