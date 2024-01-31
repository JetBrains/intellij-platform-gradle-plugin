// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingParser
import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingType
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.resolveRedirection
import org.jetbrains.intellij.utils.DependenciesDownloader
import org.jetbrains.intellij.utils.ivyRepository
import java.io.File
import java.net.URL

class CustomPluginsRepository (
    private val config: Configuration,
    private val dependenciesDownloader: DependenciesDownloader,
) : PluginsRepository {
    abstract class Configuration {
        abstract val url: Property<String>
        abstract val authorizationToken: Property<String>
    }

    override fun resolve(project: Project, plugin: PluginDependencyNotation, context: String?): File? {
        val repositoryUrl = config.url.get()
        debug(context, "Loading list of plugins from: $repositoryUrl")
        val url = URL(repositoryUrl)
        val downloadUrl = resolveDownloadUrl(url, plugin, CustomPluginRepositoryListingType.PLUGIN_REPOSITORY)
            ?: resolveDownloadUrl(url, plugin, CustomPluginRepositoryListingType.SIMPLE)
            ?: return null

        return downloadZipArtifact(downloadUrl, plugin, context)
    }

    private fun resolveDownloadUrl(url: URL, plugin: PluginDependencyNotation, type: CustomPluginRepositoryListingType) =
        CustomPluginRepositoryListingParser
            .parseListOfPlugins(downloadPluginXml(url), url, url, type)
            .find { it.pluginId.equals(plugin.id, true) && it.version.equals(plugin.version, true) }
            ?.downloadUrl
            ?.resolveRedirection()

    private fun downloadPluginXml(pluginXmlUrl: URL): String {
        val connection = pluginXmlUrl.openConnection()
        if (config.authorizationToken.isPresent) {
            connection.setRequestProperty("Authorization", config.authorizationToken.get())
        }
        return connection.getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    private fun downloadZipArtifact(url: URL, plugin: PluginDependencyNotation, context: String?) =
        dependenciesDownloader.downloadFromRepository(context, {
            create(
                group = "com.jetbrains.plugins",
                name = plugin.id,
                version = plugin.version,
                ext = "zip",
            )
        }, {
            ivyRepository(url)
        }).first()

    override fun postResolve(project: Project, context: String?) {
    }
}
