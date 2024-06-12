// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.shim

import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingParser
import io.undertow.Handlers
import io.undertow.Undertow
import io.undertow.protocols.ssl.UndertowXnioSsl
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.RoutingHandler
import io.undertow.server.handlers.proxy.*
import io.undertow.server.handlers.proxy.ProxyClient.ProxyTarget
import io.undertow.util.Headers
import io.undertow.util.HttpString
import io.undertow.util.Methods
import io.undertow.util.PathTemplateMatch
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.api.GradleException
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.api.credentials.PasswordCredentials
import org.gradle.internal.hash.Hashing
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import org.xnio.OptionMap
import org.xnio.Xnio
import java.io.Closeable
import java.net.BindException
import java.net.HttpURLConnection
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginArtifactoryShim(
    private val repository: PluginArtifactRepository,
    private val repositoryType: CustomPluginRepositoryType,
    private val port: Int,
) {

    private val portIncrement = AtomicInteger(0)
    private val repositoryListing by lazy {
        repository.url.toURL().let { url ->
            url.openConnection().run {
                repository.getCredentials(PasswordCredentials::class.java)?.let {
                    val encoded = Base64.getEncoder().encode("${it.username}:${it.password}".toByteArray())
                    setRequestProperty("Authorization", "Basic $encoded")
                }
                repository.getCredentials(HttpHeaderCredentials::class.java)?.let {
                    setRequestProperty(it.name, it.value)
                }

                runCatching {
                    getInputStream().use { inputStream ->
                        inputStream.reader().use { reader ->
                            CustomPluginRepositoryListingParser.parseListOfPlugins(reader.readText(), url, url, repositoryType)
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

    fun start(): ShimServer {
        val proxyHandler = ProxyHandler.builder()
            .setProxyClient(getProxyClient())
            .setMaxRequestTime(30000)
            .setRewriteHostHeader(true)
            .build()

        val routingHandler = Handlers.routing()
            .add(Methods.HEAD, PLUGIN_DESCRIPTOR, PluginHttpHandler(::handleHeadPluginDescriptor))
            .add(Methods.GET, PLUGIN_DESCRIPTOR, PluginHttpHandler(::handleGetPluginDescriptor))
            .add(Methods.HEAD, PLUGIN_DOWNLOAD) { exchange ->
                val parameters = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).parameters

                val pluginId = parameters[PLUGIN_ID].orEmpty()
                val pluginVersion = parameters[PLUGIN_VERSION].orEmpty()
                val plugin = findPlugin(pluginId, pluginVersion)

                exchange.responseHeaders.put(Headers.CONTENT_TYPE, "application/zip")
                exchange.statusCode = when (plugin) {
                    null -> 404
                    else -> 200
                }
            }
            .add(Methods.GET, PLUGIN_DOWNLOAD, proxyHandler::handleRequest)
            .setFallbackHandler(proxyHandler)

        do {
            try {
                val shimProxyPort = port + portIncrement.getAndIncrement()
                return tryStartServer(shimProxyPort, routingHandler)
            } catch (e: RuntimeException) {
                if (e.cause == null || e.cause !is BindException) {
                    throw e
                }
            }
        } while (true)
    }

    private fun tryStartServer(port: Int, routingHandler: RoutingHandler): ShimServer {
        val server = Undertow.builder()
            .addHttpListener(port, "localhost")
            .setHandler(routingHandler)
            .setIoThreads(4)
            .build()

        server.start()

        return object : ShimServer {
            override val url
                get() = URI.create("http://localhost:$port")

            override fun close() = server.stop()
        }
    }

    private fun getProxyClient() = PluginArtifactRepositoryProxyClient(repository.url)

    private fun handleHeadPluginDescriptor(exchange: HttpServerExchange, pluginId: String, pluginVersion: String) =
        handlePluginDescriptor(exchange, pluginId, pluginVersion, false)

    private fun handleGetPluginDescriptor(exchange: HttpServerExchange, pluginId: String, pluginVersion: String) =
        handlePluginDescriptor(exchange, pluginId, pluginVersion, true)

    private fun handlePluginDescriptor(exchange: HttpServerExchange, pluginId: String, pluginVersion: String, sendContent: Boolean) = runCatching {
        val ivyDescriptor = getIvyDescriptor(pluginId, pluginVersion)
        val sha1Etag = Hashing.sha1().hashString(ivyDescriptor).toString()

        exchange.responseHeaders.put(HttpString.tryFromString("X-Checksum-Sha1"), sha1Etag)
        exchange.responseHeaders.put(HttpString.tryFromString("ETag"), "{SHA1{$sha1Etag}}")
        exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/xml")
        exchange.setResponseContentLength(ivyDescriptor.length.toLong())

        if (sendContent) {
            exchange.getResponseSender().send(ivyDescriptor)
        }
    }.onFailure {
        when (it) {
            is UnauthorizedException -> {
                exchange.statusCode = 401
                exchange.responseSender.send(it.message)
            }
            else -> {
                exchange.statusCode = 404
            }
        }
    }

    private fun getIvyDescriptor(pluginId: String, pluginVersion: String): String {
        val plugin = findPlugin(pluginId, pluginVersion)
            ?: throw GradleException("No plugin found for $pluginId:$pluginVersion")

        val ivyModule = IvyModule(
            info = IvyModule.Info(
                organisation = JETBRAINS_MARKETPLACE_MAVEN_GROUP,
                module = plugin.pluginId,
                revision = plugin.version,
            ),
            publications = listOf(
                IvyModule.Publication(type = "zip"),
            ),
        )

        return XML {
            indentString = "  "
        }.encodeToString(ivyModule)
    }

    companion object {
        private val PLUGIN_ID = "pluginId"
        private val PLUGIN_VERSION = "pluginVersion"
        private val PLUGIN_DESCRIPTOR = "/{$PLUGIN_ID}/{$PLUGIN_VERSION}/descriptor.ivy"
        private val PLUGIN_DOWNLOAD = "/{$PLUGIN_ID}/{$PLUGIN_VERSION}/download"
    }

    interface ShimServer : Closeable {

        val url: URI

        override fun close()
    }

    class PluginHttpHandler(private val delegate: RequestHandler) : HttpHandler {

        override fun handleRequest(exchange: HttpServerExchange) {
            val parameters = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).parameters
            delegate.handleRequest(exchange, parameters[PLUGIN_ID].orEmpty(), parameters[PLUGIN_VERSION].orEmpty())
        }

        fun interface RequestHandler {
            fun handleRequest(exchange: HttpServerExchange, pluginId: String, pluginVersion: String)
        }
    }

    inner class PluginArtifactRepositoryProxyClient(url: URI) : ProxyClient {

        private val host = URI(url.scheme, url.userInfo, url.host, url.port, null, null, null)

        private val delegate =
            LoadBalancingProxyClient()
                .addHost(host, UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY))
                .setConnectionsPerThread(20)

        override fun findTarget(exchange: HttpServerExchange?) = delegate.findTarget(exchange)

        override fun getConnection(
            target: ProxyTarget,
            exchange: HttpServerExchange,
            callback: ProxyCallback<ProxyConnection>,
            timeout: Long,
            timeUnit: TimeUnit,
        ) {
            when (val plugin = resolvePlugin(exchange.relativePath)) {
                null -> {
                    exchange.statusCode = 404
                    callback.couldNotResolveBackend(exchange)
                }

                else -> {
                    exchange.statusCode = 200
                    exchange.setRequestURI(plugin.downloadUrl.toString(), true)
                    delegate.getConnection(target, exchange, callback, timeout, timeUnit)
                }
            }
        }
    }

    private fun resolvePlugin(relativePath: String): CustomPluginRepositoryListingParser.PluginInfo? =
        relativePath
            .trim('/')
            .takeIf { it.endsWith("/download") }
            ?.removeSuffix("/download")
            ?.split('/')
            ?.run {
                val (id, version) = this
                findPlugin(id, version)
            }

    internal class UnauthorizedException(cause: Throwable) : Exception(cause)
}
