// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.Directory
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.jetbrains.intellij.platform.gradle.Constants.Constraints
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import java.io.File
import javax.inject.Inject

/**
 * @param objects Gradle [ObjectFactory] instance
 * @param dependenciesHelper IntelliJ Platform dependencies helper instance
 */
@IntelliJPlatform
abstract class IntelliJPlatformPluginsExtension @Inject constructor(
    private val dependenciesHelper: IntelliJPlatformDependenciesHelper,
    objects: ObjectFactory,
) : ExtensionAware {

    internal val intellijPlatformPluginDependencyConfigurationName = objects.property<String>()
    internal val intellijPlatformPluginLocalConfigurationName = objects.property<String>()

    /**
     * Contains a list of plugins to be disabled.
     */
    internal val disabled = objects.setProperty<String>()

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The plugin identifier.
     * @param version The plugin version.
     * @param channel The plugin distribution channel.
     */
    fun plugin(id: String, version: String, channel: String = "") = dependenciesHelper.addIntelliJPlatformPluginDependencies(
        pluginsProvider = dependenciesHelper.provider { listOf(Triple(id, version, channel)) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param channel The provider of the plugin distribution channel.
     */
    fun plugin(id: Provider<String>, version: Provider<String>, channel: Provider<String>) = dependenciesHelper.addIntelliJPlatformPluginDependencies(
        pluginsProvider = dependenciesHelper.provider { listOf(Triple(id.get(), version.get(), channel.get())) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: Provider<String>) = dependenciesHelper.addIntelliJPlatformPluginDependencies(
        pluginsProvider = notation.map { listOfNotNull(it.parsePluginNotation()) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: String) = dependenciesHelper.addIntelliJPlatformPluginDependencies(
        pluginsProvider = dependenciesHelper.provider { listOfNotNull(notation.parsePluginNotation()) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) = dependenciesHelper.addIntelliJPlatformPluginDependencies(
        pluginsProvider = dependenciesHelper.provider { notations.mapNotNull { it.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) = dependenciesHelper.addIntelliJPlatformPluginDependencies(
        pluginsProvider = dependenciesHelper.provider { notations.mapNotNull { it.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) = dependenciesHelper.addIntelliJPlatformPluginDependencies(
        pluginsProvider = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: File) = dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = dependenciesHelper.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: String) = dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = dependenciesHelper.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Directory) = dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = dependenciesHelper.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Provider<*>) = dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = localPath,
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param dependency Project dependency.
     */
    fun localPlugin(dependency: ProjectDependency) = dependenciesHelper.addIntelliJPlatformLocalPluginProjectDependency(
        dependency = dependency,
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
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

    fun robotServerPlugin(version: String = Constraints.LATEST_VERSION) = dependenciesHelper.addRobotServerPluginDependency(
        versionProvider = dependenciesHelper.provider { version },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    fun robotServerPlugin(version: Provider<String>) = dependenciesHelper.addRobotServerPluginDependency(
        versionProvider = version,
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    companion object {
        fun register(dependenciesHelper: IntelliJPlatformDependenciesHelper, objects: ObjectFactory, target: Any) =
            target.configureExtension<IntelliJPlatformPluginsExtension>(
                Extensions.PLUGINS,
                dependenciesHelper,
                objects,
            )
    }
}
