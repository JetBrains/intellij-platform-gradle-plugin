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
import org.jetbrains.intellij.platform.gradle.extensions.aware.IntelliJPlatformPluginDependencyAware
import org.jetbrains.intellij.platform.gradle.extensions.aware.addIntelliJPlatformLocalPluginDependency
import org.jetbrains.intellij.platform.gradle.extensions.aware.addIntelliJPlatformLocalPluginProjectDependency
import org.jetbrains.intellij.platform.gradle.extensions.aware.addIntelliJPlatformPluginDependencies
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
    override val configurations: ConfigurationContainer,
    override val dependencies: DependencyHandler,
    override val providers: ProviderFactory,
    override val rootProjectDirectory: Path,
    private val intellijPlatformPluginDependencyConfigurationName: String,
    private val intellijPlatformPluginLocalConfigurationName: String,
    objects: ObjectFactory,
) : IntelliJPlatformPluginDependencyAware {

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
    fun plugin(id: String, version: String, channel: String = "") = addIntelliJPlatformPluginDependencies(
        pluginsProvider = providers.provider { listOf(Triple(id, version, channel)) },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param channel The provider of the plugin distribution channel.
     */
    fun plugin(id: Provider<String>, version: Provider<String>, channel: Provider<String>) = addIntelliJPlatformPluginDependencies(
        pluginsProvider = providers.provider { listOf(Triple(id.get(), version.get(), channel.get())) },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: Provider<String>) = addIntelliJPlatformPluginDependencies(
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
    fun plugin(notation: String) = addIntelliJPlatformPluginDependencies(
        pluginsProvider = providers.provider { listOfNotNull(notation.parsePluginNotation()) },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) = addIntelliJPlatformPluginDependencies(
        pluginsProvider = providers.provider { notations.mapNotNull { it.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) = addIntelliJPlatformPluginDependencies(
        pluginsProvider = providers.provider { notations.mapNotNull { it.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) = addIntelliJPlatformPluginDependencies(
        pluginsProvider = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName,
    )


    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: File) = addIntelliJPlatformLocalPluginDependency(
        localPathProvider = providers.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: String) = addIntelliJPlatformLocalPluginDependency(
        localPathProvider = providers.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Directory) = addIntelliJPlatformLocalPluginDependency(
        localPathProvider = providers.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Provider<*>) = addIntelliJPlatformLocalPluginDependency(
        localPathProvider = localPath,
        configurationName = intellijPlatformPluginLocalConfigurationName,
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param dependency Project dependency.
     */
    fun localPlugin(dependency: ProjectDependency) = addIntelliJPlatformLocalPluginProjectDependency(
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
