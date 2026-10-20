// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.json.*
import org.gradle.api.JavaVersion
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import kotlin.test.*

class ProjectConfigurationDiagnosticsTest {

    private val valid = ProjectConfigurationDiagnosticInput(
        platformVersion = "2025.1.6",
        platformBuild = "251.28774.11".toVersion(),
        platformJavaVersion = JavaVersion.VERSION_21,
        sourceCompatibility = JavaVersion.VERSION_21,
        targetCompatibility = JavaVersion.VERSION_21,
        kotlinJvmTarget = JavaVersion.VERSION_21,
        kotlinApiVersion = "2.1".toVersion(),
        kotlinLanguageVersion = "2.1".toVersion(),
        kotlinVersion = "2.1.10".toVersion(),
        kotlinPluginAvailable = true,
        kotlinStdlibDefaultDependency = false,
        descriptor = DiagnosticPluginDescriptor("251", null, "build/tmp/patchPluginXml/plugin.xml"),
        cacheIgnored = true,
        buildFile = "build.gradle.kts",
    )

    private fun diagnostics(input: ProjectConfigurationDiagnosticInput) =
        evaluateProjectConfiguration(input).associateBy { it.issue.id }

    @Test
    fun `matching effective Java and Kotlin versions do not produce findings`() {
        assertTrue(evaluateProjectConfiguration(valid).isEmpty())
    }

    @Test
    fun `shared findings cover descriptor compiler dependencies and cache in verifier order`() {
        val findings = evaluateProjectConfiguration(valid.copy(
            sourceCompatibility = JavaVersion.VERSION_17,
            targetCompatibility = JavaVersion.VERSION_25,
            kotlinJvmTarget = JavaVersion.VERSION_25,
            kotlinApiVersion = "2.2".toVersion(),
            kotlinLanguageVersion = "1.9".toVersion(),
            kotlinVersion = "1.9.24".toVersion(),
            kotlinStdlibDefaultDependency = null,
            kotlinxCoroutinesLibraryPresent = true,
            descriptor = DiagnosticPluginDescriptor("243.*", "243.*", "build/tmp/patchPluginXml/plugin.xml"),
            cacheIgnored = false,
        ))
        assertEquals(listOf(
            "since-build-wildcard",
            "since-build-below-platform",
            "until-build-restricts-compatibility",
            "java-target-exceeds-since-build",
            "kotlin-jvm-target-exceeds-since-build",
            "kotlin-api-exceeds-since-build",
            "cache-not-ignored",
            "kotlin-version-too-old",
            "java-source-below-platform",
            "kotlin-language-below-platform",
            "java-target-exceeds-platform",
            "kotlin-jvm-target-exceeds-platform",
            "kotlin-stdlib-dependency",
            "kotlin-coroutines-dependency",
        ), findings.map { it.issue.id })
        findings.forEach { diagnostic ->
            val issue = diagnostic.issue
            assertEquals("warning", issue.severity)
            assertFalse(issue.suppressed)
            assertFalse(issue.observedValue.isNullOrBlank(), issue.id)
            assertFalse(issue.expectedValue.isNullOrBlank(), issue.id)
            assertFalse(issue.configurationLocation.isNullOrBlank(), issue.id)
            assertTrue(issue.evidence.isNotEmpty(), issue.id)
            assertTrue(issue.provenance.isNotEmpty(), issue.id)
            assertTrue(issue.applicableIdeVersions.isNotEmpty(), issue.id)
            assertEquals("./gradlew :${Tasks.INTELLIJ_PLATFORM_DOCTOR}", issue.verificationCommand)
            assertTrue(issue.category in listOf("warning", "incompatibility", "blockingConfigurationError"), issue.id)
            assertEquals("${diagnostic.label} ${diagnostic.details} ${diagnostic.solution}", diagnostic.verifierMessage)
        }
    }

    @Test
    fun `Java findings carry observed limits platform and honest configuration provenance`() {
        val findings = diagnostics(valid.copy(targetCompatibility = JavaVersion.VERSION_25))
        val platform = findings.getValue("java-target-exceeds-platform").issue
        assertEquals("25", platform.observedValue)
        assertEquals("<= 21", platform.expectedValue)
        assertEquals("build.gradle.kts: targetCompatibility", platform.configurationLocation)
        assertContains(platform.evidence, "IntelliJ Platform 2025.1.6 (build 251.28774.11)")
        assertContains(platform.evidence, "Target platform supports Java 21")
        assertEquals("incompatibility", platform.category)
        val descriptor = findings.getValue("java-target-exceeds-since-build").issue
        assertTrue(descriptor.provenance.any { "build/tmp/patchPluginXml/plugin.xml" in it })
        assertContains(descriptor.applicableIdeVersions, "since-build 251")
    }

    @Test
    fun `subproject commands and unknown build file locations are retained without inventing paths`() {
        val findings = diagnostics(valid.copy(
            targetCompatibility = JavaVersion.VERSION_25,
            buildFile = null,
            verificationCommand = "./gradlew :plugin:${Tasks.INTELLIJ_PLATFORM_DOCTOR}",
        ))
        val issue = findings.getValue("java-target-exceeds-platform").issue
        assertEquals("targetCompatibility", issue.configurationLocation)
        assertEquals("./gradlew :plugin:${Tasks.INTELLIJ_PLATFORM_DOCTOR}", issue.verificationCommand)
    }

    @Test
    fun `modules skip missing or invalid descriptor but retain independent compiler checks`() {
        assertTrue(evaluateProjectConfiguration(valid.copy(module = true, descriptor = null)).isEmpty())
        val findings = diagnostics(valid.copy(
            module = true,
            descriptor = DiagnosticPluginDescriptor("211.*", "211.*"),
            targetCompatibility = JavaVersion.VERSION_25,
        ))
        assertEquals(setOf("java-target-exceeds-platform"), findings.keys)
        val missing = diagnostics(valid.copy(descriptor = null)).getValue("missing-plugin-descriptor").issue
        assertEquals("blockingConfigurationError", missing.category)
        assertEquals("warning", missing.severity)
        assertEquals("absent", missing.observedValue)
        assertEquals("pluginXml", missing.configurationLocation)
    }

    @Test
    fun `effective platform Java requirement takes precedence over build-number assumptions`() {
        val findings = diagnostics(valid.copy(platformJavaVersion = JavaVersion.VERSION_17))
        assertEquals(setOf("java-target-exceeds-platform", "kotlin-jvm-target-exceeds-platform"), findings.keys)
        assertEquals("<= 17", findings.getValue("java-target-exceeds-platform").issue.expectedValue)
        assertContains(findings.getValue("java-target-exceeds-platform").issue.evidence, "Target platform supports Java 17")
    }

    @Test
    fun `optional Kotlin metadata and unknown compatibility mappings do not invent findings`() {
        assertTrue(evaluateProjectConfiguration(valid.copy(
            kotlinJvmTarget = null,
            kotlinApiVersion = null,
            kotlinLanguageVersion = null,
            kotlinVersion = null,
        )).isEmpty())
        val findings = diagnostics(valid.copy(
            platformVersion = "2021.3",
            platformBuild = "213.1".toVersion(),
            platformJavaVersion = JavaVersion.VERSION_11,
            sourceCompatibility = JavaVersion.VERSION_11,
            targetCompatibility = JavaVersion.VERSION_11,
            kotlinJvmTarget = null,
            descriptor = DiagnosticPluginDescriptor("213", null),
        ))
        assertEquals(setOf("platform-version-too-old"), findings.keys)
    }

    @Test
    fun `version-specific rules respect minimum platform Kotlin and until-build boundaries`() {
        assertFalse("platform-version-too-old" in diagnostics(valid.copy(platformBuild = MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER)))
        assertTrue("platform-version-too-old" in diagnostics(valid.copy(platformBuild = "222.1".toVersion())))
        assertFalse("kotlin-version-too-old" in diagnostics(valid.copy(platformBuild = "243.1".toVersion(), kotlinVersion = "1.9".toVersion())))
        assertTrue("kotlin-version-too-old" in diagnostics(valid.copy(kotlinVersion = "1.9".toVersion())))
        assertFalse("kotlin-version-too-old" in diagnostics(valid.copy(kotlinVersion = "2.0.0".toVersion())))
        assertFalse("until-build-restricts-compatibility" in diagnostics(valid.copy(descriptor = DiagnosticPluginDescriptor("242", "242.*"))))
        assertTrue("until-build-restricts-compatibility" in diagnostics(valid.copy(descriptor = DiagnosticPluginDescriptor("243", "243.*"))))
        assertFalse("until-build-restricts-compatibility" in diagnostics(valid.copy(descriptor = DiagnosticPluginDescriptor("243", null))))
    }

    @Test
    fun `stdlib defaults are distinguished from explicit values and require Kotlin plugin`() {
        val default = diagnostics(valid.copy(kotlinStdlibDefaultDependency = null)).getValue("kotlin-stdlib-dependency").issue
        assertEquals("true (default)", default.observedValue)
        assertEquals("false", default.expectedValue)
        assertEquals("Gradle property: kotlin.stdlib.default.dependency", default.configurationLocation)
        assertTrue(default.evidence.any { "unset (defaults to true)" in it })
        assertEquals("true", diagnostics(valid.copy(kotlinStdlibDefaultDependency = true)).getValue("kotlin-stdlib-dependency").issue.observedValue)
        assertFalse("kotlin-stdlib-dependency" in diagnostics(valid))
        assertFalse("kotlin-stdlib-dependency" in diagnostics(valid.copy(kotlinPluginAvailable = false, kotlinStdlibDefaultDependency = true)))
    }

    @Test
    fun `cache check is emitted only for an observed missing ignore entry`() {
        assertFalse("cache-not-ignored" in diagnostics(valid.copy(cacheIgnored = null)))
        assertFalse("cache-not-ignored" in diagnostics(valid.copy(cacheIgnored = true)))
        assertEquals("warning", diagnostics(valid.copy(cacheIgnored = false)).getValue("cache-not-ignored").issue.category)
        assertTrue("cache-not-ignored" in diagnostics(valid.copy(module = true, cacheIgnored = false)))
    }

    @Test
    fun `verifier retains legacy wording and separate suppression surface`() {
        val diagnostic = diagnostics(valid.copy(sourceCompatibility = JavaVersion.VERSION_1_8)).getValue("java-source-below-platform")
        assertEquals(
            "Java sourceCompatibility too low for target platform Java sourceCompatibility is set to '1.8', but IntelliJ Platform '2025.1.6' requires Java '21'. This mismatch may prevent your plugin from compiling or using platform APIs correctly. Update sourceCompatibility to '21' in your build configuration.",
            diagnostic.verifierMessage,
        )
        assertContains(diagnostic.verifierMessage, "too low for target platform")
        assertFalse("too low for target platform" in diagnostic.issue.message)
        assertEquals("Java sourceCompatibility '1.8' is lower than Java '21' required by IntelliJ Platform 2025.1.6.", diagnostic.issue.message)
    }

    @Test
    fun `legacy issues decode with defaults and enriched issues round trip in version one envelope`() {
        val legacy = AgentReportTask.json.decodeFromString<Issue>("""{"id":"legacy","message":"Existing finding"}""")
        assertNull(legacy.observedValue)
        assertNull(legacy.expectedValue)
        assertNull(legacy.configurationLocation)
        assertNull(legacy.category)
        assertNull(legacy.verificationCommand)
        assertTrue(legacy.evidence.isEmpty())
        assertTrue(legacy.provenance.isEmpty())
        assertTrue(legacy.applicableIdeVersions.isEmpty())

        val issue = diagnostics(valid.copy(targetCompatibility = JavaVersion.VERSION_25)).getValue("java-target-exceeds-platform").issue
        val report = Report(reportType = "doctor", gradlePluginVersion = "test", projectPath = ":", status = ReportStatus.complete, data = "test", issues = listOf(issue))
        val encoded = AgentReportTask.json.encodeToString(report)
        val decoded = AgentReportTask.json.decodeFromString<Report<String>>(encoded)
        assertEquals(report, decoded)
        val json = Json.parseToJsonElement(encoded).jsonObject
        assertEquals(1, json.getValue("schemaVersion").jsonPrimitive.int)
        val jsonIssue = json.getValue("issues").jsonArray.single().jsonObject
        listOf("observedValue", "expectedValue", "configurationLocation", "category", "verificationCommand").forEach {
            assertTrue(jsonIssue.getValue(it).jsonPrimitive.isString, it)
        }
        listOf("evidence", "provenance", "applicableIdeVersions").forEach {
            assertTrue(jsonIssue.getValue(it).jsonArray.all { value -> value.jsonPrimitive.isString }, it)
        }
    }
}