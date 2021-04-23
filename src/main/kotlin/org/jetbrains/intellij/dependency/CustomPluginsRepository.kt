package org.jetbrains.intellij.dependency

import de.undercouch.gradle.tasks.download.org.apache.commons.codec.binary.Hex
import org.gradle.api.Project
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.model.Category
import org.jetbrains.intellij.model.PluginRepository
import org.jetbrains.intellij.model.Plugins
import org.jetbrains.intellij.parseXml
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest

class CustomPluginsRepository(repoUrl: String) : PluginsRepository {

    private var pluginsXmlUri: URI
    private val repoUrl: String

    init {
        val uri = URI(repoUrl)
        if (uri.path.endsWith(".xml")) {
            this.repoUrl = repoUrl.substring(0, repoUrl.lastIndexOf('/'))
            pluginsXmlUri = uri
        } else {
            this.repoUrl = repoUrl
            pluginsXmlUri =
                URI(uri.scheme, uri.userInfo, uri.host, uri.port, "${uri.path}/", uri.query, uri.fragment).resolve("updatePlugins.xml")
        }
    }

    override fun resolve(project: Project, plugin: PluginDependencyNotation): File? {
        debug(project, "Loading list of plugins from: $pluginsXmlUri")
        var downloadUrl: String?

        // Try to parse file as <plugin-repository>
        val pluginRepository = parseXml(pluginsXmlUri.toURL().openStream(), PluginRepository::class.java)
        downloadUrl = pluginRepository.categories.flatMap(Category::plugins).find {
            it.id.equals(plugin.id, true) && it.version.equals(plugin.version, true)
        }?.downloadUrl?.let { "$repoUrl/$it" }

        if (downloadUrl == null) {
            // Try to parse XML file as <plugins>
            val plugins = parseXml(pluginsXmlUri.toURL().openStream(), Plugins::class.java)
            downloadUrl = plugins.items.find {
                it.id.equals(plugin.id, true) && it.version.equals(plugin.version, true)
            }?.url
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
        val digest = MessageDigest.getInstance("SHA-1").digest(repoUrl.toByteArray())
        val hash = Hex.encodeHex(digest).toString()
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
