// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.provider.ProviderFactory
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.BuildSearchableOptionsTask
import org.jetbrains.intellij.platform.gradle.utils.Logger

/**
 * The IntelliJ Platform Gradle Plugin build features dedicated to control some of the low-level Gradle plugin behaviors.
 * To enable or disable a particular feature, add a Project property to the `gradle.properties` file with the following pattern:
 *
 * ```
 * org.jetbrains.intellij.platform.buildFeature.<buildFeatureName>=<true|false>
 * ```
 *
 * Switch to [org.gradle.api.configuration.BuildFeatures] when supporting Gradle 8.5+.
 */
enum class BuildFeature(private val defaultValue: Boolean) {
    /**
     * Instruct the IDE that sources are needed to be downloaded when working with IntelliJ Platform Gradle Plugin.
     * Value is passed directly to the [Idea Gradle Plugin](https://docs.gradle.org/current/userguide/idea_plugin.html)
     * to the `idea.module.downloadSources` property.
     *
     * @see <a href="https://docs.gradle.org/current/dsl/org.gradle.plugins.ide.idea.model.IdeaModule.html#org.gradle.plugins.ide.idea.model.IdeaModule:downloadSources">IdeaModule.downloadSources</a>
     */
    DOWNLOAD_SOURCES(true),

    /**
     * When the [BuildSearchableOptionsTask] doesn't produce any results, e.g., when the plugin doesn't implement any settings, a warning is shown to suggest disabling it
     * for better performance with [IntelliJPlatformExtension.buildSearchableOptions].
     */
    NO_SEARCHABLE_OPTIONS_WARNING(true),

    /**
     * Due to IDE limitations, it is impossible to run the IDE in headless mode to collect searchable options for a paid plugin.
     * As paid plugins require providing a valid license and presenting a UI dialog, it is impossible to handle such a case, and the task will fail.
     * This feature flag displays the given warning when the task is run by a paid plugin.
     */
    PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING(true),

    /**
     * Checks whether the currently used Gradle IntelliJ Plugin is outdated and if a new release is available.
     * The plugin performs an update check on every run asking the GitHub Releases page for the redirection URL
     * to the latest version with `HEAD` HTTP request: `https://github.com/JetBrains/intellij-platform-gradle-plugin/releases/latest`.
     *
     * If the current version is outdated, the plugin will emit a warning with its current and the latest version.
     *
     * Feature respects the Gradle `--offline` mode.
     *
     */
    SELF_UPDATE_CHECK(true),

    /**
     * By default, JetBrains Cache Redirector is used when resolving Maven repositories or any resources used by the IntelliJ Platform Gradle Plugin.
     * Due to limitations, sometimes it is desired to limit the list of remote endpoints accessed by Gradle.
     *
     * It is possible to refer to the direct location (whenever it is possible) by switching off JetBrains Cache Redirector globally.
     */
    USE_CACHE_REDIRECTOR(true),

    /**
     * Some dependencies are tied to IntelliJ Platform build numbers and hosted in the IntelliJ Dependencies Repository.
     * Despite this, certain versions (like EAP or nightly builds) might be absent.
     * To solve this, we fetch a list of all versions from the Maven repository and locate the closest match.
     * This method requires an additional remote repository request.
     * If undesired, this feature can be disabled to strictly match dependencies to your build version.
     */
    USE_CLOSEST_VERSION_RESOLVING(true),
    ;

    fun getValue(providers: ProviderFactory) = providers.gradleProperty(toString())
        .map { it.toBoolean() }
        .orElse(defaultValue)

    override fun toString() = name
        .lowercase()
        .split('_')
        .joinToString(
            separator = "",
            transform = { it.replaceFirstChar { c -> c.uppercase() } },
        )
        .replaceFirstChar { c -> c.lowercase() }
        .let { "${Plugin.ID}.buildFeature.$it" }

    /**
     * Checks if the specified build feature is enabled for the current project.
     *
     * @param providers Gradle [ProviderFactory] instance.
     * @return A provider containing the boolean value
     */
    fun isEnabled(providers: ProviderFactory) =
        getValue(providers).map { value ->
            value.also {
                val log = Logger(BuildFeature::class.java)
                when (value) {
                    true -> log.info("Build feature is enabled: $this")
                    false -> log.info("Build feature is disabled: $this")
                }
            }
        }
}
