package org.jetbrains.intellij.dependency

import groovy.transform.CompileStatic
import org.gradle.api.Action
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.repositories.MavenArtifactRepository
import org.jetbrains.intellij.debug
import java.io.File
import java.net.URI

interface MavenRepository : PluginsRepository {

    var resolvedDependency: Boolean

    fun getPluginFile(project: Project, dependency: Dependency, repository: MavenArtifactRepository, url: String): File? {
        debug(project, "Adding Maven repository to download $dependency - $url")
        var pluginFile: File? = null
        try {
            val configuration = project.configurations.detachedConfiguration(dependency)
            pluginFile = configuration.singleFile
            resolvedDependency = true
        } catch (e: Exception) {
            debug(project, "Couldn't find $dependency in $url", e)
        }
        debug(project, "Removing Maven repository $url")
        project.repositories.remove(repository)
        return pluginFile
    }

    fun postResolve(project: Project, func: () -> Unit) {
        if (resolvedDependency) {
            return func.invoke()
        }
    }
}

@CompileStatic
class MavenRepositoryPluginByAction(private val maven: Action<in MavenArtifactRepository>) : MavenRepository {

    override var resolvedDependency = false

    override fun resolve(project: Project, plugin: PluginDependencyNotation): File? {
        val dependency = plugin.toDependency(project)
        val repository = project.repositories.maven(maven)
        return getPluginFile(project, dependency, repository, repository.url.toString())
    }

    override fun postResolve(project: Project) =
        postResolve(project) {
            val repository = project.repositories.maven(maven)
            debug(project, "Adding Maven plugins repository ${repository.url}")
            project.repositories.maven(maven)
        }
}

@CompileStatic
class MavenRepositoryPlugin(private val repositoryUrl: String) : MavenRepository {

    override var resolvedDependency = false

    override fun resolve(project: Project, plugin: PluginDependencyNotation): File? {
        val dependency = plugin.toDependency(project)
        val mavenRepository = project.repositories.maven { it.url = URI.create(repositoryUrl) }
        return getPluginFile(project, dependency, mavenRepository, repositoryUrl)
    }

    override fun postResolve(project: Project) =
        postResolve(project) {
            debug(project, "Adding Maven plugins repository $repositoryUrl")
            project.repositories.maven { it.url = URI.create(repositoryUrl) }
        }
}
