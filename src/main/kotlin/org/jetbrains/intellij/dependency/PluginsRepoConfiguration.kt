package org.jetbrains.intellij.dependency

import org.jetbrains.intellij.IntelliJPluginConstants

abstract class PluginsRepoConfiguration {

    private val pluginsRepositories = mutableListOf<PluginsRepository>()

    /**
     * Use default marketplace repository
     */
    fun marketplace() {
        pluginsRepositories.add(MavenPluginsRepository(IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPO))
    }

    /**
     * Use a Maven repository with plugin artifacts
     */
    fun maven(url: String) {
        pluginsRepositories.add(MavenPluginsRepository(url))
    }

    /**
     * Use custom plugin repository. The URL should point to the `plugins.xml` or `updatePlugins.xml` file.
     */
    fun custom(url: String) {
        pluginsRepositories.add(CustomPluginsRepository(url))
    }

    fun getRepositories() = pluginsRepositories.toList()
}
