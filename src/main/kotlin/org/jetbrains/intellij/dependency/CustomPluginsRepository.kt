package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingParser
import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingType
import org.gradle.api.Project
import org.jetbrains.intellij.debug
import java.io.File
import java.math.BigInteger
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

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
            pluginsXmlUri =
                URI(uri.scheme, uri.userInfo, uri.host, uri.port, "${uri.path}/", uri.query, uri.fragment).resolve("updatePlugins.xml")
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

    private fun getCacheDirectoryPath(project: Project): String {
        // todo: a better way to define cache directory
        val gradleHomePath = project.gradle.gradleUserHomeDir.absolutePath
        val mavenCacheDirectoryPath = Paths.get(gradleHomePath, "caches/modules-2/files-2.1").toString()
        val digest = MessageDigest.getInstance("SHA-1").digest(repositoryUrl.toByteArray())
        val hash = BigInteger(1, digest).toString(16)
        return Paths.get(mavenCacheDirectoryPath, "com.jetbrains.intellij.idea", hash).toString()
    }

    private fun downloadZipArtifact(project: Project, url: String, plugin: PluginDependencyNotation): File {
        val targetFile = Paths.get(getCacheDirectoryPath(project), "com.jetbrains.plugins", "${plugin.id}-${plugin.version}.zip").toFile()
        if (!targetFile.isFile) {
            targetFile.parentFile.mkdirs()
            Files.copy(URI.create(url).toURL().openStream(), targetFile.toPath())
        }
        return targetFile
    }

    override fun postResolve(project: Project) {
    }
}
