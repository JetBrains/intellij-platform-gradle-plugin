// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.shim

import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingParser
import com.jetbrains.plugin.structure.intellij.repository.CustomPluginRepositoryListingType
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
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import org.xnio.OptionMap
import org.xnio.Xnio
import java.io.Closeable
import java.net.BindException
import java.net.URI
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class PluginArtifactoryShim(
    private val repository: PluginArtifactRepository,
    private val port: Int,
) {

    private val portIncrement = AtomicInteger(0)
    internal val repositoryListing by lazy {
        repository.url.toURL().let { url ->
            url.openConnection()
                .apply {
                    repository.getCredentials(PasswordCredentials::class.java)?.let {
                        val encoded = Base64.getEncoder().encode("${it.username}:${it.password}".toByteArray())
                        setRequestProperty("Authorization", "Basic $encoded")
                    }
                    repository.getCredentials(HttpHeaderCredentials::class.java)?.let {
                        setRequestProperty(it.name, it.value)
                    }
                }
                .getInputStream()
                .use { inputStream ->
                    inputStream.reader().use { reader ->
                        CustomPluginRepositoryListingParser.parseListOfPlugins(reader.readText(), url, url, CustomPluginRepositoryListingType.PLUGIN_REPOSITORY)
                        // TODO: handle the other type as well - through the customRepo helper arguments
                    }
                }
        }
    }

    fun start(): ShimServer {
        val proxyHandler = ProxyHandler.builder()
            .setProxyClient(getProxyClient())
            .setMaxRequestTime(30000)
            .setRewriteHostHeader(true)
            .build()

        val routingHandler = Handlers.routing() // Pure hacks to convert package.json to ivy descriptors and properly handle npm scope.
            .add(Methods.HEAD, PLUGIN_TEMPLATE, PluginHttpHandler(::handleHeadIvyDescriptor))
            .add(Methods.GET, PLUGIN_TEMPLATE, PluginHttpHandler(::handleGetIvyDescriptor))
            .add(Methods.HEAD, PLUGIN_DOWNLOAD) { exchange ->
                val parameters = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).parameters

                val pluginId = parameters[PLUGIN_ID].orEmpty()
                val pluginVersion = parameters[PLUGIN_VERSION].orEmpty()

                val plugin = repositoryListing.find {
                    it.pluginId == pluginId && it.version == pluginVersion
                }

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

    private fun getProxyClient() = runCatching {
        PluginArtifactRepositoryProxyClient(
            LoadBalancingProxyClient()
                .addHost(repository.url, UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY))
                .setConnectionsPerThread(20)
        )
    }.getOrElse { e ->
        throw RuntimeException(e)
    }

    private fun handleHeadIvyDescriptor(exchange: HttpServerExchange, pluginId: String, pluginVersion: String) {
        try {
            val ivyDescriptor = getIvyDescriptor(pluginId, pluginVersion)
            val sha1Etag = Hashing.sha1().hashString(ivyDescriptor).toString()

            exchange.responseHeaders.put(HttpString.tryFromString("X-Checksum-Sha1"), sha1Etag)
            exchange.responseHeaders.put(HttpString.tryFromString("ETag"), "{SHA1{$sha1Etag}}")

            exchange.setResponseContentLength(ivyDescriptor.length.toLong())
            exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/xml")
            exchange.setStatusCode(200)
        } catch (t: RuntimeException) {
            exchange.setStatusCode(404)
        }
    }

    private fun handleGetIvyDescriptor(exchange: HttpServerExchange, pluginId: String, pluginVersion: String) {
        try {
            val ivyDescriptor = getIvyDescriptor(pluginId, pluginVersion)
            val sha1Etag = Hashing.sha1().hashString(ivyDescriptor).toString()

            exchange.responseHeaders.put(HttpString.tryFromString("X-Checksum-Sha1"), sha1Etag)
            exchange.responseHeaders.put(HttpString.tryFromString("ETag"), "{SHA1{$sha1Etag}}")

            exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/xml")
            exchange.setResponseContentLength(ivyDescriptor.length.toLong());
            exchange.getResponseSender().send(ivyDescriptor);
        } catch (t: RuntimeException) {
            exchange.setStatusCode(404)
        }
    }

    private fun getIvyDescriptor(pluginId: String, pluginVersion: String): String {
        val plugin = repositoryListing.find {
            it.pluginId == pluginId && it.version == pluginVersion
        } ?: throw GradleException("No plugin found for $pluginId:$pluginVersion")

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
        private val PLUGIN_TEMPLATE = "/{$PLUGIN_ID}/{$PLUGIN_VERSION}/descriptor.ivy"
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

    inner class PluginArtifactRepositoryProxyClient(
        private val delegate: ProxyClient,
    ) : ProxyClient {

        override fun findTarget(exchange: HttpServerExchange?) = delegate.findTarget(exchange)

        override fun getConnection(
            target: ProxyTarget?,
            exchange: HttpServerExchange,
            callback: ProxyCallback<ProxyConnection?>?,
            timeout: Long,
            timeUnit: TimeUnit?,
        ) {
            val suffix = "/download"

            val (id, version) = exchange.relativePath
                .trim('/')
                .takeIf { it.endsWith(suffix) }
                ?.removeSuffix(suffix)
                ?.split('/')
                ?: return

            val plugin = repositoryListing
                .find { it.pluginId == id && it.version == version }
                ?: return

            exchange.setRequestURI(plugin.downloadUrl.toString(), true)
            delegate.getConnection(target, exchange, callback, timeout, timeUnit)
        }
    }
}
