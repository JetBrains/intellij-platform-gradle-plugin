// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.api.artifacts.Dependency
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.extensions.DependencyAction
import org.jetbrains.intellij.platform.gradle.extensions.createIvyDependencyFile
import org.jetbrains.intellij.platform.gradle.extensions.localPlatformArtifactsPath
import org.jetbrains.intellij.platform.gradle.models.bundledPlugins
import org.jetbrains.intellij.platform.gradle.models.toPublication
import org.jetbrains.intellij.platform.gradle.utils.asLenient
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.math.absoluteValue

interface IntelliJPlatformPluginDependencyAware: DependencyAware, IntelliJPlatformAware {
    val providers: ProviderFactory
    val rootProjectDirectory: Path
}

/**
 * A base method for adding a dependency on a plugin for IntelliJ Platform.
 *
 * @param plugins The provider of the list containing triples with plugin identifier, version, and channel.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun IntelliJPlatformPluginDependencyAware.addIntelliJPlatformPluginDependencies(
    plugins: Provider<List<Triple<String, String, String>>>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_PLUGIN_DEPENDENCY,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addAllLater(
    plugins.map {
        it.map { (id, version, channel) -> createIntelliJPlatformPluginDependency(id, version, channel).apply(action) }
    }
)

/**
 * A base method for adding a dependency on a plugin for IntelliJ Platform.
 *
 * @param bundledPlugins The provider of the list containing triples with plugin identifier, version, and channel.
 * @param configurationName The name of the configuration to add the dependency to.
 * @param action The action to be performed on the dependency. Defaults to an empty action.
 */
internal fun IntelliJPlatformPluginDependencyAware.addIntelliJPlatformBundledPluginDependencies(
    bundledPlugins: Provider<List<String>>,
    configurationName: String = Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS,
    action: DependencyAction = {},
) = configurations[configurationName].dependencies.addAllLater(
    bundledPlugins.map {
        it
            .filter { id -> id.isNotBlank() }
            .map { id -> createIntelliJPlatformBundledPluginDependency(id).apply(action) }
    }
)


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
    val id = bundledPluginId.trim()
    val bundledPluginsList = configurations[Configurations.INTELLIJ_PLATFORM_BUNDLED_PLUGINS_LIST].asLenient.single().toPath().bundledPlugins()
    val bundledPlugin = bundledPluginsList.plugins.find { it.id == id }
    requireNotNull(bundledPlugin) { "Could not find bundled plugin with ID: '$id'" }

    val artifactPath = Path(bundledPlugin.path)
    val jars = artifactPath.resolve("lib").listDirectoryEntries("*.jar")
    val hash = artifactPath.hashCode().absoluteValue % 1000

    return dependencies.create(
        group = Configurations.Dependencies.BUNDLED_PLUGIN_GROUP,
        name = id,
        version = "${productInfo.version}+$hash",
    ).apply {
        createIvyDependencyFile(
            localPlatformArtifactsPath = providers.localPlatformArtifactsPath(rootProjectDirectory),
            publications = jars.map { it.toPublication() },
        )
    }
}
