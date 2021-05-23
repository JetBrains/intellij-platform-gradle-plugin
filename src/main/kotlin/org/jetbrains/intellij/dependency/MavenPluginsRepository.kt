package org.jetbrains.intellij.dependency

import groovy.lang.Closure
import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.gradle.util.ConfigureUtil
import org.jetbrains.intellij.debug
import java.io.File
import java.net.URI

@CompileStatic
class MavenPluginsRepository(private val repositoryUrl: String?, private val mavenClosure: Closure<Any>? = null) :
    PluginsRepository {

    private var resolvedDependency = false

    override fun resolve(project: Project, plugin: PluginDependencyNotation): File? {
        val dependency = plugin.toDependency(project)
        val repository = project.repositories.maven(ConfigureUtil.configureUsing(mavenClosure))

        debug(project.name, "Adding Maven repository to download $dependency - ${repositoryUrl ?: repository.url}")
        val mavenRepository = if (repositoryUrl == null) {
            repository
        } else {
            project.repositories.maven { it.url = URI.create(repositoryUrl) }
        }
        debug(project, "Adding Maven repository to download $dependency - $repositoryUrl")
        val mavenRepository = project.repositories.maven { it.url = URI.create(repositoryUrl) }

        var pluginFile: File? = null
        try {
            val configuration = project.configurations.detachedConfiguration(dependency)
            pluginFile = configuration.singleFile
            resolvedDependency = true
        } catch (e: Exception) {
            debug(project.name, "Couldn't find $dependency in ${repositoryUrl ?: repository.url}", e)
            debug(project, "Couldn't find $dependency in $repositoryUrl", e)
        }

        debug(project.name, "Removing Maven repository ${repositoryUrl ?: repository.url}")
        debug(project, "Removing Maven repository $repositoryUrl")
        project.repositories.remove(mavenRepository)

        return pluginFile
    }

    override fun postResolve(project: Project) {
        if (resolvedDependency) {
            val repository = project.repositories.maven(ConfigureUtil.configureUsing(mavenClosure))
            debug(project.name, "Adding Maven plugins repository ${repositoryUrl ?: repository.url}")
            debug(project, "Adding Maven plugins repository $repositoryUrl")
            project.repositories.maven { it.url = URI.create(repositoryUrl) }
            if (repositoryUrl == null) {
                project.repositories.add(repository)
            } else {
                project.repositories.maven { it.url = URI.create(repositoryUrl) }
            }
        }
    }
}
