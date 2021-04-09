package org.jetbrains.intellij.dependency

import okhttp3.internal.toImmutableList
import org.gradle.api.Project
import org.jetbrains.intellij.IntelliJPluginConstants

open class PluginsRepoConfigurationImpl(val project: Project) : PluginsRepositoryConfiguration {

    private val pluginsRepositories = mutableListOf<PluginsRepository>()

    override fun marketplace() {
        pluginsRepositories.add(MavenPluginsRepository(project, IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPO))
    }

    override fun maven(url: String) {
        pluginsRepositories.add(MavenPluginsRepository(project, url))
    }

    override fun custom(url: String) {
        pluginsRepositories.add(CustomPluginsRepository(project, url))
    }

    override fun getRepositories() = pluginsRepositories.toImmutableList()
}
