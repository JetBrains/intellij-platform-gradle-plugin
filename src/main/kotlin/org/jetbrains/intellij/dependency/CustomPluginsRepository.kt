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
            pluginsXmlUri = URI(uri.scheme, uri.userInfo, uri.host, uri.port, "${uri.path}/", uri.query, uri.fragment)
                .resolve("updatePlugins.xml")
        }
    }

    override fun resolve(project: Project, plugin: PluginDependencyNotation): File? {
        debug(project, "Loading list of plugins from: $pluginsXmlUri")
        val url = pluginsXmlUri.toURL()
        var downloadUrl: String?

        // Try to parse file as <plugin-repository>
        val pluginRepository = CustomPluginRepositoryListingParser.parseListOfPlugins(
            url.readText(),
            url,
            URL(repositoryUrl),
            CustomPluginRepositoryListingType.PLUGIN_REPOSITORY,
        )

        downloadUrl = pluginRepository.find {
            it.pluginId.equals(plugin.id, true) && it.version.equals(plugin.version, true)
        }?.let { "$repositoryUrl/${it.downloadUrl}" }

        if (downloadUrl == null) {
            // Try to parse XML file as <plugins>
            val plugins = CustomPluginRepositoryListingParser.parseListOfPlugins(
                url.readText(),
                url,
                URL(repositoryUrl),
                CustomPluginRepositoryListingType.SIMPLE,
            )
            downloadUrl = plugins.find {
                it.pluginId.equals(plugin.id, true) && it.version.equals(plugin.version, true)
            }?.let { "${it.downloadUrl}" }
        }

        if (downloadUrl == null) {
            return null
        }

        return downloadZipArtifact(project, downloadUrl, plugin)
    }

    private fun downloadZipArtifact(project: Project, url: String, plugin: PluginDependencyNotation): File? {
        val repository = project.repositories.ivy { ivy ->
            ivy.url = URI(url)
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
            warn(project, "Cannot download plugin from custom repository: ${plugin.id}:${plugin.version}", e)
            null
        } finally {
            project.repositories.remove(repository)
        }
    }

    override fun postResolve(project: Project) {
    }
}
