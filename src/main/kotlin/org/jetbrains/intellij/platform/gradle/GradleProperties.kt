// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.file.ProjectLayout
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY_IDES
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY_IVY
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask
import org.jetbrains.intellij.platform.gradle.tasks.InitializeIntelliJPlatformPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginProjectConfigurationTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories

/**
 * The IntelliJ Platform Gradle Plugin introduces custom Gradle properties to control some of the low-level Gradle plugin behaviors.
 * To adjust a particular feature, add a Project property to the `gradle.properties` file with the following pattern:
 *
 * ```
 * org.jetbrains.intellij.platform.<propertyName>=<value>
 * ```
 */
sealed class GradleProperties<T : Any>(val defaultValue: T) {

    /**
     * Controls whether default IntelliJ Platform dependencies (`com.intellij` and `intellij.rider`)
     * should be added automatically when creating a dependency on the IntelliJ Platform.
     *
     * Default value: `true`
     */
    object AddDefaultIntelliJPlatformDependencies : GradleProperties<Boolean>(true)

    /**
     * Instruct the IDE that sources are needed to be downloaded when working with IntelliJ Platform Gradle Plugin.
     * Value is passed directly to the [Idea Gradle Plugin](https://docs.gradle.org/current/userguide/idea_plugin.html)
     * to the `idea.module.downloadSources` property.
     *
     * @see <a href="https://docs.gradle.org/current/dsl/org.gradle.plugins.ide.idea.model.IdeaModule.html#org.gradle.plugins.ide.idea.model.IdeaModule:downloadSources">IdeaModule.downloadSources</a>
     */
    object DownloadSources : GradleProperties<Boolean>(true)

    /**
     * Specifies the location of the local IntelliJ Platform cache directory for storing files related to the current project, like:
     * - XML files generated for the [IntelliJPlatformRepositoriesExtension.localPlatformArtifacts] local Ivy repository
     * - self-update lock file used by the [InitializeIntelliJPlatformPluginTask] task
     *
     * Note: this directory should be excluded from versioning.
     *
     * Default value: [ProjectLayout.getProjectDirectory]/.intellijPlatform/
     *
     * @see ProviderFactory.intellijPlatformCachePath
     */
    object IntellijPlatformCache : GradleProperties<String>("")

    /**
     * Specifies the location of the IntelliJ Platform IDEs cache directory for storing downloaded IDE distributions
     * and related artifacts. This cache is used to avoid re-downloading the same IDE versions across different
     * project builds and can be shared between multiple projects.
     *
     * Note: this directory can be shared across projects and should be excluded from versioning.
     *
     * Default value: [IntellijPlatformCache]/ides/
     */
    object IntellijPlatformIdesCache : GradleProperties<String>("")

    /**
     * The [IntelliJPlatformRepositoriesExtension.localPlatformArtifacts] entry applied to the `repositories {}` block is required
     * to apply to the project dependencies that need extra pre-processing before they can be correctly used by the IntelliJ Platform Gradle Plugin
     * and loaded by Gradle.
     *
     * Default value: [IntellijPlatformCache]/localPlatformArtifacts/
     *
     * @see ProviderFactory.localPlatformArtifactsPath
     */
    object LocalPlatformArtifacts : GradleProperties<String>("")

    /**
     * When the [BuildSearchableOptionsTask] doesn't produce any results, for example, when the plugin doesn't implement any settings, a warning is shown
     * to suggest disabling it for better performance with [IntelliJPlatformExtension.buildSearchableOptions].
     */
    object NoSearchableOptionsWarning : GradleProperties<Boolean>(true)

    /**
     * Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for a paid plugin.
     * As paid plugins require providing a valid license and presenting a UI dialog, it is impossible to handle such a case, and the task will fail.
     * This feature flag displays the given warning when the task is run by a paid plugin.
     *
     * Default value: `true`
     */
    object PaidPluginSearchableOptionsWarning : GradleProperties<Boolean>(true)

    /**
     * Specifies the URL from which the list of all Android Studio releases is fetched.
     * This listing is later parsed by [ProductReleasesValueSource] to provide a list of IDEs matching the filtering criteria for running
     * the IntelliJ Plugin Verifier tool with the [VerifyPluginTask] task.
     *
     * Default value: [Locations.PRODUCTS_RELEASES_ANDROID_STUDIO]
     */
    object ProductsReleasesAndroidStudioUrl : GradleProperties<String>(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO)

    /**
     * Specifies the URL from which the list of all JetBrains IDEs releases is fetched.
     * This listing is later parsed by [ProductReleasesValueSource] to provide a list of IDEs matching the filtering criteria for running
     * the IntelliJ Plugin Verifier tool with the [VerifyPluginTask] task.
     *
     * Default value: [Locations.PRODUCTS_RELEASES_JETBRAINS_IDES]
     */
    object ProductsReleasesJetBrainsIdesUrl : GradleProperties<String>(Locations.PRODUCTS_RELEASES_JETBRAINS_IDES)

    /**
     * Specifies the URL from which the list of all JetBrains IDEs CDN release builds is fetched.
     * This listing is used for mapping IDE releases to build numbers to download the corresponding JetBrains Client archive.
     *
     * Default value: [Locations.PRODUCTS_RELEASES_CDN_BUILDS]
     */
    object ProductsReleasesCdnBuildsUrl : GradleProperties<String>(Locations.PRODUCTS_RELEASES_CDN_BUILDS)

    /**
     * Checks whether the currently used Gradle IntelliJ Plugin is outdated and if a new release is available.
     * The plugin performs an update check on every run asking the GitHub Releases page for the redirection URL
     * to the latest version with `HEAD` HTTP request: `https://github.com/JetBrains/intellij-platform-gradle-plugin/releases/latest`.
     *
     * If the current version is outdated, the plugin will emit a warning with its current and the latest version.
     *
     * Feature respects the Gradle `--offline` mode.
     */
    object SelfUpdateCheck : GradleProperties<Boolean>(true)

    /**
     * Specifies the default Shim server port at which the local webserver is run.
     * The Shim server is used to proxy requests to the authorized custom plugin repositories registered with [IntelliJPlatformRepositoriesExtension.customPluginRepository].
     *
     * Default value: `7348`
     */
    object ShimServerPort : GradleProperties<Int>(7348)

    /**
     * By default, JetBrains Cache Redirector is used when resolving Maven repositories or any resources used by the IntelliJ Platform Gradle Plugin.
     * Due to limitations, sometimes it is desired to limit the list of remote endpoints accessed by Gradle.
     *
     * It is possible to refer to the direct location (whenever it is possible) by switching off JetBrains Cache Redirector globally.
     */
    object UseCacheRedirector : GradleProperties<Boolean>(true)

    /**
     * Allows muting specific messages reported by the [VerifyPluginProjectConfigurationTask] task.
     * The property accepts a comma-separated list of message patterns to be muted.
     * Each pattern is matched against the message text using a [String.contains] check (case-sensitive).
     *
     * For example, to mute warnings about Kotlin Standard Library and JetBrains Runtime:
     * ```
     * org.jetbrains.intellij.platform.verifyPluginProjectConfigurationMutedMessages=Kotlin Standard Library,Java Runtime is not JetBrains Runtime
     * ```
     *
     * Default value: empty string (no messages are muted)
     */
    object VerifyPluginProjectConfigurationMutedMessages : GradleProperties<String>("")

    override fun toString() =
        requireNotNull(this::class.simpleName)
            .replaceFirstChar(Char::lowercase)
            .let { Plugin.ID + '.' + it }
}

inline operator fun <reified T : Any> ProviderFactory.get(property: GradleProperties<T>) =
    gradleProperty(property.toString())
        .map {
            Logger(javaClass).info("Read Gradle property: $property=$it")

            when (property.defaultValue) {
                is Boolean -> it.toBoolean()
                is Int -> it.toInt()
                is String -> it
                else -> it
            } as T
        }
        .orElse(property.defaultValue)

// TODO: simplify those methods

/**
 * Returns the IntelliJ Platform Gradle Plugin cache directory for the current project.
 */
internal fun ProviderFactory.intellijPlatformCachePath(rootProjectDirectory: Path) =
    get(GradleProperties.IntellijPlatformCache).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: rootProjectDirectory.resolve(CACHE_DIRECTORY) }
        .createDirectories()
        .absolute()

/**
 * Represents the local platform artifacts directory path which contains Ivy XML files.
 *
 * @see [GradleProperties.LocalPlatformArtifacts]
 */
internal fun ProviderFactory.localPlatformArtifactsPath(rootProjectDirectory: Path) =
    get(GradleProperties.LocalPlatformArtifacts).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: intellijPlatformCachePath(rootProjectDirectory).resolve(CACHE_DIRECTORY_IVY) }
        .createDirectories()
        .absolute()

/**
 *
 *
 * @see [GradleProperties.IntellijPlatformIdesCache]
 */
internal fun ProviderFactory.intellijPlatformIdesCachePath(rootProjectDirectory: Path) =
    get(GradleProperties.IntellijPlatformIdesCache).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: intellijPlatformCachePath(rootProjectDirectory).resolve(CACHE_DIRECTORY_IDES) }
        .createDirectories()
        .absolute()
