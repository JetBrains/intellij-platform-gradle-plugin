// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText
import kotlin.test.*

class IntelliJPlatformDistributionTaskTest : IntelliJPlatformTestBase() {

    private fun fixture(plugin: String = "org.jetbrains.intellij.platform", extra: String = "") {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"report-test\"")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("$plugin") }
            version = "1.2.3"
            $extra
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected resolution: " + name) }
            }
        """.trimIndent())
    }

    private fun resource(name: String, content: String) = dir.resolve("src/main/resources/$name").apply {
        parent.createDirectories()
        writeText(content)
    }

    private fun report(path: String = "build/reports/intellij-platform/distribution.json") =
        Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject

    private fun data(path: String = "build/reports/intellij-platform/distribution.json") = report(path).getValue("data").jsonObject

    private fun zip(entries: Map<String, ByteArray>): ByteArray = ByteArrayOutputStream().use { bytes ->
        ZipOutputStream(bytes).use { output ->
            entries.toSortedMap().forEach { (path, content) ->
                output.putNextEntry(ZipEntry(path).apply { time = 0L })
                output.write(content)
                output.closeEntry()
            }
        }
        bytes.toByteArray()
    }

    private fun archive(path: String = "existing distribution.zip", pluginXml: String = archivedDescriptor) {
        val jar = zip(mapOf(
            DISTRIBUTION_PLUGIN_XML to pluginXml.toByteArray(),
            "META-INF/optional.xml" to "<idea-plugin><depends>optional.runtime</depends></idea-plugin>".toByteArray(),
            "META-INF/content.module.xml" to "<idea-plugin><dependencies><module name=\"intellij.platform.core\"/></dependencies></idea-plugin>".toByteArray(),
        ))
        dir.resolve(path).apply {
            parent.createDirectories()
            writeBytes(zip(mapOf(
                "report-test/lib/report-test-1.2.3.jar" to jar,
                "report-test/lib/local.jar" to zip(emptyMap()),
                "report-test/bin/native.txt" to "native".toByteArray(),
            )))
        }
    }

    @Test
    fun `default metadata is declared and remains offline without resolving or building`() {
        fixture(extra = """
            intellijPlatform { pluginConfiguration { id = "configured.id"; name = "Configured name" } }
            dependencies {
                intellijPlatform { intellijIdeaUltimate("2026.1") }
                compileOnly("example:compile-api:1")
                implementation("example:runtime:2")
                runtimeOnly(files("libs/local.jar"))
            }
        """.trimIndent())
        resource(DISTRIBUTION_PLUGIN_XML, """
            <idea-plugin>
              <id>raw.id</id><name>Raw name</name><version>raw-version</version>
              <idea-version since-build="253" until-build="253.*"/>
              <depends optional="true" config-file="optional.xml">optional.plugin</depends>
              <content><module name="content.module" loading="required"/></content>
            </idea-plugin>
        """.trimIndent())
        resource("META-INF/optional.xml", "<idea-plugin><depends>optional.runtime</depends></idea-plugin>")
        resource("META-INF/content.module.xml", "<idea-plugin><dependencies><module name=\"intellij.platform.core\"/></dependencies></idea-plugin>")
        val result = build(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, args = listOf("--offline"), projectProperties = mapOf("publishToken" to "do-not-report"))
        assertEquals(listOf(":intellijPlatformDistribution"), result.tasks.map { it.path })
        assertEquals(1, report().getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("distribution", report().getValue("reportType").jsonPrimitive.content)
        assertEquals("partial", report().getValue("status").jsonPrimitive.content)
        val data = data()
        assertEquals("configured.id", data.getValue("configuration").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(JsonNull, data.getValue("configuration").jsonObject.getValue("sinceBuild"))
        assertEquals(JsonNull, data.getValue("effectiveDescriptor"))
        assertEquals(JsonNull, data.getValue("archive"))
        val descriptors = data.getValue("declaredDescriptors").jsonArray.map { it.jsonObject }
        assertEquals(setOf("main", "module", "optional"), descriptors.map { it.getValue("kind").jsonPrimitive.content }.toSet())
        assertTrue(descriptors.all { it.getValue("provenance").jsonPrimitive.content == "declared" })
        assertEquals("raw.id", descriptors.single { it.getValue("kind").jsonPrimitive.content == "main" }.getValue("metadata").jsonObject.getValue("id").jsonPrimitive.content)
        val dependencies = data.getValue("plan").jsonObject.getValue("dependencies").jsonArray.map { it.jsonObject }
        assertEquals("compileOnly", dependencies.single { it.getValue("name").jsonPrimitive.content == "compile-api" }.getValue("category").jsonPrimitive.content)
        assertEquals("runtimeCandidate", dependencies.single { it.getValue("name").jsonPrimitive.content == "runtime" }.getValue("category").jsonPrimitive.content)
        assertFalse(report().toString().contains("do-not-report"))
        assertFalse(dir.resolve("build/libs").exists())
        assertFalse(dir.resolve("build/distributions").exists())
    }

    @Test
    fun `default report is deterministic and configuration cache is reused`() {
        fixture(extra = "dependencies { intellijPlatform { intellijIdeaUltimate(\"2026.1\") } }")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, args = listOf("--offline"))
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformDistribution"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        assertTrue(report().getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "effectiveDescriptor" })
    }

    @Test
    fun `module project reports missing metadata and no distribution without inventing paths`() {
        fixture("org.jetbrains.intellij.platform.module")
        resource("META-INF/feature.module.xml", "<idea-plugin><dependencies><module name=\"intellij.platform.core\"/></dependencies></idea-plugin>")
        val output = "build/custom reports/distribution.json"
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, "--output=$output", args = listOf("--offline"))
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, "--output=$output", args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformDistribution"), tasks.map { it.path })
        }
        assertTrue(data(output).getValue("module").jsonPrimitive.boolean)
        assertEquals(JsonNull, data(output).getValue("plan").jsonObject.getValue("archivePath"))
        assertEquals(JsonNull, data(output).getValue("effectiveDescriptor"))
        val module = data(output).getValue("declaredDescriptors").jsonArray.map { it.jsonObject }.single { it.getValue("kind").jsonPrimitive.content == "module" }
        assertEquals("present", module.getValue("state").jsonPrimitive.content)
        assertTrue(module.getValue("path").jsonPrimitive.content.endsWith("/META-INF/feature.module.xml"))
    }

    @Test
    fun `separate and composed plugin module declarations have distinct packaging roles`() {
        fixture(extra = """
            dependencies {
                intellijPlatform {
                    pluginModule(project(":feature"))
                    pluginComposedModule(project(":shared"))
                }
            }
        """.trimIndent())
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"report-test\"; include(\"feature\", \"shared\")")
        listOf("feature", "shared").forEach { name ->
            dir.resolve(name).createDirectories().resolve("build.gradle.kts").writeText("plugins { id(\"org.jetbrains.intellij.platform.module\") }")
        }
        build(":${Tasks.INTELLIJ_PLATFORM_DISTRIBUTION}", args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformDistribution"), tasks.map { it.path })
        }
        val dependencies = data().getValue("plan").jsonObject.getValue("dependencies").jsonArray.map { it.jsonObject }
        val separate = dependencies.single { it.getValue("category").jsonPrimitive.content == "pluginModule" }
        assertEquals(":feature", separate.getValue("projectPath").jsonPrimitive.content)
        assertEquals("report-test/lib/modules", separate.getValue("destinationDirectory").jsonPrimitive.content)
        assertEquals("separateModuleJar", separate.getValue("packagingRole").jsonPrimitive.content)
        val composed = dependencies.single { it.getValue("category").jsonPrimitive.content == "composedModule" }
        assertEquals(":shared", composed.getValue("projectPath").jsonPrimitive.content)
        assertEquals("mergedIntoComposedJar", composed.getValue("packagingRole").jsonPrimitive.content)
    }

    @Test
    fun `existing custom patched descriptor is observed but patching and compatibility providers never run`() {
        fixture(extra = """
            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PatchPluginXmlTask>("patchPluginXml") {
                outputFile = layout.projectDirectory.file("existing patched/plugin.xml")
                sinceBuild = providers.provider { error("Compatibility provider must not run") }
                doFirst { error("Patch task must not run") }
            }
        """.trimIndent())
        resource(DISTRIBUTION_PLUGIN_XML, "<idea-plugin><id>raw.id</id></idea-plugin>")
        dir.resolve("existing patched").createDirectories().resolve("plugin.xml").writeText(archivedDescriptor)
        build(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformDistribution"), tasks.map { it.path })
        }
        val effective = data().getValue("effectiveDescriptor").jsonObject
        assertEquals("verified", effective.getValue("provenance").jsonPrimitive.content)
        assertEquals("archived.id", effective.getValue("metadata").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("261", effective.getValue("metadata").jsonObject.getValue("sinceBuild").jsonPrimitive.content)
        assertTrue(effective.getValue("dependencies").jsonArray.any { it.jsonObject.getValue("configFile").jsonPrimitive.contentOrNull == "optional.xml" })
        assertTrue(report().getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "effectiveDescriptor.freshness" })
    }

    @Test
    fun `native variants report literal overlays but do not evaluate lazy sources or build variants`() {
        fixture(extra = """
            intellijPlatform {
                nativeVariants {
                    enabled = true
                    linux.arm64.from(file("native/linux-arm64"))
                    mac.arm64.from(providers.provider { error("Lazy overlay must not run") })
                }
            }
        """.trimIndent())
        dir.resolve("native/linux-arm64/bin").createDirectories().resolve("native.txt").writeText("native")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, args = listOf("--offline"))
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformDistribution"), tasks.map { it.path })
        }
        val plan = data().getValue("plan").jsonObject
        assertTrue(plan.getValue("nativeVariantsEnabled").jsonPrimitive.boolean)
        val variants = plan.getValue("nativeVariants").jsonArray.map { it.jsonObject }
        assertEquals(6, variants.size)
        val linux = variants.single { it.getValue("operatingSystem").jsonPrimitive.content == "linux" && it.getValue("architecture").jsonPrimitive.content == "arm64" }
        assertEquals("1.2.3-linux-arm64", linux.getValue("version").jsonPrimitive.content)
        assertEquals("report-test/bin/native.txt", linux.getValue("overlays").jsonArray.single().jsonObject.getValue("destination").jsonPrimitive.content)
        assertTrue(linux.getValue("requiredModules").jsonArray.any { it.jsonPrimitive.content == "com.intellij.modules.arch.arm64" })
        assertTrue(variants.single { it.getValue("operatingSystem").jsonPrimitive.content == "mac" && it.getValue("architecture").jsonPrimitive.content == "arm64" }.getValue("overlaySourcesUnavailable").jsonPrimitive.boolean)
    }

    @Test
    fun `existing archive inspects optional module dependencies and paths without building and reuses cache`() {
        fixture(extra = "dependencies { runtimeOnly(files(\"libs/local.jar\")) }")
        archive()
        val options = arrayOf(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, "--archive=existing distribution.zip")
        buildWithConfigurationCache(*options, args = listOf("--offline"))
        val first = report().toString()
        buildWithConfigurationCache(*options, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformDistribution"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val archive = data().getValue("archive").jsonObject
        assertEquals("verified", archive.getValue("provenance").jsonPrimitive.content)
        val descriptors = archive.getValue("descriptors").jsonArray.map { it.jsonObject }
        val main = descriptors.single { it.getValue("kind").jsonPrimitive.content == "main" }
        assertEquals("archived.id", main.getValue("metadata").jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals("261", main.getValue("metadata").jsonObject.getValue("sinceBuild").jsonPrimitive.content)
        assertEquals("261.*", main.getValue("metadata").jsonObject.getValue("untilBuild").jsonPrimitive.content)
        assertEquals(setOf("legacy", "module", "plugin"), main.getValue("dependencies").jsonArray.map { it.jsonObject.getValue("kind").jsonPrimitive.content }.toSet())
        assertEquals(setOf("main", "module", "optional"), descriptors.map { it.getValue("kind").jsonPrimitive.content }.toSet())
        assertTrue(descriptors.all { it.getValue("state").jsonPrimitive.content == "present" })
        assertTrue(archive.getValue("entries").jsonArray.any { it.jsonObject.getValue("path").jsonPrimitive.content == "report-test/lib/report-test-1.2.3.jar!META-INF/optional.xml" })
        assertTrue(archive.getValue("comparison").jsonObject.getValue("matched").jsonArray.any { it.jsonPrimitive.content == "report-test/lib/report-test-1.2.3.jar" })
        assertTrue(archive.getValue("comparison").jsonObject.getValue("unplanned").jsonArray.any { it.jsonPrimitive.content == "report-test/bin/native.txt" })
    }

    @Test
    fun `inspect reads configured archive path without a build dependency`() {
        fixture(extra = """
            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask>("buildPlugin") {
                destinationDirectory = layout.projectDirectory.dir("existing archives")
                archiveFileName = "custom.zip"
                doFirst { error("Archive producer must not run") }
            }
        """.trimIndent())
        archive("existing archives/custom.zip")
        build(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, "--inspect", args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformDistribution"), tasks.map { it.path })
        }
        assertTrue(data().getValue("archive").jsonObject.getValue("path").jsonPrimitive.content.endsWith("/existing archives/custom.zip"))
    }

    @Test
    fun `missing explicitly requested archive writes failed report and fails normally`() {
        fixture()
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, "--archive=missing.zip", args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformDistribution"), tasks.map { it.path })
        }
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertEquals("distribution.archive.failed", report().getValue("issues").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
        assertEquals(JsonNull, data().getValue("archive"))
    }

    @Test
    fun `malformed archive writes failed report and fails normally`() {
        fixture()
        dir.resolve("broken.zip").writeText("not a zip")
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, "--archive=broken.zip", args = listOf("--offline"))
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertEquals("distribution.archive.failed", report().getValue("issues").jsonArray.single().jsonObject.getValue("id").jsonPrimitive.content)
    }

    @Test
    fun `malformed archived plugin descriptor fails after writing report`() {
        fixture()
        archive(pluginXml = "<idea-plugin>")
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DISTRIBUTION, "--archive=existing distribution.zip", args = listOf("--offline"))
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertEquals(JsonNull, data().getValue("archive"))
    }

    private val archivedDescriptor = """
        <idea-plugin>
          <id>archived.id</id><name>Archived plugin</name><version>9.0</version>
          <idea-version since-build="261" until-build="261.*"/>
          <depends optional="true" config-file="optional.xml">optional.plugin</depends>
          <dependencies><plugin id="com.intellij.java"/><module name="intellij.platform.core"/></dependencies>
          <content><module name="content.module" loading="required"/></content>
        </idea-plugin>
    """.trimIndent()
}