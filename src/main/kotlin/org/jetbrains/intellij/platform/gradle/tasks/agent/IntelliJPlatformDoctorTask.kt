// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER
import org.jetbrains.intellij.platform.gradle.Constants.Constraints.MINIMAL_INTELLIJ_PLATFORM_VERSION
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.KotlinMetadataAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.parsePluginXml
import org.jetbrains.intellij.platform.gradle.utils.*
import kotlin.io.path.exists
import kotlin.io.path.readLines

@Serializable
internal data class DoctorReport(val targetIde: TargetIde)

/**
 * Evaluates the plugin project configuration against the resolved IntelliJ Platform and writes the findings as a JSON
 * report with stable identifiers, affected settings, and suggested fixes. Findings never fail the task unless `--fail-on`
 * is requested; the report is always written first.
 */
@UntrackedTask(because = "Diagnostics observe the current project and SDK environment")
abstract class IntelliJPlatformDoctorTask : AgentReportTask(), IntelliJPlatformVersionAware, KotlinMetadataAware, PluginAware {

    @get:Internal
    abstract val sourceCompatibility: Property<String>

    @get:Internal
    abstract val targetCompatibility: Property<String>

    /**
     * Whether the IntelliJ Platform cache directory is listed in `.gitignore`; absent when the check is not applicable.
     */
    @get:Internal
    abstract val cacheIgnored: Property<Boolean>

    @get:Internal
    abstract val mutedMessages: ListProperty<String>

    @get:Internal
    @get:Option(option = "fail-on", description = "Fail after writing the report: none (default), warning, or error.")
    abstract val failOn: Property<String>

    @TaskAction
    fun diagnose() {
        val threshold = failOn.get()
        require(threshold in listOf("none", "warning", "error")) { "--fail-on must be none, warning, or error" }

        val product = productInfo
        val target = TargetIde(product.productCode, product.version, product.buildNumber, platformPath.safePathString, "resolved")
        val unavailable = mutableListOf<Unavailable>()
        val issues = buildList {
            fun issue(id: String, message: String, setting: String, fix: String, documentation: String? = null) {
                add(Issue(id, "warning", message, setting, fix, documentation, suppressed = mutedMessages.get().any { it in message }))
            }

            val platformBuild = product.buildNumber.toVersion()
            val platformJavaVersion = product.toPlatformJavaVersion()
            val sourceJavaVersion = sourceCompatibility.get().toJavaVersion()
            val targetJavaVersion = targetCompatibility.get().toJavaVersion()
            val jvmTargetJavaVersion = kotlinJvmTarget.orNull?.toJavaVersion()
            val kotlinVersion = kotlinVersion.orNull?.toVersion()
            val platformKotlinVersion = platformBuild.platformKotlinVersion()

            when {
                module.get() -> unavailable += Unavailable("pluginXml", "notRequested", "Plugin descriptor checks do not apply to module projects.")
                pluginXml.orNull == null -> issue("missing-plugin-descriptor", "The plugin.xml descriptor could not be found.", "pluginXml", "Create src/main/resources/META-INF/plugin.xml.")
                else -> {
                    val bean = pluginXml.get().asPath.parsePluginXml()
                    val sinceBuild = bean.ideaVersion.sinceBuild.toVersion()
                    val sinceBuildJavaVersion = sinceBuild.toPlatformJavaVersion()
                    val sinceBuildKotlinVersion = sinceBuild.platformKotlinVersion()

                    if ('*' in sinceBuild.version) {
                        issue("since-build-wildcard", "The since-build='$sinceBuild' contains a wildcard, which is not supported.", "sinceBuild", "Use a specific build number like '${sinceBuild.major}'.")
                    }
                    if (sinceBuild.major < platformBuild.major) {
                        issue("since-build-below-platform", "The since-build='$sinceBuild' is lower than the target platform major version '${platformBuild.major}'.", "sinceBuild", "Set since-build to '${platformBuild.major}' or higher.")
                    }
                    if (sinceBuild.major >= 243 && bean.ideaVersion.untilBuild != null) {
                        issue("until-build-restricts-compatibility", "For IntelliJ Platform 2024.3+ (build 243+), until-build restricts compatibility with future IDE versions.", "untilBuild", "Remove the until-build property.")
                    }
                    if (sinceBuildJavaVersion < targetJavaVersion) {
                        issue("java-target-exceeds-since-build", "Java targetCompatibility '$targetJavaVersion' exceeds Java '$sinceBuildJavaVersion' required by since-build='$sinceBuild'.", "targetCompatibility", "Lower targetCompatibility to '$sinceBuildJavaVersion' or increase since-build.")
                    }
                    if (jvmTargetJavaVersion != null && sinceBuildJavaVersion < jvmTargetJavaVersion) {
                        issue("kotlin-jvm-target-exceeds-since-build", "Kotlin jvmTarget '$jvmTargetJavaVersion' exceeds Java '$sinceBuildJavaVersion' required by since-build='$sinceBuild'.", "kotlinJvmTarget", "Lower Kotlin jvmTarget to '$sinceBuildJavaVersion' or increase since-build.")
                    }
                    kotlinApiVersion.orNull?.toVersion()?.let {
                        if (sinceBuildKotlinVersion != null && sinceBuildKotlinVersion < it) {
                            issue("kotlin-api-exceeds-since-build", "Kotlin apiVersion '$it' exceeds Kotlin API '$sinceBuildKotlinVersion' bundled with since-build='$sinceBuild'.", "kotlinApiVersion", "Lower Kotlin apiVersion to '$sinceBuildKotlinVersion' or increase since-build.")
                        }
                    }
                }
            }
            if (cacheIgnored.orNull == false) {
                issue("cache-not-ignored", "The IntelliJ Platform cache directory '$CACHE_DIRECTORY' is not listed in .gitignore.", ".gitignore", "Add '$CACHE_DIRECTORY' to .gitignore.")
            }
            if (platformBuild < MINIMAL_INTELLIJ_PLATFORM_BUILD_NUMBER) {
                issue("platform-version-too-old", "The target IntelliJ Platform ${product.version} (build $platformBuild) is older than the minimal supported $MINIMAL_INTELLIJ_PLATFORM_VERSION.", "platformVersion", "Update the IntelliJ Platform dependency to $MINIMAL_INTELLIJ_PLATFORM_VERSION or newer.")
            }
            if (kotlinVersion != null && platformBuild >= Version(251) && kotlinVersion < Version(2)) {
                issue("kotlin-version-too-old", "IntelliJ Platform 2025.1+ requires Kotlin 2.0.0 or newer, but Kotlin $kotlinVersion is used.", "kotlinVersion", "Update Kotlin to 2.0.0 or newer.")
            }
            if (platformJavaVersion > sourceJavaVersion) {
                issue("java-source-below-platform", "Java sourceCompatibility '$sourceJavaVersion' is lower than Java '$platformJavaVersion' required by IntelliJ Platform ${product.version}.", "sourceCompatibility", "Set sourceCompatibility to '$platformJavaVersion'.")
            }
            if (platformJavaVersion < targetJavaVersion) {
                issue("java-target-exceeds-platform", "Java targetCompatibility '$targetJavaVersion' exceeds Java '$platformJavaVersion' supported by IntelliJ Platform ${product.version}.", "targetCompatibility", "Lower targetCompatibility to '$platformJavaVersion'.")
            }
            if (jvmTargetJavaVersion != null && platformJavaVersion < jvmTargetJavaVersion) {
                issue("kotlin-jvm-target-exceeds-platform", "Kotlin jvmTarget '$jvmTargetJavaVersion' exceeds Java '$platformJavaVersion' supported by IntelliJ Platform ${product.version}.", "kotlinJvmTarget", "Lower Kotlin jvmTarget to '$platformJavaVersion'.")
            }
            kotlinLanguageVersion.orNull?.toVersion()?.let {
                if (platformKotlinVersion != null && platformKotlinVersion > it) {
                    issue("kotlin-language-below-platform", "Kotlin languageVersion '$it' is lower than Kotlin '$platformKotlinVersion' bundled with IntelliJ Platform ${product.version}.", "kotlinLanguageVersion", "Set Kotlin languageVersion to '$platformKotlinVersion'.")
                }
            }
            if (kotlinPluginAvailable.get() && kotlinStdlibDefaultDependency.orNull != false) {
                issue("kotlin-stdlib-dependency", "The Kotlin stdlib added by the Kotlin Gradle plugin may conflict with the version bundled in IntelliJ Platform.", "kotlin.stdlib.default.dependency", "Set 'kotlin.stdlib.default.dependency=false' in gradle.properties.", "https://jb.gg/intellij-platform-kotlin-stdlib")
            }
            if (kotlinxCoroutinesLibraryPresent.get()) {
                issue("kotlin-coroutines-dependency", "The Kotlin Coroutines library is bundled with IntelliJ Platform and must not be added explicitly.", "dependencies", "Remove kotlinx-coroutines dependencies.", "https://jb.gg/intellij-platform-kotlin-coroutines")
            }
        }
        unavailable += Unavailable("runtimeVendor", "notRequested", "The JetBrains Runtime check requires resolving a runtime; run ${Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION} instead.")

        write("doctor", if (unavailable.any { it.state != "notRequested" }) ReportStatus.partial else ReportStatus.complete, DoctorReport(target), issues, unavailable)

        if (issues.any { !it.suppressed && (threshold == "warning" || threshold == "error" && it.severity == "error") }) {
            throw GradleException("IntelliJ Platform doctor found unsuppressed findings at the requested --fail-on threshold; see the JSON report.")
        }
    }

    private fun Version.platformKotlinVersion() = PlatformKotlinVersions.entries.firstOrNull { this >= it.key }?.value?.run { Version(major, minor) }

    init {
        description = "Writes a JSON report with actionable plugin project configuration findings."
        failOn.convention("none")
    }

    companion object : Registrable {
        override fun register(project: Project) = project.registerAgentReportTask<IntelliJPlatformDoctorTask>(Tasks.INTELLIJ_PLATFORM_DOCTOR) {
            val compileJavaTaskProvider = project.tasks.named<JavaCompile>(Tasks.External.COMPILE_JAVA)
            val rootDirectory = project.rootProject.rootDir.toPath()
            val cacheDirectory = project.extensionProvider.flatMap { it.caching.path }

            sourceCompatibility.convention(compileJavaTaskProvider.map { it.options.release.orNull?.toString() ?: it.sourceCompatibility })
            targetCompatibility.convention(compileJavaTaskProvider.map { it.options.release.orNull?.toString() ?: it.targetCompatibility })
            cacheIgnored.convention(project.provider {
                val cache = cacheDirectory.get().asPath
                val gitignore = rootDirectory.resolve(".gitignore")
                when {
                    cache != rootDirectory.resolve(CACHE_DIRECTORY) || !cache.exists() || !gitignore.exists() -> null
                    else -> gitignore.readLines().any { CACHE_DIRECTORY in it }
                }
            })
            mutedMessages.convention(project.providers[GradleProperties.VerifyPluginProjectConfigurationMutedMessages].map { it.splitCommaSeparated() })
        }
    }
}
