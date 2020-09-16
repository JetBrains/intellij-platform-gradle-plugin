package org.jetbrains.intellij.dependency

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.Utils

@CompileStatic
class MavenPluginsRepository implements PluginsRepository {
    private final Project project
    private final String repoUrl
    private boolean resolvedDependency = false

    MavenPluginsRepository(@NotNull Project project, @NotNull String repoUrl) {
        this.repoUrl = repoUrl
        this.project = project
    }

    @Nullable
    File resolve(@NotNull PluginDependencyNotation plugin) {
        def dependency = plugin.toDependency(project)

        Utils.debug(project, "Adding Maven repository to download $dependency - $repoUrl")
        def mavenRepo = project.repositories.maven { MavenArtifactRepository it -> it.url = repoUrl }

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
            project.repositories.maven { MavenArtifactRepository it -> it.url = repoUrl }
        }
    }
}
