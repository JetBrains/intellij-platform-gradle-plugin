package org.jetbrains.intellij.dependency

import groovy.transform.CompileStatic
import org.gradle.api.Project
import org.jetbrains.intellij.debug
import java.io.File
import java.net.URI

@CompileStatic
class MavenPluginsRepository(val project: Project, private val repoUrl: String) : PluginsRepository {

    private var resolvedDependency = false

    override fun resolve(plugin: PluginDependencyNotation): File? {
        val dependency = plugin.toDependency(project)

        debug(project, "Adding Maven repository to download $dependency - $repoUrl")
        val mavenRepo = project.repositories.maven { it.url = URI.create(repoUrl) }

        var pluginFile: File? = null
        try {
            val configuration = project.configurations.detachedConfiguration(dependency)
            pluginFile = configuration.singleFile
            resolvedDependency = true
        } catch (e: Exception) {
            debug(project, "Couldn't find $dependency in $repoUrl", e)
        }

        debug(project, "Removing Maven repository $repoUrl")
        project.repositories.remove(mavenRepo)

        return pluginFile
    }

    override fun postResolve() {
        if (resolvedDependency) {
            debug(project, "Adding Maven plugins repository $repoUrl")
            project.repositories.maven { it.url = URI.create(repoUrl) }
        }
    }
}
