// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.createIntelliJPlatformBundledPluginDependency
import org.jetbrains.intellij.platform.gradle.extensions.createIntelliJPlatformLocalPluginDependency
import org.jetbrains.intellij.platform.gradle.extensions.createIntelliJPlatformPluginDependency
import org.jetbrains.intellij.platform.gradle.models.bundledPlugins
import org.jetbrains.intellij.platform.gradle.utils.asLenient
import java.nio.file.Path

interface IntelliJPlatformPluginDependencyAware : DependencyAware, IntelliJPlatformAware {
    val providers: ProviderFactory
    val rootProjectDirectory: Path
}

internal val IntelliJPlatformPluginDependencyAware.bundledPluginsList
    get() = configurations[Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST].asLenient.bundledPlugins()

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
        plugins.map { (id, version, channel) ->
            dependencies
                .createIntelliJPlatformPluginDependency(id, version, channel)
                .apply(action)
        }
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
            .map { id ->
                dependencies
                    .createIntelliJPlatformBundledPluginDependency(id, bundledPluginsList, platformPath, productInfo, providers, rootProjectDirectory)
                    .apply(action)
            }
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
        dependencies
            .createIntelliJPlatformLocalPluginDependency(localPath, providers, rootProjectDirectory)
            .apply(action)
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
