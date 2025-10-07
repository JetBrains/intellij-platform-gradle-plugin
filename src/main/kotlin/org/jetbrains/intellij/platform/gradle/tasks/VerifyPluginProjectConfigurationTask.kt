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
                            val message = "The since-build='$sinceBuild' should not contain wildcard."
                            report(message)
                            add(message)
                        }
                        if (sinceBuild.major < platformBuild.major) {
                            val message = "The since-build='$sinceBuild' is lower than the target IntelliJ Platform major version: '${platformBuild.major}'."
                            report(message)
                            add(message)
                        }
                        if (sinceBuild.major >= 243 && file.parse { ideaVersion.untilBuild != null }) {
                            val message = "The until-build property is not recommended for use. Consider using empty until-build for future plugin versions, so users can use your plugin when they update IDE to the latest version."
                            report(message)
                            add(message)
                        }
                        if (sinceBuildJavaVersion < targetCompatibilityJavaVersion) {
                            val message = "The Java configuration specifies targetCompatibility=$targetCompatibilityJavaVersion but since-build='$sinceBuild' property requires targetCompatibility='$sinceBuildJavaVersion'."
                            report(message)
                            add(message)
                        }
                        if (sinceBuildJavaVersion < jvmTargetJavaVersion) {
                            val message = "The Kotlin configuration specifies jvmTarget='$jvmTargetJavaVersion' but since-build='$sinceBuild' property requires jvmTarget='$sinceBuildJavaVersion'."
                            report(message)
                            add(message)
                        }
                        if (sinceBuildKotlinApiVersion < kotlinApiVersion) {
                            val message = "The Kotlin configuration specifies apiVersion='$kotlinApiVersion' but since-build='$sinceBuild' property requires apiVersion='$sinceBuildKotlinApiVersion'."
                            report(message)
                            add(message)
                        }
                    }
                    ?: run {
                        val message = "The plugin.xml descriptor file could not be found."
                        report(message)
                        add(message)
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
                        val message = "The IntelliJ Platform cache directory should be excluded from the version control system. Add the '$CACHE_DIRECTORY' entry to the '.gitignore' file."
                        report(message)
                        add(message)
                    }
                }
            }

            if (platformBuild < MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER) {
                val message = "The minimal supported IntelliJ Platform version is $MINIMAL_INTELLIJ_PLATFORM_VERSION ($MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER), current: $platformVersion ($platformBuild)"
                report(message)
                add(message)
            }
            if (platformBuild >= Version(251) && kotlinVersion < Version(2)) {
                val message = "When targeting the IntelliJ Platform in version 2025.1+ (251+), the required Kotlin version is 2.0.0+, current: $kotlinVersion"
                report(message)
                add(message)
            }
            if (platformJavaVersion > sourceCompatibilityJavaVersion) {
                val message = "The Java configuration specifies sourceCompatibility='$sourceCompatibilityJavaVersion' but IntelliJ Platform '$platformVersion' requires sourceCompatibility='$platformJavaVersion'."
                report(message)
                add(message)
            }
            if (platformKotlinLanguageVersion > kotlinLanguageVersion) {
                val message = "The Kotlin configuration specifies languageVersion='$kotlinLanguageVersion' but IntelliJ Platform '$platformVersion' requires languageVersion='$platformKotlinLanguageVersion'."
                report(message)
                add(message)
            }
            if (platformJavaVersion < targetCompatibilityJavaVersion) {
                val message = "The Java configuration specifies targetCompatibility='$targetCompatibilityJavaVersion' but IntelliJ Platform '$platformVersion' requires targetCompatibility='$platformJavaVersion'."
                report(message)
                add(message)
            }
            if (platformJavaVersion < jvmTargetJavaVersion) {
                val message = "The Kotlin configuration specifies jvmTarget='$jvmTargetJavaVersion' but IntelliJ Platform '$platformVersion' requires jvmTarget='$platformJavaVersion'."
                report(message)
                add(message)
            }
            if (kotlinPluginAvailable && kotlinStdlibDefaultDependency) {
                val message = "The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib"
                report(message)
                add(message)
            }
            if (kotlinxCoroutinesLibraryPresent) {
                val message = "The Kotlin Coroutines library must not be added explicitly to the project nor as a transitive dependency as it is already provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-coroutines"
                report(message)
                add(message)
            }
            if (runtimeMetadata.get()["java.vendor"] != "JetBrains s.r.o.") {
                val message = "The currently selected Java Runtime is not JetBrains Runtime (JBR). This may lead to unexpected IDE behaviors. Please use IntelliJ Platform binary release with bundled JBR or define it explicitly with project dependencies or JVM Toolchain, see: https://jb.gg/intellij-platform-with-jbr"
                report(message)
                add(message)
            }
        }
            .filter { message -> mutedMessagePatterns.none { pattern -> message.contains(pattern) } }
            .joinToString("\n") { "- $it" }
            .takeIf { it.isNotEmpty() }
            ?.also { log.warn("The following plugin configuration issues were found:\n$it") }
    }

    private fun report(label: String, details: String = "", severity: Severity = Severity.WARNING) =
        problems.reporter.report(Problems.VerifyPluginProjectConfiguration.ConfigurationIssue) {
            contextualLabel(label)
            details(details)
            severity(severity)
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
