// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingParser
import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingType
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.utils.DependenciesDownloader
import org.jetbrains.intellij.utils.ivyRepository
import java.io.File
import java.net.URI
import java.net.URL

internal class CustomPluginsRepository(repositoryUrl: String) : PluginsRepository {

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

    private fun downloadZipArtifact(project: Project, url: URL, plugin: PluginDependencyNotation, context: String?) =
        project.objects.newInstance<DependenciesDownloader>()
            .downloadFromRepository(context, {
                create(
                    group = "com.jetbrains.plugins",
                    name = plugin.id,
                    version = plugin.version,
                    ext = "zip",
                )
            }, {
                ivyRepository(url.toString())
            }).first()

    override fun postResolve(project: Project, context: String?) {
    }
}
