// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import org.jetbrains.intellij.platform.gradle.variants
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
        val compiler = project.getValue("compilerSettings").jsonObject
        val java = compiler.getValue("java").jsonObject
        assertEquals("notResolved", java.getValue("state").jsonPrimitive.content)
        assertEquals("11", java.getValue("configured").jsonObject.getValue("release").jsonPrimitive.content)
        assertTrue(java.getValue("effective").jsonObject.values.all { it == JsonNull })
        assertEquals("notApplied", compiler.getValue("kotlin").jsonObject.getValue("state").jsonPrimitive.content)
        assertFalse(report.toString().contains("secret-do-not-report"))
    }

    @Test
    fun `declared remote platform never resolves and reuses configuration cache`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            java { toolchain.languageVersion = JavaLanguageVersion.of(999) }
            tasks.withType<JavaCompile>().configureEach {
                javaCompiler = providers.provider { error("Unexpected compiler toolchain provisioning") }
                options.release = providers.provider { error("Unexpected lazy compiler option evaluation") }
            }
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
        """.trimIndent())
        val properties = mapOf("org.gradle.java.installations.auto-download" to "false")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"), projectProperties = properties)
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"), projectProperties = properties) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformInfo"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val target = report().getValue("data").jsonObject.getValue("requestedIde").jsonObject
        assertEquals("2025.1.6", target.getValue("version").jsonPrimitive.content)
        val java = report().getValue("data").jsonObject.getValue("java").jsonObject
        assertEquals("999", java.getValue("toolchainLanguageVersion").jsonPrimitive.content)
        assertEquals(JsonNull, java.getValue("release"))
        assertEquals(JsonNull, java.getValue("sourceCompatibility"))
        assertEquals(JsonNull, java.getValue("targetCompatibility"))
    }

    @Test
    fun `latest selector stays declared offline and reuses configuration cache without release lookup`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("latest") } }
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
        """.trimIndent())
        val properties = mapOf(
            GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to dir.resolve("missing-releases-{type}.json").toUri().toString(),
            "org.gradle.java.installations.auto-download" to "false",
        )
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"), projectProperties = properties) {
            assertEquals(listOf(":intellijPlatformInfo"), tasks.map { it.path })
        }
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"), projectProperties = properties) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformInfo"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        assertEquals("complete", report().getValue("status").jsonPrimitive.content)
        val project = report().getValue("data").jsonObject
        val target = project.getValue("requestedIde").jsonObject
        assertEquals("IU", target.getValue("productCode").jsonPrimitive.content)
        assertEquals("latest", target.getValue("version").jsonPrimitive.content)
        assertEquals("requested", target.getValue("provenance").jsonPrimitive.content)
        assertEquals(JsonNull, target.getValue("root"))
        assertEquals(JsonNull, project.getValue("resolvedIde"))
        assertTrue(report().getValue("unavailable").jsonArray.any {
            it.jsonObject.getValue("observation").jsonPrimitive.content == "resolvedIde" &&
                it.jsonObject.getValue("state").jsonPrimitive.content == "notRequested"
        })
    }

    @Test
    fun `requested IDE metadata never reads lazy installer cache or product mode options`() {
        fixture(extra = """
            dependencies {
                intellijPlatform {
                    intellijIdeaUltimate("latest") {
                        useInstaller = providers.provider { error("Unexpected installer option evaluation") }
                        useCache = providers.provider { error("Unexpected cache option evaluation") }
                        productMode = providers.provider { error("Unexpected product mode evaluation") }
                    }
                }
            }
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformInfo"), tasks.map { it.path })
        }
        val target = report().getValue("data").jsonObject.getValue("requestedIde").jsonObject
        assertEquals("IU", target.getValue("productCode").jsonPrimitive.content)
        assertEquals("latest", target.getValue("version").jsonPrimitive.content)
    }

    @Test
    fun `lazy IDE identity is unavailable instead of evaluating declaration providers`() {
        fixture(extra = """
            dependencies {
                intellijPlatform {
                    create {
                        type = providers.provider { error("Unexpected IDE type evaluation") }
                        version = providers.provider { error("Unexpected IDE version evaluation") }
                    }
                }
            }
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"))
        assertEquals("partial", report().getValue("status").jsonPrimitive.content)
        assertEquals(JsonNull, report().getValue("data").jsonObject.getValue("requestedIde"))
        assertTrue(report().getValue("unavailable").jsonArray.any {
            it.jsonObject.getValue("observation").jsonPrimitive.content == "requestedIde" &&
                it.jsonObject.getValue("state").jsonPrimitive.content == "unavailable"
        })
    }

    @Test
    fun `capabilities discover custom tests UI workflows and native variants without realization or resolution`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            intellijPlatformTesting {
                testIde.register("customIdeTest")
                testIdeUi.register("customUiTest")
                runIde.register("customRunIde")
            }
            afterEvaluate {
                tasks.register<Test>("lateJvmTest") { error("A catalog must not realize unused test tasks") }
                tasks.register<org.jetbrains.intellij.platform.gradle.tasks.BuildPluginTask>("customDistribution") {
                    error("A catalog must not realize unused archive tasks")
                }
            }
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
        val project = report().getValue("data").jsonObject
        val capabilities = project.getValue("capabilities").jsonArray.map { it.jsonObject }
        val byTask = capabilities.associateBy { it.getValue("task").jsonPrimitive.content }
        assertEquals(byTask.keys.sorted(), project.getValue("commands").jsonArray.map { it.jsonPrimitive.content })
        val legacy = listOf(
            Tasks.INTELLIJ_PLATFORM_INFO, Tasks.INTELLIJ_PLATFORM_DOCTOR, Tasks.INTELLIJ_PLATFORM_DISCOVER, Tasks.INTELLIJ_PLATFORM_SDK,
            Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION, Tasks.VERIFY_PLUGIN_STRUCTURE, Tasks.VERIFY_PLUGIN,
            Tasks.BUILD_PLUGIN, Tasks.RUN_IDE, Tasks.External.TEST,
        )
        (legacy + listOf(Tasks.TEST_UI, "customIdeTest", "customUiTest", "customRunIde", "lateJvmTest", "customDistribution", Tasks.BUILD_PLUGIN_VARIANTS)).forEach {
            assertTrue(":$it" in byTask, "Missing capability for $it")
        }
        variants.forEach { variant ->
            val capability = byTask.getValue(":${Tasks.BUILD_PLUGIN_VARIANTS}_${variant.os}_${variant.arch}")
            assertTrue(capability.getValue("prerequisites").jsonArray.any { it.jsonPrimitive.content.contains("nativeVariants") })
        }
        fun costs(task: String) = byTask.getValue(":$task").getValue("executionCost").jsonArray.map { it.jsonPrimitive.content }
        assertEquals(listOf("metadata"), costs(Tasks.INTELLIJ_PLATFORM_INFO))
        assertEquals(listOf("resolution"), costs(Tasks.INTELLIJ_PLATFORM_SDK))
        assertEquals(listOf("resolution", "compile"), costs(Tasks.External.COMPILE_JAVA))
        assertEquals(listOf("resolution", "compile", "ide"), costs("customRunIde"))
        assertEquals(listOf("resolution", "compile", "test"), costs("lateJvmTest"))
        assertEquals(listOf("resolution", "compile", "ide", "test"), costs("customUiTest"))
        assertEquals(listOf("resolution", "compile", "ide", "publish"), costs(Tasks.PUBLISH_PLUGIN))
        assertEquals(listOf("resolution"), byTask.getValue(":${Tasks.INTELLIJ_PLATFORM_INFO}").getValue("optionalExecutionCost").jsonArray.map { it.jsonPrimitive.content })
        assertTrue(capabilities.all { it.getValue("purpose").jsonPrimitive.content.isNotBlank() })
    }

    @Test
    fun `subproject capabilities use qualified task paths`() {
        fixture()
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"report-test\"; include(\"child\")")
        dir.resolve("child").createDirectories().resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform.module") }
            tasks.register<Test>("nestedJvmTest") { error("Must not realize an unused test") }
        """.trimIndent())
        buildWithConfigurationCache(":child:${Tasks.INTELLIJ_PLATFORM_INFO}", args = listOf("--offline"))
        val first = report("child/build/reports/intellij-platform/info.json").toString()
        buildWithConfigurationCache(":child:${Tasks.INTELLIJ_PLATFORM_INFO}", args = listOf("--offline")) { assertConfigurationCacheReused() }
        val project = report("child/build/reports/intellij-platform/info.json").getValue("data").jsonObject
        assertEquals(first, report("child/build/reports/intellij-platform/info.json").toString())
        val commands = project.getValue("commands").jsonArray.map { it.jsonPrimitive.content }
        assertTrue(":child:nestedJvmTest" in commands)
        assertTrue(commands.all { it.startsWith(":child:") })
        assertFalse(":child:${Tasks.PUBLISH_PLUGIN}" in commands)
        assertFalse(":child:${Tasks.RUN_IDE}" in commands)
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
        val compiler = report().getValue("data").jsonObject.getValue("compilerSettings").jsonObject.getValue("kotlin").jsonObject
        assertEquals("17", compiler.getValue("configured").jsonObject.getValue("jvmTarget").jsonPrimitive.content)
        assertEquals("notResolved", compiler.getValue("state").jsonPrimitive.content)
        assertTrue(compiler.getValue("effective").jsonObject.values.all { it == JsonNull })
    }

    @Test
    fun `kotlin defaults do not acquire an unavailable toolchain`() {
        fixture()
        dir.resolve("build.gradle.kts").writeText("""
            plugins {
                id("org.jetbrains.intellij.platform")
                id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            }
            kotlin { jvmToolchain(999) }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions.apiVersion = providers.provider { error("Unexpected lazy Kotlin option evaluation") }
            }
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
        """.trimIndent())
        val properties = mapOf("org.gradle.java.installations.auto-download" to "false")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"), projectProperties = properties)
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_INFO, args = listOf("--offline"), projectProperties = properties) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformInfo"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val compiler = report().getValue("data").jsonObject.getValue("compilerSettings").jsonObject.getValue("kotlin").jsonObject
        assertEquals("notResolved", compiler.getValue("state").jsonPrimitive.content)
        assertEquals(JsonNull, compiler.getValue("configured").jsonObject.getValue("apiVersion"))
        assertTrue(compiler.getValue("effective").jsonObject.values.all { it == JsonNull })
    }
}
