// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import kotlin.io.path.*
import kotlin.test.*

class IntelliJPlatformDoctorTaskTest : IntelliJPlatformTestBase() {

    private fun fixture(module: Boolean = false) {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"doctor-test\"")
        val root = dir.resolve("sdk").createDirectories()
        root.resolve("product-info.json").writeText("""{"name":"IntelliJ IDEA","productCode":"IU","version":"2025.1.6","buildNumber":"251.28774.11"}""")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform${if (module) ".module" else ""}") }
            repositories { intellijPlatform { defaultRepositories() } }
            java { toolchain.languageVersion = JavaLanguageVersion.of(21) }
            tasks.withType<JavaCompile>().configureEach { options.release = 21 }
            dependencies { intellijPlatform { local("${root.invariantSeparatorsPathString}") } }
        """.trimIndent())
    }

    private fun report() = Json.parseToJsonElement(dir.resolve("build/reports/intellij-platform/doctor.json").readText()).jsonObject

    private fun issues() = report().getValue("issues").jsonArray.map { it.jsonObject }

    @Test
    fun `findings have stable identities and suggested fixes without compiling or acquiring a runtime`() {
        fixture()
        val result = build(Tasks.INTELLIJ_PLATFORM_DOCTOR, args = listOf("--offline"))
        assertEquals(listOf(":initializeIntellijPlatformPlugin", ":patchPluginXml", ":intellijPlatformDoctor"), result.tasks.map { it.path })
        assertTrue(issues().any { it.getValue("id").jsonPrimitive.content == "missing-plugin-descriptor" })
        assertTrue(issues().all { it.getValue("suggestedFix").jsonPrimitive.content.isNotBlank() && it.getValue("setting").jsonPrimitive.content.isNotBlank() })
        assertTrue(report().getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "runtimeVendor" })
        assertEquals(1, report().getValue("schemaVersion").jsonPrimitive.int)
        val missing = issues().single { it.getValue("id").jsonPrimitive.content == "missing-plugin-descriptor" }
        assertEquals("absent", missing.getValue("observedValue").jsonPrimitive.content)
        assertEquals("pluginXml", missing.getValue("configurationLocation").jsonPrimitive.content)
        assertEquals("blockingConfigurationError", missing.getValue("category").jsonPrimitive.content)
        assertEquals("warning", missing.getValue("severity").jsonPrimitive.content)
        assertEquals("./gradlew :${Tasks.INTELLIJ_PLATFORM_DOCTOR}", missing.getValue("verificationCommand").jsonPrimitive.content)
        listOf("evidence", "provenance", "applicableIdeVersions").forEach { key ->
            assertTrue(missing.getValue(key).jsonArray.isNotEmpty(), key)
        }
    }

    @Test
    fun `fail-on writes the report first and respects muted messages`() {
        fixture()
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DOCTOR, "--fail-on=warning", args = listOf("--offline"))
        assertTrue(issues().isNotEmpty())
        build(Tasks.INTELLIJ_PLATFORM_DOCTOR, "--fail-on=warning", args = listOf("--offline"), projectProperties = mapOf(GradleProperties.VerifyPluginProjectConfigurationMutedMessages.toString() to "plugin.xml"))
        assertTrue(issues().all { it.getValue("suppressed").jsonPrimitive.boolean })
        assertTrue(issues().all { it.getValue("evidence").jsonArray.isNotEmpty() })
        build(Tasks.INTELLIJ_PLATFORM_DOCTOR, "--fail-on=error", args = listOf("--offline"))
    }

    @Test
    fun `module projects skip descriptor checks and reuse the configuration cache`() {
        fixture(module = true)
        // The local dependency resolver creates its Ivy descriptor on first resolution.
        build(Tasks.INTELLIJ_PLATFORM_DOCTOR, args = listOf("--offline"))
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DOCTOR, args = listOf("--offline"))
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DOCTOR, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformDoctor"), tasks.map { it.path })
        }
        assertEquals("complete", report().getValue("status").jsonPrimitive.content)
        assertTrue(issues().isEmpty())
        assertTrue(report().getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "pluginXml" })
    }

    @Test
    fun `effective descriptor values are diagnosed`() {
        fixture()
        val xml = dir.resolve("src/main/resources/META-INF/plugin.xml")
        xml.parent.createDirectories()
        xml.writeText("""<idea-plugin><id>test.plugin</id><name>Test</name><idea-version since-build="251"/></idea-plugin>""")
        dir.resolve("build.gradle.kts").appendText("\n" + """
            intellijPlatform { pluginConfiguration { ideaVersion { sinceBuild = "241.*" } } }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_DOCTOR, args = listOf("--offline"))
        val ids = issues().map { it.getValue("id").jsonPrimitive.content }
        assertTrue(ids.containsAll(listOf("since-build-wildcard", "since-build-below-platform", "java-target-exceeds-since-build")), ids.toString())
        val sinceBuild = issues().single { it.getValue("id").jsonPrimitive.content == "since-build-below-platform" }
        assertEquals("241.*", sinceBuild.getValue("observedValue").jsonPrimitive.content)
        assertEquals(">= 251", sinceBuild.getValue("expectedValue").jsonPrimitive.content)
        assertEquals("intellijPlatform.pluginConfiguration.ideaVersion (effective plugin.xml)", sinceBuild.getValue("configurationLocation").jsonPrimitive.content)
        assertTrue(sinceBuild.getValue("provenance").jsonArray.any { "plugin.xml" in it.jsonPrimitive.content })
        assertTrue(sinceBuild.getValue("evidence").jsonArray.any { "since-build='241.*'" in it.jsonPrimitive.content })
        val target = issues().single { it.getValue("id").jsonPrimitive.content == "java-target-exceeds-since-build" }
        assertEquals("21", target.getValue("observedValue").jsonPrimitive.content)
        assertEquals("<= 17", target.getValue("expectedValue").jsonPrimitive.content)
        assertTrue(target.getValue("configurationLocation").jsonPrimitive.content.endsWith("build.gradle.kts: targetCompatibility"))
    }

    @Test
    fun `effective Java release evidence needs neither compilation nor a runtime`() {
        fixture(module = true)
        dir.resolve("build.gradle.kts").appendText("\ntasks.withType<JavaCompile>().configureEach { options.release = 17 }\n")
        val result = build(Tasks.INTELLIJ_PLATFORM_DOCTOR, args = listOf("--offline"))
        assertEquals(listOf(":intellijPlatformDoctor"), result.tasks.map { it.path })
        val source = issues().single()
        assertEquals("java-source-below-platform", source.getValue("id").jsonPrimitive.content)
        assertEquals("17", source.getValue("observedValue").jsonPrimitive.content)
        assertEquals(">= 21", source.getValue("expectedValue").jsonPrimitive.content)
        assertEquals("incompatibility", source.getValue("category").jsonPrimitive.content)
        assertTrue(source.getValue("evidence").jsonArray.any { "251.28774.11" in it.jsonPrimitive.content })
        assertTrue(source.getValue("provenance").jsonArray.any { "Gradle" in it.jsonPrimitive.content })
    }
}
