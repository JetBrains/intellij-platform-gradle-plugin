// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.*

class IntelliJPlatformCompatibilityMatrixTaskTest : IntelliJPlatformTestBase() {

    private fun fixture(plugin: String = "org.jetbrains.intellij.platform", extra: String = "") {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"compatibility-matrix\"")
        dir.resolve("build.gradle.kts").writeText("""
            plugins { id("$plugin") }
            $extra
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
        """.trimIndent())
    }

    private fun report(path: String = "build/reports/intellij-platform/compatibilitymatrix.json") =
        Json.parseToJsonElement(dir.resolve(path).readText()).jsonObject

    @Test
    fun `raw bounds configured targets and variants never imply verification success`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            intellijPlatform {
                pluginConfiguration.ideaVersion.sinceBuild = "261"
                nativeVariants {
                    enabled = true
                    linux.arm64.from(file("native/linux-arm64"))
                    mac.x86_64.from(providers.provider<String> { error("Native files must not be queried") })
                }
                pluginVerification.ides.create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2024.1.7")
            }
            dependencies { add("intellijPluginVerifierIdesDependency", "com.jetbrains.intellij.idea:ideaIC:2024.2.6") }
            intellijPlatformTesting.testIde.register("against20262") {
                type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea
                version = "2026.2"
            }
        """.trimIndent())
        dir.resolve("src/main/resources/META-INF").createDirectories().resolve("plugin.xml").writeText("""
            <idea-plugin><id>example.matrix</id><idea-version since-build="241" until-build="251.*"/></idea-plugin>
        """.trimIndent())
        dir.resolve("build/reports/pluginVerifier/IC-2024.2.6").createDirectories().resolve("verification-result.txt").writeText("OK")
        build(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformCompatibilityMatrix"), tasks.map { it.path })
        }
        val report = report()
        assertEquals("compatibilityMatrix", report.getValue("reportType").jsonPrimitive.content)
        val data = report.getValue("data").jsonObject
        val bounds = data.getValue("declaredBounds").jsonObject
        assertEquals("241", bounds.getValue("sinceBuild").jsonPrimitive.content)
        assertEquals("251.*", bounds.getValue("untilBuild").jsonPrimitive.content)
        assertEquals("rawDescriptor", bounds.getValue("provenance").jsonPrimitive.content)
        assertEquals("notResolved", bounds.getValue("effectiveBoundsState").jsonPrimitive.content)
        assertEquals("17", bounds.getValue("requirements").jsonObject.getValue("javaRuntime").jsonPrimitive.content)
        val targets = data.getValue("configuredTargets").jsonArray.map { it.jsonObject }
        val development = targets.single { it.getValue("purpose").jsonPrimitive.content == "development" }
        assertEquals("2025.1.6", development.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content)
        assertEquals("21", development.getValue("requirements").jsonObject.getValue("javaRuntime").jsonPrimitive.content)
        assertEquals("2.1.10", development.getValue("requirements").jsonObject.getValue("kotlinBundled").jsonPrimitive.content)
        assertEquals("2.0.0", development.getValue("requirements").jsonObject.getValue("kotlinCompilerMinimum").jsonPrimitive.content)
        val custom = targets.single { it.getValue("purpose").jsonPrimitive.content == "ide" }
        assertEquals("25", custom.getValue("requirements").jsonObject.getValue("javaRuntime").jsonPrimitive.content)
        assertEquals("./gradlew :against20262", custom.getValue("command").jsonPrimitive.content)
        val verifier = data.getValue("pluginVerifier").jsonObject
        assertEquals("configuredVerification", verifier.getValue("state").jsonPrimitive.content)
        assertEquals("notResolved", verifier.getValue("deferredDeclarationsState").jsonPrimitive.content)
        assertEquals("./gradlew :verifyPlugin", verifier.getValue("command").jsonPrimitive.content)
        assertTrue(verifier.getValue("targets").jsonArray.any { it.jsonObject.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content == "2024.2.6" })
        val explicit = verifier.getValue("targets").jsonArray.single { it.jsonObject.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content == "2024.1.7" }.jsonObject
        assertEquals("IC", explicit.getValue("ide").jsonObject.getValue("productCode").jsonPrimitive.content)
        assertEquals("declaredVerifier", explicit.getValue("ide").jsonObject.getValue("provenance").jsonPrimitive.content)
        assertEquals("configuredVerification", explicit.getValue("verificationState").jsonPrimitive.content)
        assertEquals("untested", explicit.getValue("executionStatus").jsonPrimitive.content)
        val native = data.getValue("nativeVariants").jsonObject
        assertTrue(native.getValue("enabled").jsonPrimitive.boolean)
        assertEquals("261", native.getValue("minimumSinceBuild").jsonPrimitive.content)
        assertEquals("notResolved", native.getValue("restrictionState").jsonPrimitive.content)
        assertEquals(6, native.getValue("variants").jsonArray.size)
        assertTrue(native.getValue("variants").jsonArray.any { it.jsonObject.getValue("literalPaths").toString().contains("native/linux-arm64") })
        assertTrue(native.getValue("variants").jsonArray.any { it.jsonObject.getValue("deferredSourceCount").jsonPrimitive.int == 1 })
        assertTrue(data.getValue("verifiedSuccess").jsonArray.isEmpty())
        assertTrue(targets.all { it.getValue("executionStatus").jsonPrimitive.content == "untested" })
        assertEquals("untested", verifier.getValue("executionStatus").jsonPrimitive.content)
    }

    @Test
    fun `materialized verifier dependencies do not realize deferred dependency lists`() {
        fixture(extra = """
            val deferredDependencies = objects.listProperty(org.gradle.api.artifacts.Dependency::class.java)
            deferredDependencies.addAll(providers.provider<List<org.gradle.api.artifacts.Dependency>> {
                error("Deferred verifier dependencies must not be evaluated")
            })
            configurations.named("intellijPluginVerifierIdesDependency") {
                dependencies.add(project.dependencies.create("idea:ideaIC:2024.1.7"))
                dependencies.addAllLater(deferredDependencies)
            }
        """.trimIndent())

        build(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformCompatibilityMatrix"), tasks.map { it.path })
        }
        val verifier = report().getValue("data").jsonObject.getValue("pluginVerifier").jsonObject
        val target = verifier.getValue("targets").jsonArray.single().jsonObject
        assertEquals("2024.1.7", target.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content)
        assertEquals("materializedVerifierDependency", target.getValue("ide").jsonObject.getValue("provenance").jsonPrimitive.content)
        assertEquals("untested", target.getValue("executionStatus").jsonPrimitive.content)
    }

    @Test
    fun `explicit verifier identities are visible while selectors and lazy providers stay unresolved`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            intellijPlatform.pluginVerification.ides {
                create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity, "2024.1.7") {
                    useInstaller = providers.provider { error("Verifier installer option must not be evaluated") }
                    useCache = providers.provider { error("Verifier cache option must not be evaluated") }
                    productMode = providers.provider { error("Verifier product mode must not be evaluated") }
                }
                create(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaUltimate, "latest")
                create {
                    type = providers.provider { error("Verifier type must not be evaluated") }
                    version = providers.provider { error("Verifier version must not be evaluated") }
                }
                create(providers.provider<List<String>> { error("Verifier lists must not be evaluated") })
                recommended()
                select { types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity) }
            }
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformCompatibilityMatrix"), tasks.map { it.path })
        }
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformCompatibilityMatrix"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val data = report().getValue("data").jsonObject
        val verifier = data.getValue("pluginVerifier").jsonObject
        assertEquals("configuredVerification", verifier.getValue("state").jsonPrimitive.content)
        assertEquals("notResolved", verifier.getValue("deferredDeclarationsState").jsonPrimitive.content)
        val targets = verifier.getValue("targets").jsonArray.map { it.jsonObject }
        assertEquals(setOf("2024.1.7", "latest"), targets.map { it.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content }.toSet())
        assertTrue(targets.all { it.getValue("verificationState").jsonPrimitive.content == "configuredVerification" })
        assertTrue(targets.all { it.getValue("executionStatus").jsonPrimitive.content == "untested" })
        val latest = targets.single { it.getValue("ide").jsonObject.getValue("version").jsonPrimitive.content == "latest" }
        assertEquals("unavailable", latest.getValue("requirements").jsonObject.getValue("state").jsonPrimitive.content)
        assertTrue(data.getValue("verifiedSuccess").jsonArray.isEmpty())
        assertTrue(report().getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "pluginVerifier.deferredDeclarations" })
    }

    @Test
    fun `lazy Java compiler options are not evaluated`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            java.toolchain.languageVersion = providers.provider { error("Toolchain must not be evaluated") }
            tasks.withType<JavaCompile>().configureEach {
                javaCompiler = providers.provider { error("Compiler must not be provisioned") }
                options.release = providers.provider { error("Release option must not be evaluated") }
            }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertEquals(listOf(":intellijPlatformCompatibilityMatrix"), tasks.map { it.path })
        }
        val languages = report().getValue("data").jsonObject.getValue("projectLanguages").jsonObject
        assertEquals(JsonNull, languages.getValue("javaRelease"))
        assertEquals(JsonNull, languages.getValue("javaSourceCompatibility"))
        assertEquals(JsonNull, languages.getValue("javaTargetCompatibility"))
    }

    @Test
    fun `Kotlin compiler defaults and lazy options do not acquire a toolchain`() {
        dir.resolve("settings.gradle.kts").writeText("rootProject.name = \"compatibility-kotlin\"")
        dir.resolve("build.gradle.kts").writeText("""
            plugins {
                id("org.jetbrains.intellij.platform")
                id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            }
            val evaluated = mutableListOf<String>()
            kotlin { jvmToolchain(999) }
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                compilerOptions.apiVersion = providers.provider { evaluated += "apiVersion"; error("Lazy Kotlin option must not be evaluated") }
                compilerOptions.languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0
            }
            configurations.configureEach {
                incoming.beforeResolve { error("Unexpected dependency resolution: " + name) }
            }
            gradle.taskGraph.whenReady { check(evaluated.isEmpty()) { "Lazy Kotlin providers were evaluated: " + evaluated } }
        """.trimIndent())
        val properties = mapOf("org.gradle.java.installations.auto-download" to "false")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline"), projectProperties = properties)
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline"), projectProperties = properties) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformCompatibilityMatrix"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        val languages = report().getValue("data").jsonObject.getValue("projectLanguages").jsonObject
        assertEquals(JsonNull, languages.getValue("jvmTarget"))
        assertEquals(JsonNull, languages.getValue("apiVersion"))
        assertEquals("2.0", languages.getValue("languageVersion").jsonPrimitive.content)
    }

    @Test
    fun `constant compiler values remain visible without provisioning a runtime`() {
        fixture(extra = """
            java.toolchain.languageVersion = JavaLanguageVersion.of(999)
            java.sourceCompatibility = JavaVersion.VERSION_17
            java.targetCompatibility = JavaVersion.VERSION_17
            tasks.withType<JavaCompile>().configureEach { options.release = 11 }
        """.trimIndent())
        build(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX, Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline"), projectProperties = mapOf("org.gradle.java.installations.auto-download" to "false")) {
            assertEquals(setOf(":intellijPlatformTestMatrix", ":intellijPlatformCompatibilityMatrix"), tasks.map { it.path }.toSet())
        }
        val languages = report().getValue("data").jsonObject.getValue("projectLanguages").jsonObject
        assertEquals("11", languages.getValue("javaRelease").jsonPrimitive.content)
        assertEquals("17", languages.getValue("javaSourceCompatibility").jsonPrimitive.content)
        assertEquals("17", languages.getValue("javaTargetCompatibility").jsonPrimitive.content)
        val testData = Json.parseToJsonElement(dir.resolve("build/reports/intellij-platform/testmatrix.json").readText()).jsonObject.getValue("data").jsonObject
        val runtime = testData.getValue("tests").jsonArray.first().jsonObject.getValue("runtime").jsonObject
        assertEquals("999", runtime.getValue("toolchainLanguageVersion").jsonObject.getValue("value").jsonPrimitive.content)
        assertEquals("notResolved", runtime.getValue("executable").jsonObject.getValue("state").jsonPrimitive.content)
    }

    @Test
    fun `offline metadata is deterministic and configuration cache is reused`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
            intellijPlatform.pluginVerification.ides.recommended()
        """.trimIndent())
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline"))
        val first = report().toString()
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertConfigurationCacheReused()
            assertEquals(listOf(":intellijPlatformCompatibilityMatrix"), tasks.map { it.path })
        }
        assertEquals(first, report().toString())
        assertTrue(report().getValue("unavailable").jsonArray.any { it.jsonObject.getValue("observation").jsonPrimitive.content == "pluginVerifier.deferredDeclarations" })
    }

    @Test
    fun `descriptor changes invalidate cached compatibility snapshots`() {
        fixture(extra = """
            dependencies { intellijPlatform { intellijIdeaUltimate("2025.1.6") } }
        """.trimIndent())
        val descriptor = dir.resolve("src/main/resources/META-INF").createDirectories().resolve("plugin.xml")
        descriptor.writeText("<idea-plugin><id>example.matrix</id><idea-version since-build=\"241\"/></idea-plugin>")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline"))
        assertEquals("241", report().getValue("data").jsonObject.getValue("declaredBounds").jsonObject.getValue("sinceBuild").jsonPrimitive.content)
        descriptor.writeText("<idea-plugin><id>example.matrix</id><idea-version since-build=\"242\"/></idea-plugin>")
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertFalse(output.contains("Reusing configuration cache."))
            assertEquals(listOf(":intellijPlatformCompatibilityMatrix"), tasks.map { it.path })
        }
        val bounds = report().getValue("data").jsonObject.getValue("declaredBounds").jsonObject
        assertEquals("242", bounds.getValue("sinceBuild").jsonPrimitive.content)
        assertEquals("21", bounds.getValue("requirements").jsonObject.getValue("javaRuntime").jsonPrimitive.content)
        buildWithConfigurationCache(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, args = listOf("--offline")) {
            assertConfigurationCacheReused()
        }
    }

    @Test
    fun `module and missing descriptor report unavailable bounds rather than invented ranges`() {
        fixture("org.jetbrains.intellij.platform.module")
        build(Tasks.INTELLIJ_PLATFORM_COMPATIBILITY_MATRIX, "--output=build/custom reports/compatibility.json", args = listOf("--offline"))
        val report = report("build/custom reports/compatibility.json")
        val data = report.getValue("data").jsonObject
        assertTrue(data.getValue("module").jsonPrimitive.boolean)
        assertEquals("notRequested", data.getValue("declaredBounds").jsonObject.getValue("state").jsonPrimitive.content)
        assertTrue(data.getValue("configuredTargets").jsonArray.isEmpty())
        assertTrue(data.getValue("verifiedSuccess").jsonArray.isEmpty())
        assertEquals("partial", report.getValue("status").jsonPrimitive.content)
    }

    @Test
    fun `version mappings distinguish build release and unknown selectors`() {
        assertEquals("17", matrixLanguageRequirements("IU-241.14473.1").javaRuntime)
        assertEquals("21", matrixLanguageRequirements("2024.2.6").javaRuntime)
        assertEquals("25", matrixLanguageRequirements("262.1").javaRuntime)
        assertEquals("2.4.0", matrixLanguageRequirements("2026.2").kotlinBundled)
        assertEquals("unavailable", matrixLanguageRequirements("latest").state)
        assertEquals("unavailable", matrixLanguageRequirements(null).state)
    }
}