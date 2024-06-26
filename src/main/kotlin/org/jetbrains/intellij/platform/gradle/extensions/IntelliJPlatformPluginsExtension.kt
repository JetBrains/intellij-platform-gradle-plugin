// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.file.Directory
import org.gradle.api.file.ProjectLayout
import org.gradle.api.model.ObjectFactory
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.resources.ResourceHandler
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.jetbrains.intellij.platform.gradle.Constants.Extensions
import org.jetbrains.intellij.platform.gradle.Constants.VERSION_LATEST
import org.jetbrains.intellij.platform.gradle.IntelliJPlatform
import org.jetbrains.intellij.platform.gradle.plugins.configureExtension
import org.jetbrains.intellij.platform.gradle.utils.rootProjectPath
import org.jetbrains.intellij.platform.gradle.utils.settings
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
    layout: ProjectLayout,
    objects: ObjectFactory,
    providers: ProviderFactory,
    repositories: RepositoryHandler,
    resources: ResourceHandler,
    rootProjectDirectory: Path,
    settingsRepositories: RepositoryHandler,
) : ExtensionAware {

    internal val intellijPlatformPluginDependencyConfigurationName = objects.property<String>()
    internal val intellijPlatformPluginLocalConfigurationName = objects.property<String>()

    private val delegate = IntelliJPlatformDependenciesHelper(
        configurations,
        dependencies,
        layout,
        objects,
        providers,
        repositories,
        resources,
        rootProjectDirectory,
        settingsRepositories,
    )

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
    fun plugin(id: String, version: String, channel: String = "") = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { listOf(Triple(id, version, channel)) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform.
     *
     * @param id The provider of the plugin identifier.
     * @param version The provider of the plugin version.
     * @param channel The provider of the plugin distribution channel.
     */
    fun plugin(id: Provider<String>, version: Provider<String>, channel: Provider<String>) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { listOf(Triple(id.get(), version.get(), channel.get())) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds a dependency on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notation The plugin notation in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugin(notation: Provider<String>) = delegate.addIntelliJPlatformPluginDependencies(
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
    fun plugin(notation: String) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { listOfNotNull(notation.parsePluginNotation()) },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(vararg notations: String) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { notations.mapNotNull { it.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: List<String>) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = delegate.provider { notations.mapNotNull { it.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds dependencies on a plugin for IntelliJ Platform using a string notation, in the following formats:
     * - `pluginId:version`
     * - `pluginId:version@channel`
     *
     * @param notations The plugin notations list in `pluginId:version` or `pluginId:version@channel` format.
     */
    fun plugins(notations: Provider<List<String>>) = delegate.addIntelliJPlatformPluginDependencies(
        pluginsProvider = notations.map { it.mapNotNull { notation -> notation.parsePluginNotation() } },
        configurationName = intellijPlatformPluginDependencyConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: File) = delegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = delegate.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: String) = delegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = delegate.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Directory) = delegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = delegate.provider { localPath },
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param localPath Path to the local plugin.
     */
    fun localPlugin(localPath: Provider<*>) = delegate.addIntelliJPlatformLocalPluginDependency(
        localPathProvider = localPath,
        configurationName = intellijPlatformPluginLocalConfigurationName.get(),
    )

    /**
     * Adds dependency on a local IntelliJ Platform plugin.
     *
     * @param dependency Project dependency.
     */
    fun localPlugin(dependency: ProjectDependency) = delegate.addIntelliJPlatformLocalPluginProjectDependency(
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

    fun robotServerPlugin(version: Provider<String>) = delegate.addRobotServerPluginDependency(
        versionProvider = version,
    )

    fun robotServerPlugin(version: String = VERSION_LATEST) = delegate.addRobotServerPluginDependency(
        versionProvider = delegate.provider { version },
    )

    @Suppress("UnstableApiUsage")
    companion object : Registrable<IntelliJPlatformPluginsExtension> {
        override fun register(project: Project, target: Any) =
            target.configureExtension<IntelliJPlatformPluginsExtension>(
                Extensions.PLUGINS,
                project.configurations,
                project.dependencies,
                project.layout,
                project.objects,
                project.providers,
                project.repositories,
                project.resources,
                project.rootProjectPath,
                project.settings.dependencyResolutionManagement.repositories,
            )
    }
}
