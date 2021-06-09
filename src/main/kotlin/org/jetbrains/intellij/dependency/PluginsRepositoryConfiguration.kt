package org.jetbrains.intellij.dependency

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.jetbrains.intellij.IntelliJPluginConstants

abstract class PluginsRepositoryConfiguration {

    private val pluginsRepositories = mutableListOf<PluginsRepository>()

    /**
     * Use default marketplace repository
     */
    fun marketplace() {
        pluginsRepositories.add(MavenRepositoryPlugin(IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPOSITORY))
    }

    /**
     * Use a Maven repository with plugin artifacts
     */
    fun maven(url: String) {
        pluginsRepositories.add(MavenRepositoryPlugin(url))
    }

    /**
     * Use a Maven repository by action
     */
    fun maven(action: Action<in MavenArtifactRepository>) {
        pluginsRepositories.add(MavenRepositoryPluginByAction(action))
    }

    /**
     * Use custom plugin repository. The URL should point to the `plugins.xml` or `updatePlugins.xml` file.
     */
    @Suppress("unused")
    fun custom(url: String) {
        pluginsRepositories.add(CustomPluginsRepository(url))
    }

    fun getRepositories() = pluginsRepositories.toList()
}
