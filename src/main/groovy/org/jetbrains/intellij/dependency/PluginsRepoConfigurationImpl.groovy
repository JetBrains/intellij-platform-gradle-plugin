package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension

class PluginsRepoConfigurationImpl implements IntelliJPluginExtension.PluginsRepoConfiguration {

    private List<PluginsRepository> pluginsRepositories = new ArrayList<>()
    private Project project

    PluginsRepoConfigurationImpl(Project project) {
        this.project = project
    }

    @Override
    void marketplace() {
        pluginsRepositories.add(new MavenPluginsRepository(project, IntelliJPlugin.DEFAULT_INTELLIJ_PLUGINS_REPO))
    }

    @Override
    void maven(@NotNull String url) {
        pluginsRepositories.add(new MavenPluginsRepository(project, url))
    }

    @Override
    void custom(@NotNull String url) {
        pluginsRepositories.add(new CustomPluginsRepository(project, url))
    }

    @Override
    List<PluginsRepository> getRepositories() {
        return Collections.unmodifiableList(pluginsRepositories)
    }
}
