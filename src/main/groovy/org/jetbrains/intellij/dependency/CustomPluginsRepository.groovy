package org.jetbrains.intellij.dependency

import org.gradle.api.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.Utils

class CustomPluginsRepository implements PluginsRepository {

    private final Project project
    private final String repoUrl
    private final Node pluginsXml

    CustomPluginsRepository(@NotNull Project project, @NotNull String repoUrl) {
        this.project = project
        def pluginsXmlUrl
        if (repoUrl.endsWith(".xml")) {
            this.repoUrl = repoUrl.substring(0, repoUrl.lastIndexOf('/'))
            pluginsXmlUrl = repoUrl
        } else {
            this.repoUrl = repoUrl
            pluginsXmlUrl = repoUrl + "/updatePlugins.xml"
        }
        Utils.debug(project, "Loading list of plugins from: ${pluginsXmlUrl}")
        pluginsXml = Utils.parseXml(new URI("${pluginsXmlUrl}").toURL().openStream())
    }

    @Nullable
    File resolve(@NotNull PluginDependencyNotation plugin) {
        String downloadUrl = pluginsXml.category."idea-plugin"
                .find { it.id.text().equalsIgnoreCase(plugin.id) && it.version.text() == plugin.version }
                ?."download-url"?.text()
        if (downloadUrl == null) return null

        return Utils.downloadZipArtifact(project, repoUrl,
                downloadUrl.replace("${plugin.version}.zip", "[revision].[ext]"),
                "com.jetbrains.plugins:$plugin.id:$plugin.version")
    }

    @Override
    void postResolve() {
    }
}
