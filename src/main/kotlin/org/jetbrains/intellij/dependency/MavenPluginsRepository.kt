package org.jetbrains.intellij.dependency

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.jetbrains.intellij.debug
import java.io.File
import java.net.URI

@CompileStatic
class MavenPluginsRepository(private val repositoryUrl: String) : PluginsRepository {

    private var resolvedDependency = false

    override fun resolve(project: Project, plugin: PluginDependencyNotation): File? {
        val dependency = plugin.toDependency(project)

        debug(project, "Adding Maven repository to download $dependency - $repositoryUrl")
        val mavenRepository = project.repositories.maven { it.url = URI.create(repositoryUrl) }

        var pluginFile: File? = null
        try {
            val configuration = project.configurations.detachedConfiguration(dependency)
            pluginFile = configuration.singleFile
            resolvedDependency = true
        } catch (e: Exception) {
            debug(project, "Couldn't find $dependency in $repositoryUrl", e)
        }

        debug(project, "Removing Maven repository $repositoryUrl")
        project.repositories.remove(mavenRepository)

        return pluginFile
    }

    override fun postResolve(project: Project) {
        if (resolvedDependency) {
            debug(project, "Adding Maven plugins repository $repositoryUrl")
            project.repositories.maven { it.url = URI.create(repositoryUrl) }
        }
    }
}
