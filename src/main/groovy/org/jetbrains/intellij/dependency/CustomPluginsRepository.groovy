package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.Utils

import java.nio.file.Files
import java.nio.file.Paths

class CustomPluginsRepository implements PluginsRepository {

    private final Project project
    private final String repoUrl
    private final Node pluginsXml

    CustomPluginsRepository(@NotNull Project project, @NotNull String repoUrl) {
        this.project = project
        URI pluginsXmlUri
        def uri = new URI(repoUrl)
        if (uri.path.endsWith('.xml')) {
            this.repoUrl = repoUrl.substring(0, repoUrl.lastIndexOf('/'))
            pluginsXmlUri = uri
        } else {
            this.repoUrl = repoUrl
            pluginsXmlUri = new URI(uri.scheme, uri.userInfo, uri.host, uri.port, "$uri.path/", uri.query, uri.fragment)
                    .resolve("updatePlugins.xml")
        }
        Utils.debug(project, "Loading list of plugins from: ${pluginsXmlUri}")
        pluginsXml = Utils.parseXml(pluginsXmlUri.toURL().openStream())
    }

    @Nullable
    File resolve(@NotNull PluginDependencyNotation plugin) {
        String downloadUrl
        if (pluginsXml.name() == "plugin-repository") {
            downloadUrl = pluginsXml.category."idea-plugin"
                    .find { it.id.text().equalsIgnoreCase(plugin.id) && it.version.text() == plugin.version }
                    ?."download-url"?.text()
            if (downloadUrl == null) return null
            downloadUrl = repoUrl + "/" + downloadUrl
        } else {
            downloadUrl = pluginsXml.plugin
                    .find { it.@id.equalsIgnoreCase(plugin.id) && it.@version.equalsIgnoreCase(plugin.version) }
                    ?."@url"
            if (downloadUrl == null) return null
        }
        return downloadZipArtifact(downloadUrl, plugin)
    }

    private String getCacheDirectoryPath() {
        // todo: a better way to define cache directory
        String gradleHomePath = project.gradle.gradleUserHomeDir.absolutePath
        String mavenCacheDirectoryPath = Paths.get(gradleHomePath, 'caches/modules-2/files-2.1').toString()
        return Paths.get(mavenCacheDirectoryPath, 'com.jetbrains.intellij.idea').toString()
    }

    @NotNull
    private File downloadZipArtifact(@NotNull String url, @NotNull PluginDependencyNotation plugin) {
        def targetFile = Paths.get(getCacheDirectoryPath(), "com.jetbrains.plugins", "${plugin.id}-${plugin.version}.zip").toFile()
        if (!targetFile.isFile()) {
            targetFile.parentFile.mkdirs()
            Files.copy(URI.create(url).toURL().openStream(), targetFile.toPath())
        }
        return targetFile
    }

    @Override
    void postResolve() {
    }
}
