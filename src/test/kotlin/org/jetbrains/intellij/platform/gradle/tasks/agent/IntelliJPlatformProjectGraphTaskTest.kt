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

class IntelliJPlatformProjectGraphTaskTest : IntelliJPlatformTestBase() {

    private fun fixture() {
        dir.resolve("settings.gradle.kts").writeText("""
            rootProject.name = "graph-test"
            include("shared", "api-lib", "module", "composed", "test-support")
            includeBuild("conventions build") { name = "conventions" }
        """.trimIndent())
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform") }
            dependencies {
                intellijPlatform {
                    intellijIdeaUltimate("2025.1.6")
                    pluginModule(project(":module"))
                    pluginComposedModule(project(":composed"))
                }
                implementation(project(":shared"))
                testImplementation(project(":test-support"))
            }
            sourceSets.named("main") { java.srcDir("custom sources") }
            allprojects {
                configurations.configureEach {
                    incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
                }
            }
        """.trimIndent())
        dir.resolve("shared").createDirectories().resolve("build.gradle").writeText("""
            plugins { id 'java-library' }
            dependencies { api project(':api-lib') }
        """.trimIndent())
        listOf("api-lib", "test-support").forEach {
            dir.resolve(it).createDirectories().resolve("build.gradle.kts").writeText("plugins { `java-library` }")
        }
        listOf("module", "composed").forEach {
            dir.resolve(it).createDirectories().resolve("build.gradle.kts").writeText("plugins { id(\"org.jetbrains.intellij.platform.module\") }")
        }
        dir.resolve("conventions build").createDirectories().run {
            resolve("settings.gradle.kts").writeText("rootProject.name = \"conventions\"")
            resolve("build.gradle.kts").writeText("")
        }
    }

    private fun report(path: String = "build/reports/intellij-platform/projectgraph.json") =
        Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject

    @Test
    fun `multi-project graph observes declarations offline without resolving and reuses configuration cache`() {
        fixture()
        buildWithConfigurationCache(":${Tasks.INTELLIJ_PLATFORM_PROJECT_GRAPH}", args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformProjectGraph"), tasks.map { it.path })
        }
        val first = report()
        buildWithConfigurationCache(":${Tasks.INTELLIJ_PLATFORM_PROJECT_GRAPH}", args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformProjectGraph"), tasks.map { it.path })
        }
        assertEquals(first, report())
        assertEquals(1, first.getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("projectGraph", first.getValue("reportType").jsonPrimitive.content)
        assertEquals(":", first.getValue("projectPath").jsonPrimitive.content)
        val graph = first.getValue("data").jsonObject
        val projects = graph.getValue("projects").jsonArray.map { it.jsonObject }
        assertEquals(listOf(":", ":api-lib", ":composed", ":module", ":shared", ":test-support"), projects.map { it.getValue("path").jsonPrimitive.content })
        val root = projects.first()
        assertContains(root.getValue("roles").jsonArray.map { it.jsonPrimitive.content }, "distribution")
        assertTrue(root.getValue("plugins").jsonArray.any { it.jsonPrimitive.content.endsWith("IntelliJPlatformPlugin") })
        val main = root.getValue("sourceSets").jsonArray.single { it.jsonObject.getValue("name").jsonPrimitive.content == "main" }.jsonObject
        assertContains(main.getValue("sourceRoots").jsonArray.map { it.jsonPrimitive.content }, dir.toRealPath().resolve("custom sources").invariantSeparatorsPathString)
        assertContains(main.getValue("resourceRoots").jsonArray.map { it.jsonPrimitive.content }, dir.toRealPath().resolve("src/main/resources").invariantSeparatorsPathString)
        assertEquals(dir.toRealPath().resolve("shared/build.gradle").invariantSeparatorsPathString, projects.single { it.getValue("path").jsonPrimitive.content == ":shared" }.getValue("buildFile").jsonPrimitive.content)
        listOf(":shared", ":api-lib", ":module", ":composed").forEach { path ->
            assertContains(projects.single { it.getValue("path").jsonPrimitive.content == path }.getValue("roles").jsonArray.map { it.jsonPrimitive.content }, "shared")
        }
        assertContains(projects.single { it.getValue("path").jsonPrimitive.content == ":test-support" }.getValue("roles").jsonArray.map { it.jsonPrimitive.content }, "test")
        val edges = graph.getValue("edges").jsonArray.map { it.jsonObject }
        listOf(Triple(":", ":shared", "implementation"), Triple(":shared", ":api-lib", "api"), Triple(":", ":module", "pluginModule"), Triple(":", ":composed", "pluginComposedModule")).forEach { (from, to, kind) ->
            assertTrue(edges.any { it.getValue("from").jsonPrimitive.content == from && it.getValue("to").jsonPrimitive.content == to && it.getValue("kind").jsonPrimitive.content == kind })
        }
        assertEquals("conventions", graph.getValue("includedBuilds").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals("includedBuildCandidate", graph.getValue("buildLogic").jsonArray.single().jsonObject.getValue("kind").jsonPrimitive.content)
        assertTrue(first.getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "pluginOrigins" })
    }

    @Test
    fun `module invocation still reports the whole build and supports output paths with spaces`() {
        fixture()
        val output = "build/custom reports/project graph.json"
        buildWithConfigurationCache(":module:${Tasks.INTELLIJ_PLATFORM_PROJECT_GRAPH}", "--output=$output", args = listOf("--offline")) {
            assertEquals(listOf(":module:intellijPlatformProjectGraph"), tasks.map { it.path })
        }
        buildWithConfigurationCache(":module:${Tasks.INTELLIJ_PLATFORM_PROJECT_GRAPH}", "--output=$output", args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":module:intellijPlatformProjectGraph"), tasks.map { it.path })
        }
        val report = report("module/$output")
        assertEquals(":module", report.getValue("projectPath").jsonPrimitive.content)
        assertEquals(6, report.getValue("data").jsonObject.getValue("projects").jsonArray.size)
    }
}