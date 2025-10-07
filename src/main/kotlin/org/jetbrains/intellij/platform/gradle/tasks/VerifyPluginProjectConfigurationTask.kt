// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.Severity
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_VERSION
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.problems.Problems
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.*
import java.io.File
import kotlin.io.path.exists
import kotlin.io.path.readLines

/**
 * Validates the plugin project configuration:
 *
 * - The [PatchPluginXmlTask.sinceBuild] property can't be lower than the target IntelliJ Platform major version.
 * - The Java/Kotlin `sourceCompatibility` and `targetCompatibility` properties should align Java versions required by [PatchPluginXmlTask.sinceBuild] and the currently used IntelliJ Platform.
 * - The Kotlin API version should align the version required by [PatchPluginXmlTask.sinceBuild] and the currently used IntelliJ Platform.
 * - The used IntelliJ Platform version must be equal or higher than the minimum supported version `2022.3` (`223`) defined in [MINIMAL_INTELLIJ_PLATFORM_VERSION].
 * - The dependency on the [Kotlin Standard Library](https://jb.gg/intellij-platform-kotlin-stdlib) should be excluded.
 * - The Kotlin Coroutines library [must not be added explicitly](https://jb.gg/intellij-platform-kotlin-coroutines) to the project as it is already provided with the IntelliJ Platform.
 *
 * @see <a href="https://jb.gg/intellij-platform-versions">Build Number Ranges</a>
 */
@CacheableTask
@Suppress("UnstableApiUsage")
abstract class VerifyPluginProjectConfigurationTask : DefaultTask(), IntelliJPlatformVersionAware, KotlinMetadataAware,
    RuntimeAware, PluginAware, ModuleAware, ProblemsAware {

    /**
     * Root project path.
     */
    @get:Internal
    abstract val rootDirectory: Property<File>

    /**
     * IntelliJ Platform cache directory.
     *
     * Default value: [IntelliJPlatformExtension.cachePath]
     */
    @get:Internal
    abstract val intellijPlatformCache: DirectoryProperty

    /**
     * The `.gitignore` file located in the [rootDirectory], tracked for content change.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val gitignoreFile: RegularFileProperty

    /**
     * [JavaCompile.sourceCompatibility] property defined in the build script.
     */
    @get:Internal
    abstract val sourceCompatibility: Property<String>

    /**
     * [JavaCompile.targetCompatibility] property defined in the build script.
     */
    @get:Internal
    abstract val targetCompatibility: Property<String>

    /**
     * List of message patterns to be muted.
     * Each pattern is matched against the message text using a [String.contains] check (case-sensitive).
     */
    @get:Internal
    abstract val mutedMessages: ListProperty<String>

    private val log = Logger(javaClass)

    @TaskAction
    fun verifyPluginConfiguration() {
        val isModule = module.get()
        val platformBuild = productInfo.buildNumber.toVersion()
        val platformVersion = productInfo.version.toVersion()
        val platformJavaVersion = getPlatformJavaVersion(platformBuild)
        val sourceCompatibilityJavaVersion = sourceCompatibility.get().toJavaVersion()
        val targetCompatibilityJavaVersion = targetCompatibility.get().toJavaVersion()
        val jvmTargetJavaVersion = kotlinJvmTarget.orNull?.toJavaVersion()
        val kotlinApiVersion = kotlinApiVersion.orNull?.toVersion()
        val kotlinLanguageVersion = kotlinLanguageVersion.orNull?.toVersion()
        val kotlinPluginAvailable = kotlinPluginAvailable.get()
        val kotlinStdlibDefaultDependency = kotlinStdlibDefaultDependency.orNull != false
        val kotlinVersion = kotlinVersion.orNull?.toVersion()
        val kotlinxCoroutinesLibraryPresent = kotlinxCoroutinesLibraryPresent.get()
        val platformKotlinLanguageVersion = getPlatformKotlinVersion(platformBuild)?.run { "$major.$minor".toVersion() }

        // Get muted message patterns from the property
        val mutedMessagePatterns = mutedMessages.getOrElse(emptyList())

        val messages = buildList {
            if (!isModule) {
                pluginXml.orNull
                    ?.let { file ->
                        val sinceBuild = file.parse { ideaVersion.sinceBuild.toVersion() }
                        val sinceBuildJavaVersion = getPlatformJavaVersion(sinceBuild)
                        val sinceBuildKotlinApiVersion =
                            getPlatformKotlinVersion(sinceBuild)?.run { "$major.$minor".toVersion() }

                        if (sinceBuild.version.contains('*')) {
                            val label = "Invalid since-build version format"
                            val details = "The since-build='$sinceBuild' contains a wildcard character (*). Wildcards are not supported in since-build declarations and may cause compatibility issues."
                            val solution = "Remove the wildcard from since-build in plugin.xml. Use a specific version number like '${sinceBuild.major}'."
                            report(label) {
                                details(details)
                                solution(solution)
                            }
                            add("$label $details $solution")
                        }
                        if (sinceBuild.major < platformBuild.major) {
                            val label = "since-build is lower than target platform version"
                            val details = "The since-build='$sinceBuild' (major version ${sinceBuild.major}) is lower than the target IntelliJ Platform major version '${platformBuild.major}'. This means your plugin declares support for older IDE versions than you're building against."
                            val solution = "Update since-build in plugin.xml to match or exceed the target platform version: '${platformBuild.major}'."
                            report(label) {
                                details(details)
                                solution(solution)
                            }
                            add("$label $details $solution")
                        }
                        if (sinceBuild.major >= 243 && file.parse { ideaVersion.untilBuild != null }) {
                            val label = "until-build property should be removed"
                            val details = "For IntelliJ Platform 2024.3+ (build 243+), the until-build property restricts plugin compatibility with future IDE versions. This prevents users from installing your plugin when they update to newer IDE versions."
                            val solution = "Remove the until-build property from plugin.xml to allow forward compatibility with future IDE versions."
                            report(label) {
                                details(details)
                                solution(solution)
                            }
                            add("$label $details $solution")
                        }
                        if (sinceBuildJavaVersion < targetCompatibilityJavaVersion) {
                            val label = "Java targetCompatibility exceeds since-build requirements"
                            val details = "Java targetCompatibility is set to '$targetCompatibilityJavaVersion', but since-build='$sinceBuild' only requires Java '$sinceBuildJavaVersion'. This creates bytecode that may not be compatible with the minimum supported IDE version."
                            val solution = "Lower targetCompatibility to '$sinceBuildJavaVersion' or increase since-build to match the target Java version."
                            report(label) {
                                details(details)
                                solution(solution)
                            }
                            add("$label $details $solution")
                        }
                        if (sinceBuildJavaVersion < jvmTargetJavaVersion) {
                            val label = "Kotlin jvmTarget exceeds since-build requirements"
                            val details = "Kotlin jvmTarget is set to '$jvmTargetJavaVersion', but since-build='$sinceBuild' only requires Java '$sinceBuildJavaVersion'. This creates bytecode that may not be compatible with the minimum supported IDE version."
                            val solution = "Lower Kotlin jvmTarget to '$sinceBuildJavaVersion' or increase since-build to match the JVM target version."
                            report(label) {
                                details(details)
                                solution(solution)
                            }
                            add("$label $details $solution")
                        }
                        if (sinceBuildKotlinApiVersion < kotlinApiVersion) {
                            val label = "Kotlin apiVersion exceeds since-build requirements"
                            val details = "Kotlin apiVersion is set to '$kotlinApiVersion', but since-build='$sinceBuild' only requires Kotlin API '$sinceBuildKotlinApiVersion'. This may cause compatibility issues with the minimum supported IDE version."
                            val solution = "Lower Kotlin apiVersion to '$sinceBuildKotlinApiVersion' or increase since-build to match the Kotlin API version."
                            report(label) {
                                details(details)
                                solution(solution)
                            }
                            add("$label $details $solution")
                        }
                    }
                    ?: run {
                        val label = "Missing plugin.xml descriptor"
                        val details = "The plugin.xml descriptor file could not be found in the expected location. This file is required to define plugin metadata, extensions, and configurations."
                        val solution = "Create a plugin.xml file in src/main/resources/META-INF/ directory with the required plugin descriptor structure."
                        report(label) {
                            details(details)
                            solution(solution)
                        }
                        add("$label $details $solution")
                    }

                run {
                    val gitignore = gitignoreFile.orNull?.asPath ?: return@run
                    val cache = intellijPlatformCache.asPath.takeIf { it.exists() } ?: return@run
                    val root = rootDirectory.get().toPath()

                    if (cache != root.resolve(CACHE_DIRECTORY)) {
                        return@run
                    }

                    val containsEntry = gitignore.readLines().any { line -> line.contains(CACHE_DIRECTORY) }
                    if (!containsEntry) {
                        val label = "IntelliJ Platform cache not excluded from VCS"
                        val details = "The IntelliJ Platform cache directory ('$CACHE_DIRECTORY') is not listed in .gitignore. This directory contains downloaded IDE dependencies and should not be committed to version control."
                        val solution = "Add '$CACHE_DIRECTORY' entry to your .gitignore file."
                        report(label) {
                            details(details)
                            solution(solution)
                        }
                        add("$label $details $solution")
                    }
                }
            }

            if (platformBuild < MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER) {
                val label = "IntelliJ Platform version too old"
                val details = "The project targets IntelliJ Platform version $platformVersion (build $platformBuild), but the minimum supported version is $MINIMAL_INTELLIJ_PLATFORM_VERSION (build $MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER). Older versions are no longer maintained and may have compatibility issues."
                val solution = "Update your IntelliJ Platform dependency to version $MINIMAL_INTELLIJ_PLATFORM_VERSION or newer."
                report(label) {
                    details(details)
                    solution(solution)
                }
                add("$label $details $solution")
            }
            if (platformBuild >= Version(251) && kotlinVersion < Version(2)) {
                val label = "Kotlin version too old for target platform"
                val details = "IntelliJ Platform 2025.1+ (build 251+) requires Kotlin 2.0.0 or newer, but your project is using Kotlin $kotlinVersion. The platform's bundled Kotlin version may conflict with your project's dependencies."
                val solution = "Update your Kotlin version to 2.0.0 or newer in your build configuration."
                report(label) {
                    details(details)
                    solution(solution)
                }
                add("$label $details $solution")
            }
            if (platformJavaVersion > sourceCompatibilityJavaVersion) {
                val label = "Java sourceCompatibility too low for target platform"
                val details = "Java sourceCompatibility is set to '$sourceCompatibilityJavaVersion', but IntelliJ Platform '$platformVersion' requires Java '$platformJavaVersion'. This mismatch may prevent your plugin from compiling or using platform APIs correctly."
                val solution = "Update sourceCompatibility to '$platformJavaVersion' in your build configuration."
                report(label) {
                    details(details)
                    solution(solution)
                }
                add("$label $details $solution")
            }
            if (platformKotlinLanguageVersion > kotlinLanguageVersion) {
                val label = "Kotlin languageVersion too low for target platform"
                val details = "Kotlin languageVersion is set to '$kotlinLanguageVersion', but IntelliJ Platform '$platformVersion' requires Kotlin '$platformKotlinLanguageVersion'. This may cause compatibility issues with platform APIs."
                val solution = "Update Kotlin languageVersion to '$platformKotlinLanguageVersion' in your build configuration."
                report(label) {
                    details(details)
                    solution(solution)
                }
                add("$label $details $solution")
            }
            if (platformJavaVersion < targetCompatibilityJavaVersion) {
                val label = "Java targetCompatibility too high for target platform"
                val details = "Java targetCompatibility is set to '$targetCompatibilityJavaVersion', but IntelliJ Platform '$platformVersion' only supports Java '$platformJavaVersion'. This creates bytecode that cannot be executed by the target platform."
                val solution = "Lower targetCompatibility to '$platformJavaVersion' to match the platform's Java version."
                report(label) {
                    details(details)
                    solution(solution)
                }
                add("$label $details $solution")
            }
            if (platformJavaVersion < jvmTargetJavaVersion) {
                val label = "Kotlin jvmTarget too high for target platform"
                val details = "Kotlin jvmTarget is set to '$jvmTargetJavaVersion', but IntelliJ Platform '$platformVersion' only supports Java '$platformJavaVersion'. This creates bytecode that cannot be executed by the target platform."
                val solution = "Lower Kotlin jvmTarget to '$platformJavaVersion' to match the platform's Java version."
                report(label) {
                    details(details)
                    solution(solution)
                }
                add("$label $details $solution")
            }
            if (kotlinPluginAvailable && kotlinStdlibDefaultDependency) {
                val label = "Kotlin stdlib dependency conflict"
                val details = "The Kotlin Standard Library (stdlib) is automatically added by the Gradle Kotlin plugin and may conflict with the version bundled in IntelliJ Platform. This can cause ClassNotFoundException or version mismatch issues at runtime."
                val solution = "Exclude the Kotlin stdlib dependency by setting 'kotlin.stdlib.default.dependency=false' in gradle.properties. See: https://jb.gg/intellij-platform-kotlin-stdlib"
                report(label) {
                    details(details)
                    solution(solution)
                    documentedAt("https://jb.gg/intellij-platform-kotlin-stdlib")
                }
                add("$label $details $solution")
            }
            if (kotlinxCoroutinesLibraryPresent) {
                val label = "Kotlin Coroutines library must not be added explicitly"
                val details = "The Kotlin Coroutines library is bundled with IntelliJ Platform and should not be added as a project dependency. Including it explicitly may cause version conflicts and runtime errors."
                val solution = "Remove kotlinx-coroutines dependencies from your build configuration. The platform provides the correct version automatically. See: https://jb.gg/intellij-platform-kotlin-coroutines"
                report(label) {
                    details(details)
                    solution(solution)
                    documentedAt("https://jb.gg/intellij-platform-kotlin-coroutines")
                }
                add("$label $details $solution")
            }
            if (runtimeMetadata.get()["java.vendor"] != "JetBrains s.r.o.") {
                val label = "Non-JetBrains Runtime detected"
                val details = "The current Java Runtime is not JetBrains Runtime (JBR). JBR includes IDE-specific patches and optimizations. Using a different runtime may lead to unexpected behaviors, rendering issues, or missing features."
                val solution = "Use IntelliJ Platform with bundled JBR, or configure JVM Toolchain to use JBR explicitly. See: https://jb.gg/intellij-platform-with-jbr"
                report(label) {
                    details(details)
                    solution(solution)
                    documentedAt("https://jb.gg/intellij-platform-with-jbr")
                }
                add("$label $details $solution")
            }
        }
            .filter { message -> mutedMessagePatterns.none { pattern -> message.contains(pattern) } }
            .joinToString("\n") { "- $it" }
            .takeIf { it.isNotEmpty() }
            ?.also { log.warn("The following plugin configuration issues were found:\n$it") }
    }

    private fun report(
        label: String,
        severity: Severity = Severity.WARNING,
        spec: org.gradle.api.problems.ProblemSpec.() -> Unit = {},
    ) {
        problems.reporter.report(Problems.VerifyPluginProjectConfiguration.ConfigurationIssue) {
            contextualLabel(label)
            severity(severity)
            spec()
        }
    }

    private fun getPlatformJavaVersion(buildNumber: Version) =
        PlatformJavaVersions.entries.firstOrNull { buildNumber >= it.key }?.value

    private fun getPlatformKotlinVersion(buildNumber: Version) =
        PlatformKotlinVersions.entries.firstOrNull { buildNumber >= it.key }?.value

    private operator fun JavaVersion?.compareTo(other: JavaVersion?) = other?.let { this?.compareTo(it) } ?: 0

    private operator fun Version?.compareTo(other: Version?) = other?.let { this?.compareTo(it) } ?: 0

    init {
        group = Plugin.GROUP_NAME
        description = "Validates the plugin project configuration."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<VerifyPluginProjectConfigurationTask>(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
                log.info("Configuring plugin configuration verification task")

                val compileJavaTaskProvider = project.tasks.named<JavaCompile>(Tasks.External.COMPILE_JAVA)
                val rootDirectoryProvider = project.provider { project.rootProject.rootDir }

                rootDirectory.convention(rootDirectoryProvider)
                intellijPlatformCache.convention(project.extensionProvider.flatMap { it.caching.path } )
                gitignoreFile.convention(project.layout.file(project.provider {
                    project.rootProject.rootDir.resolve(".gitignore").takeIf { it.exists() }
                }))
                sourceCompatibility.convention(compileJavaTaskProvider.map { it.sourceCompatibility })
                targetCompatibility.convention(compileJavaTaskProvider.map { it.targetCompatibility })
                mutedMessages.convention(
                    project.providers[GradleProperties.VerifyPluginProjectConfigurationMutedMessages]
                        .map { it.split(',').map(String::trim).filter(String::isNotEmpty) }
                )
            }
    }
}
