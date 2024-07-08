// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.shim

import io.undertow.Undertow
import io.undertow.protocols.ssl.UndertowXnioSsl
import io.undertow.server.HttpHandler
import io.undertow.server.HttpServerExchange
import io.undertow.server.RoutingHandler
import io.undertow.server.handlers.proxy.*
import io.undertow.server.handlers.proxy.ProxyClient.ProxyTarget
import io.undertow.util.*
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XML
import org.gradle.internal.hash.Hashing
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.BaseArtifactRepository
import org.jetbrains.intellij.platform.gradle.models.IvyModule
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.xnio.OptionMap
import org.xnio.Xnio
import java.io.Closeable
import java.net.BindException
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal const val GROUP_ID = "groupId"
internal const val ARTIFACT_ID = "artifactId"
internal const val VERSION = "version"
internal const val DESCRIPTOR_PATH = "/{$GROUP_ID}/{$ARTIFACT_ID}/{$VERSION}/descriptor.ivy"
internal const val DOWNLOAD_PATH = "/{$GROUP_ID}/{$ARTIFACT_ID}/{$VERSION}/download"

abstract class Shim(
    private val repository: BaseArtifactRepository,
    private val port: Int,
) {
    private val portIncrement = AtomicInteger(0)

    private val log = Logger(javaClass)

    abstract fun getRoutingHandler(): RoutingHandler

    fun start(): Server {
        val routingHandler = getRoutingHandler()

        do {
            try {
                val shimProxyPort = port + portIncrement.getAndIncrement()
                log.info("Starting shim proxy on port $shimProxyPort")

                return tryStartServer(shimProxyPort, routingHandler)
            } catch (e: RuntimeException) {
                if (e.cause == null || e.cause !is BindException) {
                    throw e
                }
            }
        } while (true)
    }

    private fun tryStartServer(port: Int, routingHandler: RoutingHandler): Server {
        val server = Undertow.builder()
            .addHttpListener(port, "localhost")
            .setHandler(routingHandler)
            .setIoThreads(4)
            .build()

        server.start()

        return object : Server {
            override val url
                get() = URI.create("http://localhost:$port")

            override fun close() = server.stop()
        }
    }

    internal fun createProxyHandler(proxyClient: ShimProxyClient) =
        ProxyHandler.builder()
            .setProxyClient(proxyClient)
            .setMaxRequestTime(30000)
            .setRewriteHostHeader(true)
            .build()

    abstract inner class ShimProxyClient(url: URI) : ProxyClient {

        private val host = URI(url.scheme, url.userInfo, url.host, url.port, null, null, null)

        private val delegate = LoadBalancingProxyClient()
            .addHost(host, UndertowXnioSsl(Xnio.getInstance(), OptionMap.EMPTY))
            .setConnectionsPerThread(20)

        abstract fun resolveUrl(exchange: HttpServerExchange): String?

        override fun findTarget(exchange: HttpServerExchange?) = delegate.findTarget(exchange)

        override fun getConnection(
            target: ProxyTarget,
            exchange: HttpServerExchange,
            callback: ProxyCallback<ProxyConnection>,
            timeout: Long,
            timeUnit: TimeUnit,
        ) {
            val url = resolveUrl(exchange)
            when (url) {
                null -> {
                    exchange.statusCode = StatusCodes.NOT_FOUND
                    callback.couldNotResolveBackend(exchange)
                }

                else -> {
                    exchange.statusCode = StatusCodes.FOUND
                    exchange.setRequestURI(url, true)
                    delegate.getConnection(target, exchange, callback, timeout, timeUnit)
                }
            }
        }
    }

    internal class IvyDescriptorHttpHandler(private val delegate: RequestHandler) : HttpHandler {

        private val log = Logger(javaClass)

        override fun handleRequest(exchange: HttpServerExchange) = runCatching {
            val parameters = exchange.getAttachment(PathTemplateMatch.ATTACHMENT_KEY).parameters
            val groupId = parameters[GROUP_ID].orEmpty()
            val artifactId = parameters[ARTIFACT_ID].orEmpty()
            val version = parameters[VERSION].orEmpty()

            log.info("Resolving descriptor for $groupId:$artifactId:$version")

            val ivyModule = delegate.createIvyModule(groupId, artifactId, version)

            if (ivyModule == null) {
                log.info("Descriptor for $groupId:$artifactId:$version not found")

                exchange.statusCode = 404
                exchange.endExchange()
                return@runCatching
            }

            val ivyDescriptor = XML {
                indentString = "  "
            }.encodeToString(ivyModule)

            val sha1 = Hashing.sha1().hashString(ivyDescriptor).toString()
            log.info("Descriptor for $groupId:$artifactId:$version resolved (hash: $sha1)")

            exchange.responseHeaders.put(HttpString.tryFromString("X-Checksum-Sha1"), sha1)
            exchange.responseHeaders.put(Headers.ETAG, "{SHA1{$sha1}}")
            exchange.responseHeaders.put(Headers.CONTENT_TYPE, "text/xml")
            exchange.setResponseContentLength(ivyDescriptor.length.toLong())

            log.info("Descriptor requested with ${exchange.requestMethod} method")
            if (exchange.requestMethod == Methods.GET) {
                exchange.getResponseSender().send(ivyDescriptor)
            }
        }.onFailure {
            when (it) {
                is UnauthorizedException -> {
                    exchange.statusCode = StatusCodes.UNAUTHORIZED
                    exchange.responseSender.send(it.message)

                    log.error("Unauthorized request to ${exchange.requestURI}: ${it.message}")
                }

                else -> {
                    exchange.statusCode = StatusCodes.NOT_FOUND

                    log.error("Could not resolve descriptor for ${exchange.requestURI}", it)
                }
            }
        }.getOrThrow()

        fun interface RequestHandler {
            fun createIvyModule(group: String, artifact: String, version: String): IvyModule?
        }
    }

    interface Server : Closeable {
        val url: URI
        override fun close()
    }

    internal class UnauthorizedException(cause: Throwable) : Exception(cause)
}
