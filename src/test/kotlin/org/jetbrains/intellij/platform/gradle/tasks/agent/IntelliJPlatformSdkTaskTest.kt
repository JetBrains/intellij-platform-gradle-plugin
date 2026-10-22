// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import java.util.jar.JarOutputStream
import kotlin.io.path.*
import kotlin.test.*

class IntelliJPlatformSdkTaskTest : IntelliJPluginTestBase() {

    override val enableIntelliJPlatformCache = true

    private fun report(path: String = "build/reports/intellij-platform/sdk.json") = Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject
    private fun data() = report().getValue("data").jsonObject

    @Test
    fun `resolved SDK paths exist ownership is precise and external artifacts are distinct`() {
        val external = dir.resolve("external-library.jar")
        JarOutputStream(external.outputStream()).use { }
        dir.resolve("build.gradle.kts").appendText("\n" + """
            dependencies {
                implementation(files("${external.invariantSeparatorsPathString}"))
                intellijPlatform { bundledPlugin("com.intellij.java") }
            }
        """.trimIndent())
        val result = build(Tasks.INTELLIJ_PLATFORM_SDK)
        assertEquals(listOf(":initializeIntellijPlatformPlugin", ":intellijPlatformSdk"), result.tasks.map { it.path })
        assertEquals("IU", data().getValue("targetIde").jsonObject.getValue("productCode").jsonPrimitive.content)
        assertEquals(":${Tasks.INTELLIJ_PLATFORM_SOURCES}", data().getValue("sourcesTask").jsonPrimitive.content)
        val root = Path(data().getValue("root").jsonPrimitive.content)
        assertTrue(root.isDirectory())
        val owners = data().getValue("owners").jsonArray.map { it.jsonObject }
        assertTrue(owners.any { it.getValue("id").jsonPrimitive.content == "com.intellij.java" })
        owners.flatMap { it.getValue("archives").jsonArray }.map { Path(it.jsonPrimitive.content) }.forEach { assertFalse(it.isAbsolute) }
        val classpath = data().getValue("compileClasspath").jsonArray.map { it.jsonObject }
        val artifact = classpath.single { it.getValue("path").jsonPrimitive.content == external.invariantSeparatorsPathString }
        assertEquals("project", artifact.getValue("kind").jsonPrimitive.content)
        assertTrue(artifact.getValue("owners").jsonArray.isEmpty())
        val sdkArtifacts = classpath.filter { it.getValue("kind").jsonPrimitive.content == "sdk" }
        assertTrue(sdkArtifacts.isNotEmpty())
        sdkArtifacts.forEach { assertTrue(root.resolve(it.getValue("path").jsonPrimitive.content).exists()) }
        assertTrue(sdkArtifacts.any { it.getValue("owners").jsonArray.isNotEmpty() })
        data().getValue("sources").jsonArray.forEach { assertTrue(root.resolve(it.jsonPrimitive.content).isRegularFile()) }
    }

    @Test
    fun `owner presentation filtering preserves complete classpath ownership`() {
        dir.resolve("build.gradle.kts").appendText("\ndependencies { intellijPlatform { bundledPlugin(\"com.intellij.java\") } }")
        build(Tasks.INTELLIJ_PLATFORM_SDK)
        val classpath = data().getValue("compileClasspath").jsonArray
        val selectedOwners = data().getValue("owners").jsonArray.map { it.jsonObject }
            .filter { it.getValue("id").jsonPrimitive.content == "com.intellij.java" || it.getValue("aliases").jsonArray.any { alias -> alias.jsonPrimitive.content == "com.intellij.java" } }
            .map { it.getValue("ref").jsonPrimitive.content }
        val otherOwners = classpath.flatMap { it.jsonObject.getValue("owners").jsonArray }
            .map { it.jsonPrimitive.content }.filterNot { it in selectedOwners }
        assertTrue(otherOwners.isNotEmpty(), "The regression needs classpath ownership outside the selected plugin.")

        build(Tasks.INTELLIJ_PLATFORM_SDK, "--owner=com.intellij.java", args = listOf("--offline"))
        val owners = data().getValue("owners").jsonArray.map { it.jsonObject }
        assertTrue(owners.isNotEmpty())
        assertTrue(owners.all { it.getValue("id").jsonPrimitive.content == "com.intellij.java" || it.getValue("aliases").jsonArray.any { alias -> alias.jsonPrimitive.content == "com.intellij.java" } })
        assertEquals(classpath, data().getValue("compileClasspath").jsonArray)

        build(Tasks.INTELLIJ_PLATFORM_SDK, "--owner=there-is-no-owner-9385", args = listOf("--offline"))
        assertTrue(data().getValue("owners").jsonArray.isEmpty())
        assertEquals(classpath, data().getValue("compileClasspath").jsonArray)
    }

    @Test
    fun `owner filtering local SDKs and configuration cache execute no unrelated tasks`() {
        build(Tasks.INTELLIJ_PLATFORM_SDK)
        val root = data().getValue("root").jsonPrimitive.content
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform.module") }
            repositories { intellijPlatform { defaultRepositories() } }
            dependencies { intellijPlatform { local("$root") } }
        """.trimIndent())
        // Warm local Ivy descriptors before testing configuration-cache reuse.
        build(Tasks.INTELLIJ_PLATFORM_SDK, "--owner=com.intellij.java", args = listOf("--offline"))
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SDK, "--owner=com.intellij.java", args = listOf("--offline"))
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_SDK, "--owner=com.intellij.java", args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformSdk"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val owners = data().getValue("owners").jsonArray.map { it.jsonObject }
        assertTrue(owners.isNotEmpty())
        assertTrue(owners.all { it.getValue("id").jsonPrimitive.content == "com.intellij.java" || it.getValue("aliases").jsonArray.any { alias -> alias.jsonPrimitive.content == "com.intellij.java" } })
        assertEquals(Path(root), Path(data().getValue("root").jsonPrimitive.content))
        build(Tasks.INTELLIJ_PLATFORM_SDK, "--owner=there-is-no-owner-9385", "--output=build/custom reports/sdk.json", args = listOf("--offline"))
        assertTrue(report("build/custom reports/sdk.json").getValue("data").jsonObject.getValue("owners").jsonArray.isEmpty())

        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"sdk-map\"; include(\"child\")")
        dir.resolve("child").createDirectories().resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform.module") }
            repositories { intellijPlatform { localPlatformArtifacts() } }
            dependencies { intellijPlatform { local("$root") } }
        """.trimIndent())
        buildWithConfigurationCache(":child:${Tasks.INTELLIJ_PLATFORM_SDK}", args = listOf("--offline")) {
            assertEquals(listOf(":child:${Tasks.INTELLIJ_PLATFORM_SDK}"), tasks.map { it.path })
        }
        val child = report("child/build/reports/intellij-platform/sdk.json").getValue("data").jsonObject
        val command = child.getValue("sourcesTask").jsonPrimitive.content
        assertEquals(":child:${Tasks.INTELLIJ_PLATFORM_SOURCES}", command)
        assertEquals(data().getValue("sources"), child.getValue("sources"))
        buildWithConfigurationCache(command, "--owner=there-is-no-owner-9385", "--output=build/custom reports/sources.json", args = listOf("--offline")) {
            assertEquals(listOf(command), tasks.map { it.path })
        }
        val sources = report("child/build/custom reports/sources.json")
        assertEquals(":child", sources.getValue("projectPath").jsonPrimitive.content)
        assertEquals("sources", sources.getValue("reportType").jsonPrimitive.content)
        assertEquals("complete", sources.getValue("status").jsonPrimitive.content)
        assertTrue(sources.getValue("data").jsonObject.getValue("binaries").jsonArray.isEmpty())
    }
}
