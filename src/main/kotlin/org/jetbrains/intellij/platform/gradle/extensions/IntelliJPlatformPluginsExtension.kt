// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.setProperty
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.extensions.helpers.IntelliJPlatformHelper
import org.jetbrains.intellij.platform.gradle.extensions.helpers.IntelliJPlatformPluginDependencyHelper
import org.jetbrains.intellij.platform.gradle.extensions.helpers.ProvidersHelper
import java.io.File
import java.nio.file.Path
import javax.inject.Inject

/**
 * @param configurations The Gradle [ConfigurationContainer] to manage configurations.
 * @param dependencies The Gradle [DependencyHandler] to manage dependencies.
 * @param providers The Gradle [ProviderFactory] to create providers.
 * @param rootProjectDirectory The root project directory location.
 */
@IntelliJPlatform
abstract class IntelliJPlatformPluginsExtension @Inject constructor(
    configurations: ConfigurationContainer,
    dependencies: DependencyHandler,
    providers: ProviderFactory,
    rootProjectDirectory: Path,
    private val intellijPlatformPluginDependencyConfigurationName: String,
    private val intellijPlatformPluginLocalConfigurationName: String,
    objects: ObjectFactory,
) {

    private val providersDelegate = ProvidersHelper(providers)

    private val intelliJPlatformDelegate = IntelliJPlatformHelper(
        configurations,
        providers,
    )

    private val intelliJPlatformPluginDependencyDelegate = IntelliJPlatformPluginDependencyHelper(
        configurations,
        dependencies,
        intelliJPlatformDelegate.platformPath,
        intelliJPlatformDelegate.productInfo,
        providers,
        rootProjectDirectory
    )

    /**
     * Contains a list of plugins to be disabled.
     */
    internal val disabled = objects.setProperty(String::class)

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The plugin identifier.
     * @param version The plugin version.
     * @param channel The plugin distribution channel.
     */
    fun plugin(id: String, version: String, channel: String = "") = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = providersDelegate.of { listOf(Triple(id, version, channel)) },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param channel The provider of the plugin distribution channel.
     */
    fun plugin(id: Provider<String>, version: Provider<String>, channel: Provider<String>) =
        intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
            pluginsProvider = providersDelegate.of { listOf(Triple(id.get(), version.get(), channel.get())) },
            configurationName = intellijPlatformPluginDependencyConfigurationName,
        )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: Provider<String>) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = notation.map { listOfNotNull(it.parsePluginNotation()) },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: String) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = providersDelegate.of { listOfNotNull(notation.parsePluginNotation()) },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = providersDelegate.of { notations.mapNotNull { it.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = providersDelegate.of { notations.mapNotNull { it.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )


    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: File) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = providersDelegate.of { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: String) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = providersDelegate.of { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Directory) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = providersDelegate.of { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Provider<*>) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = localPath,
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param dependency Project dependency.
     */
    fun localPlugin(dependency: ProjectDependency) = intelliJPlatformPluginDependencyDelegate.addIntelliJPlatformLocalPluginProjectDependency(
        dependency = dependency,
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Disables plugin with a specific [id].
     *
     * @param id The plugin identifier.
     */
    fun disablePlugin(id: String) = disabled.add(id)

    /**
     * Disables plugin with a specific [id].
     *
     * @param id The plugin identifier.
     */
    fun disablePlugin(id: Provider<String>) = disabled.add(id)

    /**
     * Disables plugins with a specific [ids].
     *
     * @param ids Plugin identifiers.
     */
    fun disablePlugins(ids: List<String>) = disabled.addAll(ids)

    /**
     * Disables plugins with a specific [ids].
     *
     * @param ids Plugin identifiers.
     */
    fun disablePlugins(ids: Provider<List<String>>) = disabled.addAll(ids)

    /**
     * Disables plugins with a specific [ids].
     *
     * @param ids Plugin identifiers.
     */
    fun disablePlugins(vararg ids: String) = disabled.addAll(*ids)
}
