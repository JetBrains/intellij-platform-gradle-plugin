// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.createIvyDependencyFile
import org.jetbrains.intellij.platform.gradle.extensions.localPlatformArtifactsPath
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.utils.asLenient
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

interface IntelliJPlatformPluginDependencyAware : DependencyAware, IntelliJPlatformAware {
    val providers: ProviderFactory
    val rootProjectDirectory: Path
}

internal val IntelliJPlatformPluginDependencyAware.bundledPluginsList
    get() = configurations[Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST].asLenient.bundledPlugins()

private val pluginManager by lazy {
    IdePluginManager.createManager()
}

/**
 * A base method for adding a dependency on a plugin for IntelliJ Platform.
 *
 * @param pluginsProvider The provider of the list containing triples with plugin identifier, version, and channel.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun IntelliJPlatformPluginDependencyAware.addIntelliJPlatformPluginDependencies(
    pluginsProvider: Provider<List<Triple<String, String, String>>>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addAllLater(
    pluginsProvider.map { plugins ->
        plugins.map { (id, version, channel) -> createIntelliJPlatformPluginDependency(id, version, channel).apply(action) }
    }
)

/**
 * A base method for adding a dependency on a plugin for IntelliJ Platform.
 *
 * @param bundledPluginsProvider The provider of the list containing triples with plugin identifier, version, and channel.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun IntelliJPlatformPluginDependencyAware.addIntelliJPlatformBundledPluginDependencies(
    bundledPluginsProvider: Provider<List<String>>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addAllLater(
    bundledPluginsProvider.map { bundledPlugins ->
        bundledPlugins
            .filter { id -> id.isNotBlank() }
            .map { id -> createIntelliJPlatformBundledPluginDependency(id).apply(action) }
    }
)

/**
 * A base method for adding a dependency on a local plugin for IntelliJ Platform.
 *
 * @param localPathProvider The provider of the path to the local plugin.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun IntelliJPlatformPluginDependencyAware.addIntelliJPlatformLocalPluginDependency(
    localPathProvider: Provider<*>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addLater(
    localPathProvider.map { localPath ->
        val artifactPath = when (localPath) {
            is String -> localPath
            is File -> localPath.absolutePath
            is Directory -> localPath.asPath.pathString
            else -> throw IllegalArgumentException("Invalid argument type: '${localPath.javaClass}'. Supported types: String, File, or Directory.")
        }
            .let { Path(it) }
            .takeIf { it.exists() }
            .let { requireNotNull(it) { "Specified localPath '$localPath' doesn't exist." } }

        val plugin by lazy {
            val pluginPath = when {
                artifactPath.isDirectory() -> generateSequence(artifactPath) {
                    it.takeIf { it.resolve("lib").exists() } ?: it.listDirectoryEntries().singleOrNull()
                }.firstOrNull { it.resolve("lib").exists() } ?: throw GradleException("Could not resolve plugin directory: $artifactPath")

                else -> artifactPath
            }

            val pluginCreationResult = pluginManager.createPlugin(pluginPath, false)
            require(pluginCreationResult is PluginCreationSuccess)
            pluginCreationResult.plugin
        }

        dependencies.create(
            group = Configurations.Dependencies.LOCAL_PLUGIN_GROUP,
            name = plugin.pluginId ?: artifactPath.name,
            version = plugin.pluginVersion ?: "0.0.0",
        ).apply {
            createIvyDependencyFile(
                localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                publications = listOf(artifactPath.toPublication()),
            )
        }.apply(action)
    }
)

/**
 * A base method for adding a project dependency on a local plugin for IntelliJ Platform.
 *
 * @param dependency The plugin project dependency.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun IntelliJPlatformPluginDependencyAware.addIntelliJPlatformLocalPluginProjectDependency(
    dependency: ProjectDependency,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_LOCAL,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.add(dependency.apply(action))

/**
 * Creates a dependency for an IntelliJ platform plugin.
 *
 * @param id the ID of the plugin
 * @param version the version of the plugin
 * @param channel the channel of the plugin. Can be null or empty for the default channel.
 */
private fun IntelliJPlatformPluginDependencyAware.createIntelliJPlatformPluginDependency(id: String, version: String, channel: String?): Dependency {
    val group = when (channel) {
        "default", "", null -> JETBRAINS_MARKETPLACE_MAVEN_GROUP
        else -> "$channel.$JETBRAINS_MARKETPLACE_MAVEN_GROUP"
    }

    return dependencies.create(
        group = group,
        name = id.trim(),
        version = version,
    )
}

/**
 * Creates a dependency for an IntelliJ platform bundled plugin.
 *
 * @param bundledPluginId The ID of the bundled plugin.
 * @throws IllegalArgumentException
 */
@Throws(IllegalArgumentException::class)
private fun IntelliJPlatformPluginDependencyAware.createIntelliJPlatformBundledPluginDependency(bundledPluginId: String): Dependency {
    val plugin = bundledPluginsList.plugins.find { it.id == bundledPluginId.trim() }
    requireNotNull(plugin) { "Could not find bundled plugin with ID: '$bundledPluginId'" }

    return dependencies.create(
        group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
        name = plugin.id,
        version = productInfo.version,
    ).apply {
        createBundledPluginIvyDependencyFile(plugin, version!!)
    }
}

private fun IntelliJPlatformPluginDependencyAware.createBundledPluginIvyDependencyFile(
    bundledPlugin: BundledPlugin,
    version: String,
    resolved: List<String> = emptyList(),
) {
    val localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory)
    val group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP
    val name = bundledPlugin.id
    val existingIvyFile = localPlatformArtifactsPath.resolve("$group-$name-$version.xml")
    if (existingIvyFile.exists()) {
        return
    }

    if (name in resolved) {
        return
    }

    val artifactPath = Path(bundledPlugin.path)
    val plugin by lazy {
        val pluginCreationResult = pluginManager.createPlugin(artifactPath, false)
        require(pluginCreationResult is PluginCreationSuccess)
        pluginCreationResult.plugin
    }

    val dependencyIds = plugin.dependencies.map { it.id } - plugin.pluginId
    val bundledModules = productInfo.layout
        .filter { layout -> layout.name in dependencyIds }
        .filter { layout -> layout.classPath.isNotEmpty() }

    val ivyDependencies = bundledModules.mapNotNull { layout ->
        when (layout.kind) {
            ProductInfo.LayoutItemKind.plugin -> {
                IvyModule.Dependency(
                    organization = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
                    name = layout.name,
                    version = version,
                ).also { dependency ->
                    val dependencyPlugin = bundledPluginsList.plugins.find { it.id == dependency.name } ?: return@also
                    createBundledPluginIvyDependencyFile(dependencyPlugin, version, resolved + name)
                }
            }

            ProductInfo.LayoutItemKind.pluginAlias -> {
                // TODO: not important?
                null
            }

            ProductInfo.LayoutItemKind.moduleV2, ProductInfo.LayoutItemKind.productModuleV2 -> {
                // TODO: drop if classPath empty?
                IvyModule.Dependency(
                    organization = Configurations.Dependencies.BUNDLED_MODULE_GROUP,
                    name = layout.name,
                    version = version,
                ).also { dependency ->
                    createIvyDependencyFile(
                        group = dependency.organization,
                        name = dependency.name,
                        version = dependency.version,
                        localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
                        publications = layout.classPath.map { classPath ->
                            platformPath.resolve(classPath).toPublication()
                        },
                    )
                }
            }
        }
    }

    createIvyDependencyFile(
        group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
        name = bundledPlugin.id,
        version = version,
        localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
        publications = listOf(artifactPath.toPublication()),
        dependencies = ivyDependencies,
    )
}
