// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.shim

import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingParser
import io.undertow.Handlers
import io.undertow.server.HttpServerExchange
import io.undertow.util.Headers
import io.undertow.util.Methods
import io.undertow.util.PathTemplateMatch
import io.undertow.util.StatusCodes
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.credentials.PasswordCredentials
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import java.net.HttpURLConnection
import java.net.URI
import java.util.*

class PluginArtifactoryShim(repository: PluginArtifactRepository, port: Int) : Shim(repository, port) {

    private val repositoryListing by lazy {
        repository.url.toURL().let { url ->
            url.openConnection().run {
                repository.getCredentials(PasswordCredentials::class.java).let {
                    val encoded = Base64.getEncoder().encode("${it.username}:${it.password}".toByteArray())
                    setRequestProperty("Authorization", "Basic $encoded")
                }
                repository.getCredentials(HttpHeaderCredentials::class.java).let {
                    setRequestProperty(it.name, it.value)
                }

                runCatching {
                    getInputStream().use { inputStream ->
                        inputStream.reader().use { reader ->
                            CustomPluginRepositoryListingParser.parseListOfPlugins(reader.readText(), url, url, repository.type)
                        }
                    }
                }.onFailure {
                    throw when (this) {
                        is HttpURLConnection -> {
                            when {
                                responseCode == 401 -> UnauthorizedException(it)
                                else -> it
                            }
                        }

                        else -> it
                    }
                }.getOrThrow()
            }
        }
    }

    private fun findPlugin(pluginId: String, pluginVersion: String) =
        repositoryListing.find {
            it.pluginId == pluginId && it.version == pluginVersion
        }

    private val ivyDescriptorHandler = IvyDescriptorHttpHandler { groupId, artifactId, version ->
        val plugin = findPlugin(artifactId, version)
            ?: return@IvyDescriptorHttpHandler null

        IvyModule(
            info = IvyModule.Info(
                organisation = JETBRAINS_MARKETPLACE_MAVEN_GROUP,
                module = plugin.pluginId,
                revision = plugin.version,
            ),
            publications = listOf(
                IvyModule.Artifact(type = "zip"),
            ),
        )
    }

    private val downloadHandler: (HttpServerExchange) -> Unit = { exchange ->
        val parameters = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).parameters
        val artifactId = parameters[ARTIFACT_ID].orEmpty()
        val version = parameters[VERSION].orEmpty()

        val plugin = findPlugin(artifactId, version)

        when (plugin) {
            null -> {
                exchange.statusCode = StatusCodes.NOT_FOUND
            }

            else -> {
                exchange.statusCode = StatusCodes.FOUND
                exchange.responseHeaders.put(Headers.CONTENT_TYPE, "application/zip")
            }
        }
    }

    private val proxyHandler = createProxyHandler(PluginArtifactRepositoryProxyClient(repository.url))

    override fun getRoutingHandler() = Handlers.routing()
        .add(Methods.HEAD, DESCRIPTOR_PATH, ivyDescriptorHandler)
        .add(Methods.GET, DESCRIPTOR_PATH, ivyDescriptorHandler)
        .add(Methods.HEAD, DOWNLOAD_PATH, downloadHandler)
        .add(Methods.GET, DOWNLOAD_PATH, proxyHandler::handleRequest)

    inner class PluginArtifactRepositoryProxyClient(url: URI) : ShimProxyClient(url) {

        override fun resolveUrl(exchange: HttpServerExchange) =
            exchange.relativePath
                .trim('/')
                .takeIf { it.endsWith("/download") }
                ?.removeSuffix("/download")
                ?.split('/')
                ?.run {
                    val (id, version) = this
                    findPlugin(id, version)
                }
                ?.downloadUrl
                ?.toString()
    }
}
