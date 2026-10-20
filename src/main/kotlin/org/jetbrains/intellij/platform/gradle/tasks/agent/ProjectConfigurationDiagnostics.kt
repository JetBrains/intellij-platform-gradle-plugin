// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import org.gradle.api.JavaVersion
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_VERSION
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.PlatformKotlinVersions
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toPlatformJavaVersion
import org.jetbrains.intellij.platform.gradle.utils.toVersion

/** Effective descriptor values, after patching; [path] is the observed file, not the source of a DSL override. */
internal data class DiagnosticPluginDescriptor(val sinceBuild: String, val untilBuild: String?, val path: String? = null)

/** Resolved facts only: evaluating rules never resolves dependencies, acquires a runtime, or reads project files. */
internal data class ProjectConfigurationDiagnosticInput(
    val platformVersion: String,
    val platformBuild: Version,
    val platformJavaVersion: JavaVersion,
    val sourceCompatibility: JavaVersion,
    val targetCompatibility: JavaVersion,
    val kotlinJvmTarget: JavaVersion? = null,
    val kotlinApiVersion: Version? = null,
    val kotlinLanguageVersion: Version? = null,
    val kotlinVersion: Version? = null,
    val kotlinPluginAvailable: Boolean = false,
    val kotlinStdlibDefaultDependency: Boolean? = null,
    val kotlinxCoroutinesLibraryPresent: Boolean = false,
    val module: Boolean = false,
    val descriptor: DiagnosticPluginDescriptor? = null,
    val cacheIgnored: Boolean? = null,
    val buildFile: String? = null,
    val verificationCommand: String = "./gradlew :${Tasks.INTELLIJ_PLATFORM_DOCTOR}",
)

/** Shared finding, with the verifier's existing Problems/console wording kept separate from Doctor's JSON wording. */
internal data class ProjectConfigurationDiagnostic(
    val issue: Issue,
    val label: String,
    val details: String,
    val solution: String,
) {
    val verifierMessage get() = "$label $details $solution"
}

/** Evaluates the common Doctor/Verify rules in the verifier's established reporting order. */
internal fun evaluateProjectConfiguration(input: ProjectConfigurationDiagnosticInput): List<ProjectConfigurationDiagnostic> = with(input) {
    val platformKotlinVersion = platformBuild.diagnosticKotlinVersion()
    val doctorPlatformKotlinVersion = platformKotlinVersion?.run { Version(major, minor) }
    val platformVersion = platformVersion.toVersion()
    val platformEvidence = "IntelliJ Platform ${input.platformVersion} (build $platformBuild)"
    val platformProvenance = "Resolved IntelliJ Platform product-info.json and platform compatibility tables"
    val buildProvenance = "Effective Gradle Java/Kotlin compilation and dependency configuration"
    val descriptorLocation = "intellijPlatform.pluginConfiguration.ideaVersion (effective plugin.xml)"

    buildList {
        fun finding(
            id: String,
            message: String,
            setting: String,
            fix: String,
            label: String,
            details: String,
            solution: String,
            observed: String,
            expected: String,
            category: String = "incompatibility",
            location: String = buildFile?.let { "$it: $setting" } ?: setting,
            evidence: List<String> = listOf(platformEvidence),
            provenance: List<String> = listOf(buildProvenance, platformProvenance),
            applicableIdeVersions: List<String> = listOf("build $platformBuild"),
            documentation: String? = null,
        ) {
            add(ProjectConfigurationDiagnostic(
                issue = Issue(
                    id = id,
                    message = message,
                    setting = setting,
                    suggestedFix = fix,
                    documentation = documentation,
                    observedValue = observed,
                    expectedValue = expected,
                    configurationLocation = location,
                    evidence = evidence,
                    provenance = provenance,
                    category = category,
                    applicableIdeVersions = applicableIdeVersions,
                    verificationCommand = verificationCommand,
                ),
                label = label,
                details = details,
                solution = solution,
            ))
        }

        if (!module) {
            if (descriptor != null) {
                val sinceBuild = descriptor.sinceBuild.toVersion()
                val sinceBuildJavaVersion = sinceBuild.toPlatformJavaVersion()
                val sinceBuildKotlinVersion = sinceBuild.diagnosticKotlinVersion()
                val doctorSinceBuildKotlinVersion = sinceBuildKotlinVersion?.run { Version(major, minor) }
                val descriptorEvidence = listOf(platformEvidence, "Effective plugin.xml: since-build='$sinceBuild', until-build='${descriptor.untilBuild}'")
                val descriptorProvenance = listOfNotNull(descriptor.path?.let { "Patched plugin descriptor: $it" }, "Effective plugin.xml idea-version and platform compatibility tables")
                val sinceBuildVersions = listOf("since-build $sinceBuild", "target build $platformBuild")

                if ('*' in sinceBuild.version) {
                    finding(
                        id = "since-build-wildcard",
                        message = "The since-build='$sinceBuild' contains a wildcard, which is not supported.",
                        setting = "sinceBuild",
                        fix = "Use a specific build number like '${sinceBuild.major}'.",
                        label = "Invalid since-build version format",
                        details = "The since-build='$sinceBuild' contains a wildcard character (*). Wildcards are not supported in since-build declarations and may cause compatibility issues.",
                        solution = "Remove the wildcard from since-build in plugin.xml. Use a specific version number like '${sinceBuild.major}'.",
                        observed = descriptor.sinceBuild,
                        expected = "A specific build number without a wildcard, e.g. ${sinceBuild.major}",
                        category = "blockingConfigurationError",
                        location = descriptorLocation,
                        evidence = descriptorEvidence,
                        provenance = descriptorProvenance,
                        applicableIdeVersions = sinceBuildVersions,
                    )
                }
                if (sinceBuild.major < platformBuild.major) {
                    finding(
                        id = "since-build-below-platform",
                        message = "The since-build='$sinceBuild' is lower than the target platform major version '${platformBuild.major}'.",
                        setting = "sinceBuild",
                        fix = "Set since-build to '${platformBuild.major}' or higher.",
                        label = "since-build is lower than target platform version",
                        details = "The since-build='$sinceBuild' (major version ${sinceBuild.major}) is lower than the target IntelliJ Platform major version '${platformBuild.major}'. This means your plugin declares support for older IDE versions than you're building against.",
                        solution = "Update since-build in plugin.xml to match or exceed the target platform version: '${platformBuild.major}'.",
                        observed = descriptor.sinceBuild,
                        expected = ">= ${platformBuild.major}",
                        location = descriptorLocation,
                        evidence = descriptorEvidence,
                        provenance = descriptorProvenance,
                        applicableIdeVersions = sinceBuildVersions,
                    )
                }
                if (sinceBuild.major >= 243 && descriptor.untilBuild != null) {
                    finding(
                        id = "until-build-restricts-compatibility",
                        message = "For IntelliJ Platform 2024.3+ (build 243+), until-build restricts compatibility with future IDE versions.",
                        setting = "untilBuild",
                        fix = "Remove the until-build property.",
                        label = "until-build property should be removed",
                        details = "For IntelliJ Platform 2024.3+ (build 243+), the until-build property restricts plugin compatibility with future IDE versions. This prevents users from installing your plugin when they update to newer IDE versions.",
                        solution = "Remove the until-build property from plugin.xml to allow forward compatibility with future IDE versions.",
                        observed = descriptor.untilBuild,
                        expected = "absent",
                        category = "warning",
                        location = descriptorLocation,
                        evidence = descriptorEvidence,
                        provenance = descriptorProvenance,
                        applicableIdeVersions = listOf("build 243+", "since-build $sinceBuild"),
                    )
                }
                if (sinceBuildJavaVersion < targetCompatibility) {
                    finding(
                        id = "java-target-exceeds-since-build",
                        message = "Java targetCompatibility '$targetCompatibility' exceeds Java '$sinceBuildJavaVersion' required by since-build='$sinceBuild'.",
                        setting = "targetCompatibility",
                        fix = "Lower targetCompatibility to '$sinceBuildJavaVersion' or increase since-build.",
                        label = "Java targetCompatibility exceeds since-build requirements",
                        details = "Java targetCompatibility is set to '$targetCompatibility', but since-build='$sinceBuild' only requires Java '$sinceBuildJavaVersion'. This creates bytecode that may not be compatible with the minimum supported IDE version.",
                        solution = "Lower targetCompatibility to '$sinceBuildJavaVersion' or increase since-build to match the target Java version.",
                        observed = targetCompatibility.toString(),
                        expected = "<= $sinceBuildJavaVersion",
                        evidence = descriptorEvidence + "since-build requires Java $sinceBuildJavaVersion",
                        provenance = descriptorProvenance + buildProvenance,
                        applicableIdeVersions = sinceBuildVersions,
                    )
                }
                if (kotlinJvmTarget != null && sinceBuildJavaVersion < kotlinJvmTarget) {
                    finding(
                        id = "kotlin-jvm-target-exceeds-since-build",
                        message = "Kotlin jvmTarget '$kotlinJvmTarget' exceeds Java '$sinceBuildJavaVersion' required by since-build='$sinceBuild'.",
                        setting = "kotlinJvmTarget",
                        fix = "Lower Kotlin jvmTarget to '$sinceBuildJavaVersion' or increase since-build.",
                        label = "Kotlin jvmTarget exceeds since-build requirements",
                        details = "Kotlin jvmTarget is set to '$kotlinJvmTarget', but since-build='$sinceBuild' only requires Java '$sinceBuildJavaVersion'. This creates bytecode that may not be compatible with the minimum supported IDE version.",
                        solution = "Lower Kotlin jvmTarget to '$sinceBuildJavaVersion' or increase since-build to match the JVM target version.",
                        observed = kotlinJvmTarget.toString(),
                        expected = "<= $sinceBuildJavaVersion",
                        evidence = descriptorEvidence + "since-build requires Java $sinceBuildJavaVersion",
                        provenance = descriptorProvenance + buildProvenance,
                        applicableIdeVersions = sinceBuildVersions,
                    )
                }
                if (kotlinApiVersion != null && sinceBuildKotlinVersion != null && sinceBuildKotlinVersion < kotlinApiVersion) {
                    finding(
                        id = "kotlin-api-exceeds-since-build",
                        message = "Kotlin apiVersion '$kotlinApiVersion' exceeds Kotlin API '$doctorSinceBuildKotlinVersion' bundled with since-build='$sinceBuild'.",
                        setting = "kotlinApiVersion",
                        fix = "Lower Kotlin apiVersion to '$doctorSinceBuildKotlinVersion' or increase since-build.",
                        label = "Kotlin apiVersion exceeds since-build requirements",
                        details = "Kotlin apiVersion is set to '$kotlinApiVersion', but since-build='$sinceBuild' only requires Kotlin API '$sinceBuildKotlinVersion'. This may cause compatibility issues with the minimum supported IDE version.",
                        solution = "Lower Kotlin apiVersion to '$sinceBuildKotlinVersion' or increase since-build to match the Kotlin API version.",
                        observed = kotlinApiVersion.toString(),
                        expected = "<= $sinceBuildKotlinVersion",
                        evidence = descriptorEvidence + "since-build bundles Kotlin API $sinceBuildKotlinVersion",
                        provenance = descriptorProvenance + buildProvenance,
                        applicableIdeVersions = sinceBuildVersions,
                    )
                }
            } else {
                finding(
                    id = "missing-plugin-descriptor",
                    message = "The plugin.xml descriptor could not be found.",
                    setting = "pluginXml",
                    fix = "Create src/main/resources/META-INF/plugin.xml.",
                    label = "Missing plugin.xml descriptor",
                    details = "The plugin.xml descriptor file could not be found in the expected location. This file is required to define plugin metadata, extensions, and configurations.",
                    solution = "Create a plugin.xml file in src/main/resources/META-INF/ directory with the required plugin descriptor structure.",
                    observed = "absent",
                    expected = "A plugin.xml descriptor for a non-module plugin project",
                    category = "blockingConfigurationError",
                    location = "pluginXml",
                    evidence = listOf("The effective pluginXml task property is absent", "module=false"),
                    provenance = listOf("Effective Gradle pluginXml and module task properties"),
                )
            }
        }
        if (cacheIgnored == false) {
            finding(
                id = "cache-not-ignored",
                message = "The IntelliJ Platform cache directory '$CACHE_DIRECTORY' is not listed in .gitignore.",
                setting = ".gitignore",
                fix = "Add '$CACHE_DIRECTORY' to .gitignore.",
                label = "IntelliJ Platform cache not excluded from VCS",
                details = "The IntelliJ Platform cache directory ('$CACHE_DIRECTORY') is not listed in .gitignore. This directory contains downloaded IDE dependencies and should not be committed to version control.",
                solution = "Add '$CACHE_DIRECTORY' entry to your .gitignore file.",
                observed = "No entry containing $CACHE_DIRECTORY",
                expected = "An entry containing $CACHE_DIRECTORY",
                category = "warning",
                location = "root project .gitignore",
                evidence = listOf("The default root-project cache directory and .gitignore exist", "No .gitignore line contains $CACHE_DIRECTORY"),
                provenance = listOf("Root-project .gitignore contents and effective IntelliJ Platform cache path"),
            )
        }
        if (platformBuild < MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER) {
            finding(
                id = "platform-version-too-old",
                message = "The target IntelliJ Platform ${input.platformVersion} (build $platformBuild) is older than the minimal supported $MINIMAL_INTELLIJ_PLATFORM_VERSION.",
                setting = "platformVersion",
                fix = "Update the IntelliJ Platform dependency to $MINIMAL_INTELLIJ_PLATFORM_VERSION or newer.",
                label = "IntelliJ Platform version too old",
                details = "The project targets IntelliJ Platform version $platformVersion (build $platformBuild), but the minimum supported version is $MINIMAL_INTELLIJ_PLATFORM_VERSION (build $MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER). Older versions are no longer maintained and may have compatibility issues.",
                solution = "Update your IntelliJ Platform dependency to version $MINIMAL_INTELLIJ_PLATFORM_VERSION or newer.",
                observed = "${input.platformVersion} (build $platformBuild)",
                expected = ">= $MINIMAL_INTELLIJ_PLATFORM_VERSION (build $MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER)",
                category = "blockingConfigurationError",
            )
        }
        if (kotlinVersion != null && platformBuild >= Version(251) && kotlinVersion < Version(2)) {
            finding(
                id = "kotlin-version-too-old",
                message = "IntelliJ Platform 2025.1+ requires Kotlin 2.0.0 or newer, but Kotlin $kotlinVersion is used.",
                setting = "kotlinVersion",
                fix = "Update Kotlin to 2.0.0 or newer.",
                label = "Kotlin version too old for target platform",
                details = "IntelliJ Platform 2025.1+ (build 251+) requires Kotlin 2.0.0 or newer, but your project is using Kotlin $kotlinVersion. The platform's bundled Kotlin version may conflict with your project's dependencies.",
                solution = "Update your Kotlin version to 2.0.0 or newer in your build configuration.",
                observed = kotlinVersion.toString(),
                expected = ">= 2.0.0",
                applicableIdeVersions = listOf("build 251+", "target build $platformBuild"),
            )
        }
        if (platformJavaVersion > sourceCompatibility) {
            finding(
                id = "java-source-below-platform",
                message = "Java sourceCompatibility '$sourceCompatibility' is lower than Java '$platformJavaVersion' required by IntelliJ Platform ${input.platformVersion}.",
                setting = "sourceCompatibility",
                fix = "Set sourceCompatibility to '$platformJavaVersion'.",
                label = "Java sourceCompatibility too low for target platform",
                details = "Java sourceCompatibility is set to '$sourceCompatibility', but IntelliJ Platform '$platformVersion' requires Java '$platformJavaVersion'. This mismatch may prevent your plugin from compiling or using platform APIs correctly.",
                solution = "Update sourceCompatibility to '$platformJavaVersion' in your build configuration.",
                observed = sourceCompatibility.toString(),
                expected = ">= $platformJavaVersion",
                evidence = listOf(platformEvidence, "Target platform requires Java $platformJavaVersion"),
            )
        }
        if (kotlinLanguageVersion != null && platformKotlinVersion != null && platformKotlinVersion > kotlinLanguageVersion) {
            finding(
                id = "kotlin-language-below-platform",
                message = "Kotlin languageVersion '$kotlinLanguageVersion' is lower than Kotlin '$doctorPlatformKotlinVersion' bundled with IntelliJ Platform ${input.platformVersion}.",
                setting = "kotlinLanguageVersion",
                fix = "Set Kotlin languageVersion to '$doctorPlatformKotlinVersion'.",
                label = "Kotlin languageVersion too low for target platform",
                details = "Kotlin languageVersion is set to '$kotlinLanguageVersion', but IntelliJ Platform '$platformVersion' requires Kotlin '$platformKotlinVersion'. This may cause compatibility issues with platform APIs.",
                solution = "Update Kotlin languageVersion to '$platformKotlinVersion' in your build configuration.",
                observed = kotlinLanguageVersion.toString(),
                expected = ">= $platformKotlinVersion",
                evidence = listOf(platformEvidence, "Target platform bundles Kotlin $platformKotlinVersion (major.minor)"),
            )
        }
        if (platformJavaVersion < targetCompatibility) {
            finding(
                id = "java-target-exceeds-platform",
                message = "Java targetCompatibility '$targetCompatibility' exceeds Java '$platformJavaVersion' supported by IntelliJ Platform ${input.platformVersion}.",
                setting = "targetCompatibility",
                fix = "Lower targetCompatibility to '$platformJavaVersion'.",
                label = "Java targetCompatibility too high for target platform",
                details = "Java targetCompatibility is set to '$targetCompatibility', but IntelliJ Platform '$platformVersion' only supports Java '$platformJavaVersion'. This creates bytecode that cannot be executed by the target platform.",
                solution = "Lower targetCompatibility to '$platformJavaVersion' to match the platform's Java version.",
                observed = targetCompatibility.toString(),
                expected = "<= $platformJavaVersion",
                evidence = listOf(platformEvidence, "Target platform supports Java $platformJavaVersion"),
            )
        }
        if (kotlinJvmTarget != null && platformJavaVersion < kotlinJvmTarget) {
            finding(
                id = "kotlin-jvm-target-exceeds-platform",
                message = "Kotlin jvmTarget '$kotlinJvmTarget' exceeds Java '$platformJavaVersion' supported by IntelliJ Platform ${input.platformVersion}.",
                setting = "kotlinJvmTarget",
                fix = "Lower Kotlin jvmTarget to '$platformJavaVersion'.",
                label = "Kotlin jvmTarget too high for target platform",
                details = "Kotlin jvmTarget is set to '$kotlinJvmTarget', but IntelliJ Platform '$platformVersion' only supports Java '$platformJavaVersion'. This creates bytecode that cannot be executed by the target platform.",
                solution = "Lower Kotlin jvmTarget to '$platformJavaVersion' to match the platform's Java version.",
                observed = kotlinJvmTarget.toString(),
                expected = "<= $platformJavaVersion",
                evidence = listOf(platformEvidence, "Target platform supports Java $platformJavaVersion"),
            )
        }
        if (kotlinPluginAvailable && kotlinStdlibDefaultDependency != false) {
            finding(
                id = "kotlin-stdlib-dependency",
                message = "The Kotlin stdlib added by the Kotlin Gradle plugin may conflict with the version bundled in IntelliJ Platform.",
                setting = "kotlin.stdlib.default.dependency",
                fix = "Set 'kotlin.stdlib.default.dependency=false' in gradle.properties.",
                label = "Kotlin stdlib dependency conflict",
                details = "The Kotlin Standard Library (stdlib) is automatically added by the Gradle Kotlin plugin and may conflict with the version bundled in IntelliJ Platform. This can cause ClassNotFoundException or version mismatch issues at runtime.",
                solution = "Exclude the Kotlin stdlib dependency by setting 'kotlin.stdlib.default.dependency=false' in gradle.properties. See: https://jb.gg/intellij-platform-kotlin-stdlib",
                observed = kotlinStdlibDefaultDependency?.toString() ?: "true (default)",
                expected = "false",
                category = "warning",
                location = "Gradle property: kotlin.stdlib.default.dependency",
                evidence = listOf("Kotlin Gradle plugin is available", "kotlin.stdlib.default.dependency=${kotlinStdlibDefaultDependency ?: "unset (defaults to true)"}"),
                provenance = listOf("Kotlin Gradle plugin availability and effective Gradle property (including defaults)"),
                documentation = "https://jb.gg/intellij-platform-kotlin-stdlib",
            )
        }
        if (kotlinxCoroutinesLibraryPresent) {
            finding(
                id = "kotlin-coroutines-dependency",
                message = "The Kotlin Coroutines library is bundled with IntelliJ Platform and must not be added explicitly.",
                setting = "dependencies",
                fix = "Remove kotlinx-coroutines dependencies.",
                label = "Kotlin Coroutines library must not be added explicitly",
                details = "The Kotlin Coroutines library is bundled with IntelliJ Platform and should not be added as a project dependency. Including it explicitly may cause version conflicts and runtime errors.",
                solution = "Remove kotlinx-coroutines dependencies from your build configuration. The platform provides the correct version automatically. See: https://jb.gg/intellij-platform-kotlin-coroutines",
                observed = "kotlinx-coroutines library present",
                expected = "No explicit kotlinx-coroutines dependency",
                category = "warning",
                evidence = listOf("kotlinxCoroutinesLibraryPresent=true", platformEvidence),
                provenance = listOf("Effective Gradle Kotlin dependency metadata"),
                documentation = "https://jb.gg/intellij-platform-kotlin-coroutines",
            )
        }
    }
}

private fun Version.diagnosticKotlinVersion() =
    PlatformKotlinVersions.entries.firstOrNull { this >= it.key }?.value?.run { "$major.$minor".toVersion() }