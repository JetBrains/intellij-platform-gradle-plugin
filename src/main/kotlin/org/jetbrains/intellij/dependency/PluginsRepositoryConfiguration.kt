// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import org.gradle.api.Action
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.gradle.api.model.ObjectFactory
import org.jetbrains.intellij.IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPOSITORY
import org.jetbrains.intellij.utils.DependenciesDownloader
import javax.inject.Inject

abstract class PluginsRepositoryConfiguration @Inject constructor(
    private val objects: ObjectFactory,
    private val dependenciesDownloader: DependenciesDownloader,
) {

    private val pluginsRepositories = mutableListOf<PluginsRepository>()

    /**
     * Use default marketplace repository.
     */
    fun marketplace() {
        pluginsRepositories.add(MavenRepositoryPlugin(DEFAULT_INTELLIJ_PLUGINS_REPOSITORY, dependenciesDownloader))
    }

    /**
     * Use a Maven repository with plugin artifacts.
     */
    fun maven(url: String) {
        pluginsRepositories.add(MavenRepositoryPlugin(url, dependenciesDownloader))
    }

    /**
     * Use a Maven repository by action.
     */
    fun maven(action: Action<in MavenArtifactRepository>) {
        pluginsRepositories.add(MavenRepositoryPluginByAction(action, dependenciesDownloader))
    }

    /**
     * Use custom plugin repository. The URL should point to the `plugins.xml` or `updatePlugins.xml` file.
     */
    fun custom(url: String) {
        pluginsRepositories.add(newCustomPluginRepository { this.url.set(url) })
    }

    /**
     * Use a custom plugin repository by action.
     */
    fun custom(action: Action<in CustomPluginsRepository.Configuration>) {
        pluginsRepositories.add(newCustomPluginRepository(action))
    }

    private fun newCustomPluginRepository(
        configure: Action<in CustomPluginsRepository.Configuration>
    ) : CustomPluginsRepository {
        val config = objects.newInstance(CustomPluginsRepository.Configuration::class.java)
        configure.execute(config)
        return objects.newInstance(CustomPluginsRepository::class.java, config, dependenciesDownloader)
    }

    fun getRepositories() = pluginsRepositories.toList()
}
