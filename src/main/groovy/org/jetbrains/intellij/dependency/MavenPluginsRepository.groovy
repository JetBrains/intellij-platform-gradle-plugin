package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.Utils

class MavenPluginsRepository implements PluginsRepository {

    private final Project project
    private final String repoUrl
    private boolean resolvedDependency = false

    MavenPluginsRepository(@NotNull Project project, @NotNull String repoUrl) {
        this.repoUrl = repoUrl
        this.project = project
    }

    @Nullable
    File resolve(@NotNull String id, @NotNull String version, @Nullable String channel) {
        def dependency = project.dependencies.create(PluginDependencyManager.pluginDependency(id, version, channel))

        Utils.debug(project, "Adding Maven repository to download $dependency - $repoUrl")
        def mavenRepo = project.repositories.maven { it.url = repoUrl }

        def pluginFile = null
        try {
            def configuration = project.configurations.detachedConfiguration(dependency)
            pluginFile = configuration.singleFile
            resolvedDependency = true
        } catch (Exception e) {
            Utils.debug(project, "Couldn't find " + dependency + " in $repoUrl", e)
        }

        Utils.debug(project, "Removing Maven repository $repoUrl")
        project.repositories.remove(mavenRepo)

        return pluginFile
    }

    @Override
    void postResolve() {
        if (resolvedDependency) {
            Utils.debug(project, "Adding Maven plugins repository $repoUrl")
            project.repositories.maven { it.url = repoUrl }
        }
    }
}
