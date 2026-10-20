// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class IntelliJPlatformTestMatrixTaskTest : IntelliJPlatformTestBase() {

    private fun fixture(plugin: String = "org.jetbrains.intellij.platform", extra: String = "") {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"test-matrix\"")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("$plugin") }
            $extra
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
            tasks.withType<Test>().configureEach {
                doFirst { error("The matrix must not execute tests") }
            }
        """.trimIndent())
    }

    private fun report(path: String = "build/reports/intellij-platform/testmatrix.json") =
        Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject

    private fun rows() = report().getValue("data").jsonObject.getValue("tests").jsonArray.map { it.jsonObject }.associateBy { it.getValue("taskPath").jsonPrimitive.content }

    @Test
    fun `custom tests report runners commands source sets and configured outputs without executing`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            val integration = sourceSets.create("integrationTest") {
                java.setSrcDirs(listOf("custom-integration/java"))
            }
            dependencies { add("integrationTestImplementation", "org.junit.jupiter:junit-jupiter:5.10.0") }
            tasks.register<Test>("integrationTest") {
                testClassesDirs = integration.output.classesDirs
                classpath = integration.runtimeClasspath
                useJUnitPlatform()
                reports.junitXml.outputLocation = layout.buildDirectory.dir("custom reports/xml")
                reports.html.required = false
                systemProperty("idea.log.path", file("custom reports/logs").absolutePath)
                systemProperty("unrelated.secret", "must-not-leak")
                javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(99) }
            }
            tasks.register<Test>("testNgSmoke") { useTestNG() }
            tasks.register<Test>("legacySmoke") { useJUnit() }
        """.trimIndent())
        val result = build(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, args = listOf("--offline"))
        assertEquals(listOf(":intellijPlatformTestMatrix"), result.tasks.map { it.path })
        val report = report()
        assertEquals("testMatrix", report.getValue("reportType").jsonPrimitive.content)
        assertEquals(1, report.getValue("schemaVersion").jsonPrimitive.int)
        val rows = rows()
        val integration = rows.getValue(":integrationTest")
        assertEquals("junitPlatform", integration.getValue("framework").jsonPrimitive.content)
        assertEquals("jvm", integration.getValue("kind").jsonPrimitive.content)
        assertEquals("./gradlew :integrationTest", integration.getValue("command").jsonPrimitive.content)
        assertEquals("./gradlew :integrationTest --tests '<class-or-method-pattern>'", integration.getValue("filteredCommand").jsonPrimitive.content)
        assertEquals(listOf("integrationTest"), integration.getValue("sourceSets").jsonArray.map { it.jsonPrimitive.content })
        assertEquals("convention", integration.getValue("sourceSetAssociation").jsonPrimitive.content)
        assertTrue(integration.getValue("frameworkDependencies").jsonArray.any { it.jsonObject.getValue("name").jsonPrimitive.content == "junit-jupiter" })
        val outputs = integration.getValue("outputs").jsonObject
        assertTrue(outputs.getValue("junitXml").jsonObject.getValue("value").jsonPrimitive.content.endsWith("custom reports/xml"))
        assertEquals("disabled", outputs.getValue("html").jsonObject.getValue("state").jsonPrimitive.content)
        assertTrue(outputs.getValue("log").jsonObject.getValue("value").jsonPrimitive.content.endsWith("custom reports/logs"))
        assertEquals("testng", rows.getValue(":testNgSmoke").getValue("framework").jsonPrimitive.content)
        assertEquals("junit4", rows.getValue(":legacySmoke").getValue("framework").jsonPrimitive.content)
        assertFalse(report.toString().contains("must-not-leak"))
        assertTrue(report.getValue("data").jsonObject.getValue("sourceSets").jsonArray.any { it.jsonObject.getValue("sourceDirectories").toString().contains("custom-integration/java") })
    }

    @Test
    fun `IDE UI and performance tasks report targets costs filtering and missing prerequisites`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            intellijPlatformTesting {
                testIde.register("againstOldIde") {
                    type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity
                    version = "2024.1.7"
                    sandboxDirectory = layout.buildDirectory.dir("explicit sandbox")
                    task { useJUnitPlatform() }
                }
                testIdeUi.register("extraUi") { task { enabled = false } }
                testIdePerformance.register("measureIde")
            }
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformTestMatrix"), tasks.map { it.path })
        }
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformTestMatrix"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val rows = rows()
        val custom = rows.getValue(":againstOldIde")
        assertEquals("2024.1.7", custom.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content)
        assertTrue(custom.getValue("sandbox").jsonObject.getValue("value").jsonPrimitive.content.endsWith("explicit sandbox"))
        assertTrue(custom.getValue("supportsTestFilter").jsonPrimitive.boolean)
        val ui = rows.getValue(":extraUi")
        assertEquals("junitPlatform", ui.getValue("framework").jsonPrimitive.content)
        assertTrue(ui.getValue("costs").jsonObject.getValue("startsIde").jsonPrimitive.boolean)
        assertTrue(ui.getValue("costs").jsonObject.getValue("requiresUi").jsonPrimitive.boolean)
        assertFalse(ui.getValue("enabled").jsonPrimitive.boolean)
        assertEquals("notConfigured", ui.getValue("outputs").jsonObject.getValue("screenshots").jsonObject.getValue("state").jsonPrimitive.content)
        val performance = rows.getValue(":measureIde")
        assertFalse(performance.getValue("supportsTestFilter").jsonPrimitive.boolean)
        assertEquals(JsonNull, performance.getValue("filteredCommand"))
        assertTrue(performance.getValue("prerequisites").jsonArray.any {
            it.jsonObject.getValue("id").jsonPrimitive.content == "testDataDirectory" && it.jsonObject.getValue("state").jsonPrimitive.content == "unavailable"
        })
        assertTrue(rows.values.all { it.getValue("executionStatus").jsonPrimitive.content == "untested" })
    }

    @Test
    fun `latest stays literal without a toolchain override and both matrices reuse configuration cache`() {
        fixture(extra = """
            dependencies {
                intellijPlatform {
                    intellijIdeaUltimate("latest") {
                        useInstaller = providers.provider { error("Installer options must not be evaluated") }
                        useCache = providers.provider { error("Cache options must not be evaluated") }
                        productMode = providers.provider { error("Product mode must not be evaluated") }
                    }
                }
            }
            tasks.withType<JavaCompile>().configureEach {
                javaCompiler = providers.provider { error("The compiler must not be provisioned") }
            }
        """.trimIndent())
        val properties = mapOf(
            GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to dir.resolve("missing-releases-{type}.json").toUri().toString(),
            "org.gradle.java.installations.auto-download" to "false",
        )
        val selected = setOf(":intellijPlatformTestMatrix", ":intellijPlatformCompatibilityMatrix")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline"), projectProperties = properties) {
            assertEquals(selected, tasks.map { it.path }.toSet())
        }
        val first = report().toString()
        val compatibilityPath = dir.resolve("build/reports/intellij-platform/compatibilitymatrix.json")
        val compatibility = compatibilityPath.readText()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline"), projectProperties = properties) {
            assertConfigurationCacheReused()
            assertEquals(selected, tasks.map { it.path }.toSet())
        }
        assertEquals(first, report().toString())
        assertEquals(compatibility, compatibilityPath.readText())
        val test = rows().getValue(":test")
        val ide = test.getValue("ide").jsonObject
        assertEquals("configured", ide.getValue("state").jsonPrimitive.content)
        assertEquals("IU", ide.getValue("productCode").jsonPrimitive.content)
        assertEquals("latest", ide.getValue("version").jsonPrimitive.content)
        val runtime = test.getValue("runtime").jsonObject
        assertEquals("notResolved", runtime.getValue("toolchainLanguageVersion").jsonObject.getValue("state").jsonPrimitive.content)
        assertEquals(JsonNull, runtime.getValue("toolchainLanguageVersion").jsonObject.getValue("value"))
        assertEquals("notResolved", runtime.getValue("executable").jsonObject.getValue("state").jsonPrimitive.content)
        val data = Json.parseToJsonElement(compatibility).jsonObject.getValue("data").jsonObject
        val target = data.getValue("configuredTargets").jsonArray.single { it.jsonObject.getValue("purpose").jsonPrimitive.content == "development" }.jsonObject
        assertEquals("latest", target.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content)
        assertEquals("unavailable", target.getValue("requirements").jsonObject.getValue("state").jsonPrimitive.content)
        assertEquals("untested", target.getValue("executionStatus").jsonPrimitive.content)
        assertEquals(JsonNull, data.getValue("projectLanguages").jsonObject.getValue("javaRelease"))
        assertTrue(data.getValue("verifiedSuccess").jsonArray.isEmpty())
    }

    @Test
    fun `lazy IDE identities and toolchain declarations stay unresolved`() {
        fixture(extra = """
            val evaluated = mutableListOf<String>()
            dependencies {
                intellijPlatform {
                    create {
                        type = providers.provider { evaluated += "type"; error("IDE type must not be evaluated") }
                        version = providers.provider { evaluated += "version"; error("IDE version must not be evaluated") }
                    }
                }
            }
            java.toolchain.languageVersion = providers.provider { error("Toolchain must not be evaluated") }
            gradle.taskGraph.whenReady { check(evaluated.isEmpty()) { "Lazy IDE providers were evaluated: " + evaluated } }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformTestMatrix"), tasks.map { it.path })
        }
        val test = rows().getValue(":test")
        val ide = test.getValue("ide").jsonObject
        assertEquals("notResolved", ide.getValue("state").jsonPrimitive.content)
        assertEquals(JsonNull, ide.getValue("productCode"))
        assertEquals(JsonNull, ide.getValue("version"))
        assertEquals("notResolved", test.getValue("runtime").jsonObject.getValue("toolchainLanguageVersion").jsonObject.getValue("state").jsonPrimitive.content)
        assertEquals("untested", test.getValue("executionStatus").jsonPrimitive.content)
    }

    @Test
    fun `late task declarations and configuration are captured outside task configuration actions`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            tasks.named("${Tasks.INTELLIJ_PLATFORM_TEST_MATRIX}").get()
            tasks.named("${Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX}").get()
            afterEvaluate {
                tasks.register<Test>("lateSmoke") { useTestNG() }
                intellijPlatformTesting.testIdeUi.register("lateUi") {
                    version = "2024.2.6"
                    task { reports.html.required = false }
                }
            }
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertEquals(setOf(":intellijPlatformTestMatrix", ":intellijPlatformCompatibilityMatrix"), tasks.map { it.path }.toSet())
        }
        val first = report().toString()
        val compatibilityPath = dir.resolve("build/reports/intellij-platform/compatibilitymatrix.json")
        val compatibility = compatibilityPath.readText()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(setOf(":intellijPlatformTestMatrix", ":intellijPlatformCompatibilityMatrix"), tasks.map { it.path }.toSet())
        }
        assertEquals(first, report().toString())
        assertEquals(compatibility, compatibilityPath.readText())
        val rows = rows()
        assertEquals("testng", rows.getValue(":lateSmoke").getValue("framework").jsonPrimitive.content)
        val ui = rows.getValue(":lateUi")
        assertEquals("junitPlatform", ui.getValue("framework").jsonPrimitive.content)
        assertEquals("2024.2.6", ui.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content)
        assertEquals("disabled", ui.getValue("outputs").jsonObject.getValue("html").jsonObject.getValue("state").jsonPrimitive.content)
        assertEquals(listOf("lateUi"), ui.getValue("sourceSets").jsonArray.map { it.jsonPrimitive.content })
        val targets = Json.parseToJsonElement(compatibility).jsonObject.getValue("data").jsonObject.getValue("configuredTargets").jsonArray
        assertTrue(targets.any { it.jsonObject.getValue("command").jsonPrimitive.contentOrNull == "./gradlew :lateUi" })
    }

    @Test
    fun `unselected matrices do not realize test tasks`() {
        fixture(extra = """
            tasks.register<Test>("mustRemainLazy") { error("An unselected matrix must not realize tests") }
        """.trimIndent())
        build("help", args = listOf("--offline")) {
            assertEquals(listOf(":help"), tasks.map { it.path })
        }
    }

    @Test
    fun `module with no IDE configuration has explicit unavailable states and cache reuse`() {
        fixture("org.jetbrains.intellij.platform.module")
        val path = "build/custom reports/matrix.json"
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, "--output=$path", args = listOf("--offline"))
        val first = dir.resolve(path).readText()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, "--output=$path", args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformTestMatrix"), tasks.map { it.path })
        }
        assertEquals(first, dir.resolve(path).readText())
        val report = report(path)
        assertTrue(report.getValue("data").jsonObject.getValue("module").jsonPrimitive.boolean)
        assertEquals("partial", report.getValue("status").jsonPrimitive.content)
        assertTrue(report.getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content.endsWith(".ide") })
    }

    @Test
    fun `qualified commands remain runnable from a multi project root`() {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"matrix-root\"; include(\":plugin\")")
        dir.resolve("build.gradle.kts").writeText("")
        dir.resolve("plugin").createDirectories().resolve("build.gradle.kts").writeText("""
            plugins { id("org.jetbrains.intellij.platform.module") }
            tasks.register<Test>("smoke") { useJUnitPlatform() }
            configurations.configureEach { incoming.beforeResolve { error("No resolution") } }
        """.trimIndent())
        build(":plugin:${Tasks.INTELLIJ_PLATFORM_TEST_MATRIX}", args = listOf("--offline"))
        val report = report("plugin/build/reports/intellij-platform/testmatrix.json")
        val smoke = report.getValue("data").jsonObject.getValue("tests").jsonArray.single { it.jsonObject.getValue("taskPath").jsonPrimitive.content == ":plugin:smoke" }.jsonObject
        assertEquals("./gradlew :plugin:smoke", smoke.getValue("command").jsonPrimitive.content)
    }
}