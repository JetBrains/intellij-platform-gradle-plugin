package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingParser
import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingType
import org.gradle.api.Project
import org.jetbrains.intellij.create
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.warn
import java.io.File
import java.net.URI
import java.net.URL

class CustomPluginsRepository(repositoryUrl: String) : PluginsRepository {

    private val pluginsXmlUri: URI
    private val repositoryUrl: String

    init {
        val uri = URI(repositoryUrl)
        if (uri.path.endsWith(".xml")) {
            this.repositoryUrl = repositoryUrl.substring(0, repositoryUrl.lastIndexOf('/'))
            pluginsXmlUri = uri
        } else {
            this.repositoryUrl = repositoryUrl
            pluginsXmlUri = uri.run { URI(scheme, userInfo, host, port, "$path/", query, fragment).resolve("updatePlugins.xml") }
        }
    }

    override fun resolve(project: Project, plugin: PluginDependencyNotation, context: String?): File? {
        debug(context, "Loading list of plugins from: $pluginsXmlUri")
        val url = pluginsXmlUri.toURL()
        val downloadUrl = resolveDownloadUrl(url, plugin, CustomPluginRepositoryListingType.PLUGIN_REPOSITORY)
            ?: resolveDownloadUrl(url, plugin, CustomPluginRepositoryListingType.SIMPLE)
            ?: return null

        return downloadZipArtifact(project, downloadUrl, plugin, context)
    }

    private fun resolveDownloadUrl(url: URL, plugin: PluginDependencyNotation, type: CustomPluginRepositoryListingType) =
        CustomPluginRepositoryListingParser
            .parseListOfPlugins(url.readText(), url, URL(repositoryUrl), type)
            .find { it.pluginId.equals(plugin.id, true) && it.version.equals(plugin.version, true) }
            ?.downloadUrl

    private fun downloadZipArtifact(project: Project, url: URL, plugin: PluginDependencyNotation, context: String?): File? {
        val repository = project.repositories.ivy { ivy ->
            ivy.url = url.toURI()
            ivy.patternLayout { it.artifact("") }
            ivy.metadataSources { it.artifact() }
        }
        val dependency = project.dependencies.create(
            group = "com.jetbrains.plugins",
            name = plugin.id,
            version = plugin.version,
            extension = "zip",
        )

        return try {
            project.configurations.detachedConfiguration(dependency).singleFile
        } catch (e: Exception) {
            warn(context, "Cannot download plugin from custom repository: ${plugin.id}:${plugin.version}", e)
            null
        } finally {
            project.repositories.remove(repository)
        }
    }

    override fun postResolve(project: Project, context: String?) {
    }
}
