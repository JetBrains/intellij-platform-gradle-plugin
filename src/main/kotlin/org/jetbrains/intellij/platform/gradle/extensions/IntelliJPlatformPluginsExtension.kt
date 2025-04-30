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
@Suppress("unused")
@IntelliJPlatform
abstract class IntelliJPlatformPluginsExtension @Inject constructor(
    private val dependenciesHelper: IntelliJPlatformDependenciesHelper,
    objects: ObjectFactory,
) : ExtensionAware {

    internal val intellijPlatformConfigurationName = objects.property<String>()
    internal val intellijPlatformPluginDependencyConfigurationName = objects.property<String>()
    internal val intellijPlatformPluginLocalConfigurationName = objects.property<String>()
    internal val intellijPlatformTestBundledPluginsConfiguration = objects.property<String>()
    internal val intellijPlatformTestBundledModulesConfiguration = objects.property<String>()

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
     * Adds a dependency on a plugin in a version compatible with the current IntelliJ Platform.
     *
     * @param id The plugin identifier.
     */
    fun compatiblePlugin(id: String) = dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
        pluginsProvider = dependenciesHelper.provider { listOf(id) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on a plugin in a version compatible with the current IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     */
    fun compatiblePlugin(id: Provider<String>) = dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
        pluginsProvider = id.map { listOf(it) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on plugins in versions compatible with the current IntelliJ Platform.
     *
     * @param ids The plugin identifiers.
     */
    fun compatiblePlugins(ids: List<String>) = dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
        pluginsProvider = dependenciesHelper.provider { ids },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on plugins in versions compatible with the current IntelliJ Platform.
     *
     * @param ids The plugin identifiers.
     */
    fun compatiblePlugins(vararg ids: String) = dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
        pluginsProvider = dependenciesHelper.provider { ids.asList() },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on plugins in versions compatible with the current IntelliJ Platform.
     *
     * @param ids The provider of the plugin identifiers.
     */
    fun compatiblePlugins(ids: Provider<List<String>>) = dependenciesHelper.addCompatibleIntelliJPlatformPluginDependencies(
        pluginsProvider = ids.map { it },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on a bundled IntelliJ Platform plugin.
     *
     * @param id The provider of the bundled plugin identifier.
     */
    fun bundledPlugin(id: Provider<String>) = dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = id.map { listOf(it) },
        configurationName = intellijPlatformTestBundledPluginsConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(vararg ids: String) = dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = dependenciesHelper.provider { ids.asList() },
        configurationName = intellijPlatformTestBundledPluginsConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: List<String>) = dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = dependenciesHelper.provider { ids },
        configurationName = intellijPlatformTestBundledPluginsConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform plugins.
     *
     * @param ids The bundled plugin identifiers.
     */
    fun bundledPlugins(ids: Provider<List<String>>) = dependenciesHelper.addIntelliJPlatformBundledPluginDependencies(
        bundledPluginsProvider = ids,
        configurationName = intellijPlatformTestBundledPluginsConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on a bundled IntelliJ Platform module.
     *
     * @param id The bundled module identifier.
     */
    fun bundledModule(id: String) = dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
        bundledModulesProvider = dependenciesHelper.provider { listOf(id) },
        configurationName = intellijPlatformTestBundledModulesConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on a bundled IntelliJ Platform module.
     *
     * @param id The provider of the bundled module identifier.
     */
    fun bundledModule(id: Provider<String>) = dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
        bundledModulesProvider = id.map { listOf(it) },
        configurationName = intellijPlatformTestBundledModulesConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun bundledModules(vararg ids: String) = dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
        bundledModulesProvider = dependenciesHelper.provider { ids.asList() },
        configurationName = intellijPlatformTestBundledModulesConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun bundledModules(ids: List<String>) = dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
        bundledModulesProvider = dependenciesHelper.provider { ids },
        configurationName = intellijPlatformTestBundledModulesConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds dependencies on bundled IntelliJ Platform modules.
     *
     * @param ids The bundled module identifiers.
     */
    fun bundledModules(ids: Provider<List<String>>) = dependenciesHelper.addIntelliJPlatformBundledModuleDependencies(
        bundledModulesProvider = ids,
        configurationName = intellijPlatformTestBundledModulesConfiguration.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: File) = dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = dependenciesHelper.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: String) = dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = dependenciesHelper.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Directory) = dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = dependenciesHelper.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Provider<*>) = dependenciesHelper.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = localPath,
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
        intellijPlatformConfigurationName = intellijPlatformConfigurationName.get(),
    )

    /**
     * Adds a dependency on a local IntelliJ Platform plugin.
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
