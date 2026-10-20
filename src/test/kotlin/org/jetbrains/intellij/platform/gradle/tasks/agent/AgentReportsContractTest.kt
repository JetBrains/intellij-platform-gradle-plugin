// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
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

        val properties = mapOf("publishToken" to "secret-not-for-reports")
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
        if (platformVersion != null) {
            val info = Json.parseToJsonElement(first.getValue(Tasks.INTELLIJ_PLATFORM_INFO)).jsonObject.getValue("data").jsonObject
            assertEquals(platformVersion, info.getValue("requestedIde").jsonObject.getValue("version").jsonPrimitive.content)
        }

        buildWithConfigurationCache(*metadataTasks.toTypedArray(), args = listOf("--offline"), projectProperties = properties) {
            assertConfigurationCacheReused()
        }
        assertEquals(first, reports())
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