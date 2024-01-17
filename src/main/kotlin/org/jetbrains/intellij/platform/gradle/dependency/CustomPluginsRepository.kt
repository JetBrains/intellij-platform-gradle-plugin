// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.dependency

import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingParser
import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingType
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create
import org.jetbrains.intellij.platform.gradle.debug
import org.jetbrains.intellij.platform.gradle.utils.DependenciesDownloader
import org.jetbrains.intellij.platform.gradle.utils.ivyRepository
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class CustomPluginsRepository(
    private val repositoryUrl: String,
    private val dependenciesDownloader: DependenciesDownloader,
) : PluginsRepository {

    override fun resolve(project: Project, plugin: PluginDependencyNotation, context: String?): File? {
        debug(context, "Loading list of plugins from: $repositoryUrl")
        val url = URL(repositoryUrl)
        val downloadUrl = resolveDownloadUrl(url, plugin, CustomPluginRepositoryListingType.PLUGIN_REPOSITORY)
            ?: resolveDownloadUrl(url, plugin, CustomPluginRepositoryListingType.SIMPLE)
            ?: return null

        return downloadZipArtifact(downloadUrl, plugin, context)
    }

    private fun resolveDownloadUrl(url: URL, plugin: PluginDependencyNotation, type: CustomPluginRepositoryListingType) =
        CustomPluginRepositoryListingParser
            .parseListOfPlugins(url.readText(), url, url, type)
            .find { it.pluginId.equals(plugin.id, true) && it.version.equals(plugin.version, true) }
            ?.downloadUrl
            ?.resolveRedirection()

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

internal fun URL.resolveRedirection() = with(openConnection() as HttpURLConnection) {
    instanceFollowRedirects = false
    inputStream.use {
        when (responseCode) {
            HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP -> URL(this@resolveRedirection, getHeaderField("Location"))
            else -> this@resolveRedirection
        }
    }.also { disconnect() }
}
