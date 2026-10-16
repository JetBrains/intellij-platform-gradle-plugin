// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class IntelliJPlatformInfoTaskTest : IntelliJPlatformTestBase() {

    private fun fixture(plugin: String = "org.jetbrains.intellij.platform", extra: String = "") {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"report-test\"")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("$plugin") }
            group = "example"
            version = "1.2.3"
            repositories { intellijPlatform { defaultRepositories() } }
            java { toolchain.languageVersion = JavaLanguageVersion.of(17) }
            tasks.withType<JavaCompile>().configureEach { options.release = 11 }
            $extra
        """.trimIndent())
    }

    private fun report(path: String = "build/reports/intellij-platform/info.json") =
        Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject

    @Test
    fun `missing platform is explicit and no unrelated tasks run`() {
        fixture()
        val result = build(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"), projectProperties = mapOf("publishToken" to "secret-do-not-report"))
        assertEquals(listOf(":intellijPlatformInfo"), result.tasks.map { it.path })
        val report = report()
        assertEquals(1, report.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("partial", report.getValue("status").jsonPrimitive.content)
        assertTrue(report.getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "requestedIde" })
        val project = report.getValue("data").jsonObject
        assertEquals("report-test", project.getValue("name").jsonPrimitive.content)
        assertEquals("11", project.getValue("java").jsonObject.getValue("release").jsonPrimitive.content)
        assertFalse(report.toString().contains("secret-do-not-report"))
    }

    @Test
    fun `declared remote platform never resolves and reuses configuration cache`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"))
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformInfo"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val target = report().getValue("data").jsonObject.getValue("requestedIde").jsonObject
        assertEquals("2025.1.6", target.getValue("version").jsonPrimitive.content)
    }

    @Test
    fun `module projects support custom paths with spaces`() {
        fixture("org.jetbrains.intellij.platform.module")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, "--output=build/custom reports/context.json", args = listOf("--offline"))
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, "--output=build/custom reports/context.json", args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformInfo"), tasks.map { it.path })
        }
        val project = report("build/custom reports/context.json").getValue("data").jsonObject
        assertTrue(project.getValue("module").jsonPrimitive.boolean)
    }

    @Test
    fun `requested resolution failure writes a failed report`() {
        fixture()
        buildAndFail(Tasks.INTELLIJ_PLATFORM_INFO, "--resolve", args = listOf("--offline"))
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertEquals("resolution.failed", report().getValue("issues").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `local SDK resolution reports actual product and build`() {
        val sdk = dir.resolve("local-sdk").createDirectories()
        sdk.resolve("product-info.json").writeText("""{"name":"IntelliJ IDEA","productCode":"IU","version":"2025.1.6","buildNumber":"251.28774.11"}""")
        fixture(extra = """dependencies { intellijPlatform { local("${sdk.invariantSeparatorsPathString}") } }""")
        // The local dependency resolver creates its Ivy descriptor on first resolution.
        build(Tasks.INTELLIJ_PLATFORM_INFO, "--resolve", args = listOf("--offline"))
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, "--resolve", args = listOf("--offline"))
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, "--resolve", args = listOf("--offline")) { assertConfigurationCacheReused() }
        val target = report().getValue("data").jsonObject.getValue("resolvedIde").jsonObject
        assertEquals("IU", target.getValue("productCode").jsonPrimitive.content)
        assertEquals("251.28774.11", target.getValue("buildNumber").jsonPrimitive.content)
        assertEquals(sdk.invariantSeparatorsPathString, target.getValue("root").jsonPrimitive.content)
    }

    @Test
    fun `kotlin settings are observed without compiling`() {
        fixture(extra = "")
        dir.resolve("build.gradle.kts").writeText("""
            plugins {
                id("org.jetbrains.intellij.platform")
                id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            }
            kotlin { jvmToolchain(17) }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
            }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_INFO)
        val settings = report().getValue("data").jsonObject.getValue("kotlin").jsonObject
        assertEquals("17", settings.getValue("jvmTarget").jsonPrimitive.content)
    }
}
