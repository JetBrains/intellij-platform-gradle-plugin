// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY_PRODUCT_RELEASES
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import kotlin.io.path.*
import kotlin.test.*

class IntelliJPlatformDiscoverReleasesTaskTest : IntelliJPlatformTestBase() {

    private fun fixture(extra: String = "", plugin: String = "org.jetbrains.intellij.platform") {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"discover-releases-test\"")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("$plugin") }
            $extra
            configurations.configureEach {
                incoming.beforeResolve { error("Discovery must not resolve SDK artifacts: " + name) }
            }
        """.trimIndent())

        val listings = dir.resolve("listings").createDirectories()
        IntelliJPlatformType.entries.distinctBy { it.code }.forEach { type ->
            listings.resolve("${type.code}.json").writeText(when (type.code) {
                "IU" -> """[{"code":"IIU","releases":[
                    {"type":"release","version":"2025.1","build":"251.100"},
                    {"type":"release","version":"2025.2","build":"252.100"},
                    {"type":"eap","version":"2025.3","build":"253.100"},
                    {"type":"release","version":"2025.1.1","build":"251.200"},
                    {"type":"beta","version":"2025.2","build":"252.90"}
                ]}]""".trimIndent()
                "PS" -> """[{"code":"PS","releases":[
                    {"type":"release","version":"2025.1","build":"251.150"},
                    {"type":"rc","version":"2025.2","build":"252.50"}
                ]}]""".trimIndent()
                else -> """[{"code":"${type.code}","releases":[]}]"""
            })
        }
        listings.resolve("android.json").writeText("""{"content":{"version":1,"item":[]}}""")
        dir.resolve("gradle.properties").writeText("""
            ${GradleProperties.ProductsReleasesCdnBuildsUrl}=${listings.resolve("IU.json").toUri().toString().replace("IU.json", "{type}.json")}
            ${GradleProperties.ProductsReleasesAndroidStudioUrl}=${listings.resolve("android.json").toUri()}
        """.trimIndent())
    }

    private fun report(path: String = "build/reports/intellij-platform/discover.json") =
        Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject

    private fun data() = report().getValue("data").jsonObject

    private fun releases() = data().getValue("releases").jsonArray.map { it.jsonObject }

    private fun notations() = releases().map { it.getValue("notation").jsonPrimitive.content }

    private fun assertOnlyDiscovery(tasks: List<String>) =
        assertEquals(listOf(":${Tasks.INTELLIJ_PLATFORM_DISCOVER}"), tasks - ":${Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN}")

    @Test
    fun `explicit release filters need no SDK and execute no unrelated tasks`() {
        fixture()
        val result = build(
            Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=iu,ps", "--channel=release,rc",
            "--since-build=251", "--until-build=252.*",
        )
        assertOnlyDiscovery(result.tasks.map { it.path })
        assertEquals("complete", report().getValue("status").jsonPrimitive.content)
        assertEquals(1, report().getValue("schemaVersion").jsonPrimitive.int)
        assertEquals("discover", report().getValue("reportType").jsonPrimitive.content)
        assertEquals(listOf("IU-2025.2", "IU-2025.1.1", "IU-2025.1", "PS-252.50", "PS-2025.1"), notations())
        assertEquals(setOf("RELEASE", "RC"), releases().map { it.getValue("channel").jsonPrimitive.content }.toSet())
        assertEquals("252.100", releases().first().getValue("buildNumber").jsonPrimitive.content)
        assertEquals(JsonNull, data().getValue("targetIde"))
        assertTrue(report().getValue("unavailable").jsonArray.any {
            it.jsonObject.getValue("observation").jsonPrimitive.content == "targetIde" &&
                it.jsonObject.getValue("state").jsonPrimitive.content == "notRequested"
        })
        assertTrue(data().getValue("items").jsonArray.isEmpty())
    }

    @Test
    fun `release defaults do not consult a declared SDK or plugin compatibility bounds`() {
        fixture(extra = """
            repositories { intellijPlatform { defaultRepositories() } }
            dependencies { intellijPlatform { intellijIdea("2026.1") } }
            intellijPlatform { pluginConfiguration { ideaVersion { sinceBuild = "999"; untilBuild = "999.*" } } }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases") {
            assertOnlyDiscovery(tasks.map { it.path })
        }
        assertEquals(7, releases().size)
        assertEquals(setOf("IU", "PS"), releases().map { it.getValue("productCode").jsonPrimitive.content }.toSet())
        assertEquals(setOf("RELEASE", "EAP", "BETA", "RC"), releases().map { it.getValue("channel").jsonPrimitive.content }.toSet())
    }

    @Test
    fun `query is case insensitive and empty matches are successful`() {
        fixture()
        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU", "--channel=RELEASE", "--query=iU-2025.1")
        assertEquals(listOf("IU-2025.1.1", "IU-2025.1"), notations())

        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU", "--query=not-a-release")
        assertEquals("partial", report().getValue("status").jsonPrimitive.content)
        assertTrue(releases().isEmpty())
    }

    @Test
    fun `release discovery is byte deterministic and configuration cache safe without SDK resolution`() {
        fixture(extra = """
            repositories { intellijPlatform { defaultRepositories() } }
            dependencies { intellijPlatform { intellijIdea("2026.1") } }
        """.trimIndent())
        val options = arrayOf(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU,PS")
        buildWithConfigurationCache(*options)
        val first = dir.resolve("build/reports/intellij-platform/discover.json").readText()
        buildWithConfigurationCache(*options) {
            assertConfigurationCacheReused()
            assertOnlyDiscovery(tasks.map { it.path })
        }
        assertEquals(first, dir.resolve("build/reports/intellij-platform/discover.json").readText())
    }

    @Test
    fun `cached listings support offline discovery and configuration cache reuse`() {
        fixture()
        val options = arrayOf(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU", "--channel=RELEASE")
        build(*options)
        val first = report()
        val cache = dir.resolve(CACHE_DIRECTORY).resolve(CACHE_DIRECTORY_PRODUCT_RELEASES)
        assertEquals(1, cache.listDirectoryEntries("*.json").size)
        dir.resolve("listings/IU.json").deleteExisting()

        buildWithConfigurationCache(*options, args = listOf("--offline"))
        buildWithConfigurationCache(*options, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertOnlyDiscovery(tasks.map { it.path })
        }
        assertEquals(first, report())
    }

    @Test
    fun `offline cache miss is reported as unavailable rather than a successful empty result`() {
        fixture()
        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU", args = listOf("--offline"))
        assertEquals("partial", report().getValue("status").jsonPrimitive.content)
        assertTrue(report().getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "releases" })
        assertTrue(releases().isEmpty())
    }

    @Test
    fun `a valid empty listing is successful including offline`() {
        fixture()
        dir.resolve("listings/IU.json").writeText("""[{"code":"IIU","releases":[]}]""")
        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU")
        val first = report()
        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU", args = listOf("--offline"))
        assertEquals(first, report())
        assertTrue(releases().isEmpty())
    }

    @Test
    fun `operational errors are sanitized and replace an earlier successful report`() {
        fixture()
        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU")
        val secret = "secret-do-not-report"
        dir.resolve("listings/PS.json").writeText("not-json-$secret")
        buildAndFail(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=PS")
        assertEquals("failed", report().getValue("status").jsonPrimitive.content)
        assertFalse(report().toString().contains(secret))
        assertTrue(releases().isEmpty())
    }

    @Test
    fun `existing JetBrains and Android fixtures expose product and platform release fields`() {
        fixture()
        dir.resolve("listings/IU.json").writeText(checkNotNull(javaClass.classLoader.getResource("products-releases/jetbrains-product-releases-IU.json")).readText())
        dir.resolve("listings/android.json").writeText(checkNotNull(javaClass.classLoader.getResource("products-releases/android-studio-releases-list.json")).readText())
        build(
            Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU,AI", "--channel=RELEASE",
            "--since-build=223", "--until-build=233.*",
        )
        assertTrue("IU-2023.3.8" in notations())
        val android = releases().single { it.getValue("notation").jsonPrimitive.content == "AI-2023.2.1.23" }
        assertEquals("AI", android.getValue("productCode").jsonPrimitive.content)
        assertEquals("AI-232.10227.8.2321.11479570", android.getValue("buildNumber").jsonPrimitive.content)
        assertEquals("2023.2.5", android.getValue("platformVersion").jsonPrimitive.content)
        assertEquals("232.10227.8", android.getValue("platformBuildNumber").jsonPrimitive.content)
    }

    @Test
    fun `module projects support custom report paths with spaces`() {
        fixture(plugin = "org.jetbrains.intellij.platform.module")
        val path = "build/custom reports/releases.json"
        build(Tasks.INTELLIJ_PLATFORM_DISCOVER, "--kind=releases", "--type=IU", "--output=$path") {
            assertOnlyDiscovery(tasks.map { it.path })
        }
        assertEquals("complete", report(path).getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `invalid kind and release filter options fail`() {
        fixture()
        listOf("--kind=UNKNOWN", "--type=UNKNOWN", "--channel=UNKNOWN").forEach { option ->
            val result = buildAndFail(Tasks.INTELLIJ_PLATFORM_DISCOVER, option)
            assertTrue(result.output.contains("UNKNOWN"), result.output)
        }
    }
}
