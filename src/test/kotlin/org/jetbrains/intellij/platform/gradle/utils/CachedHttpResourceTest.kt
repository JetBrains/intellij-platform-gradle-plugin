// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.io.IOException
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CachedHttpResourceTest {

    private lateinit var tempDir: Path
    private lateinit var cacheDir: Path
    private val servers = mutableListOf<HttpServer>()

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("cached-http-resource-test")
        cacheDir = tempDir.resolve("cache").createDirectories()
    }

    @AfterTest
    fun tearDown() {
        servers.forEach { it.stop(0) }
        servers.clear()
        tempDir.toFile().deleteRecursively()
    }

    private fun startServer(handler: HttpHandler) = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        .apply {
            createContext("/data.json", handler)
            start()
            servers += this
        }

    private fun serverUrl(server: HttpServer) = "http://127.0.0.1:${server.address.port}/data.json"

    @Test
    fun `read Http resource online writes cache and validator files`() {
        val body = """{"key":"value"}"""
        val etag = "\"etag-123\""
        val lastModified = "Mon, 01 Jan 2026 00:00:00 GMT"

        val server = startServer { exchange ->
            exchange.responseHeaders.add("ETag", etag)
            exchange.responseHeaders.add("Last-Modified", lastModified)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val url = serverUrl(server)
        var refreshCalled = false
        val content = CachedHttpResource.read(
            url = url,
            cacheDirectory = cacheDir,
            onRefresh = { text, _ ->
                refreshCalled = true
                assertEquals(body, text)
            },
        )

        assertEquals(body, content)
        assertTrue(refreshCalled)

        val cacheFile = CachedHttpResource.cacheFile(url, cacheDir)
        assertEquals(body, cacheFile.readText())
        assertEquals(etag, cacheFile.resolveSibling("${cacheFile.fileName}.etag").readText())
        assertEquals(lastModified, cacheFile.resolveSibling("${cacheFile.fileName}.last-modified").readText())
    }

    @Test
    fun `read Http resource with 304 reuses cache without overwriting`() {
        val body = """{"key":"initial"}"""
        val etag = "\"etag-initial\""
        val lastModified = "Mon, 01 Jan 2026 00:00:00 GMT"
        val requestCount = AtomicInteger()
        val receivedEtag = AtomicReference<String?>()

        val server = startServer { exchange ->
            val count = requestCount.incrementAndGet()
            receivedEtag.set(exchange.requestHeaders.getFirst("If-None-Match"))
            if (count == 1) {
                exchange.responseHeaders.add("ETag", etag)
                exchange.responseHeaders.add("Last-Modified", lastModified)
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } else {
                exchange.sendResponseHeaders(304, -1)
            }
        }

        val url = serverUrl(server)
        val result1 = CachedHttpResource.read(url = url, cacheDirectory = cacheDir)
        assertEquals(body, result1)
        assertEquals(1, requestCount.get())
        assertNull(receivedEtag.get())

        var cacheHit = false
        val result2 = CachedHttpResource.read(
            url = url,
            cacheDirectory = cacheDir,
            onCacheHit = { _, is304 ->
                if (is304) cacheHit = true
            },
        )
        assertEquals(body, result2)
        assertEquals(2, requestCount.get())
        assertEquals(etag, receivedEtag.get())
        assertTrue(cacheHit)
    }

    @Test
    fun `read Http resource offline returns cached content when present`() {
        val cachedBody = """{"offline":true}"""
        val url = "http://offline.local/data.json"
        val cacheFile = CachedHttpResource.cacheFile(url, cacheDir)
        cacheFile.writeText(cachedBody)

        var offlineHit = false
        val content = CachedHttpResource.read(
            url = url,
            cacheDirectory = cacheDir,
            offline = true,
            onOfflineCacheHit = { offlineHit = true },
        )

        assertEquals(cachedBody, content)
        assertTrue(offlineHit)
    }

    @Test
    fun `read Http resource offline returns null when no cache exists`() {
        var offlineMiss = false
        val content = CachedHttpResource.read(
            url = "http://offline.local/data.json",
            cacheDirectory = cacheDir,
            offline = true,
            onOfflineCacheMiss = { offlineMiss = true },
        )

        assertNull(content)
        assertTrue(offlineMiss)
    }

    @Test
    fun `read Http resource falls back to cache on server error`() {
        val body = """{"fallback":true}"""
        val shouldFail = AtomicBoolean(false)

        val server = startServer { exchange ->
            if (shouldFail.get()) {
                exchange.sendResponseHeaders(500, -1)
            } else {
                val bytes = body.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }

        val url = serverUrl(server)
        assertEquals(body, CachedHttpResource.read(url = url, cacheDirectory = cacheDir))

        shouldFail.set(true)
        var fallbackTriggered = false
        val result = CachedHttpResource.read(
            url = url,
            cacheDirectory = cacheDir,
            onErrorFallback = { _, _, _ -> fallbackTriggered = true },
        )

        assertEquals(body, result)
        assertTrue(fallbackTriggered)
    }

    @Test
    fun `read Http resource throws exception on server error when no cache exists`() {
        val server = startServer { exchange ->
            exchange.sendResponseHeaders(500, -1)
        }

        assertFailsWith<IOException> {
            CachedHttpResource.read(url = serverUrl(server), cacheDirectory = cacheDir)
        }
    }

    @Test
    fun `read non-Http URL reads and caches content`() {
        val localFile = tempDir.resolve("local-data.json").apply { writeText("""{"local":true}""") }
        val url = localFile.toUri().toString()

        val result = CachedHttpResource.read(url = url, cacheDirectory = cacheDir)
        assertEquals("""{"local":true}""", result)

        val cacheFile = CachedHttpResource.cacheFile(url, cacheDir)
        assertEquals("""{"local":true}""", cacheFile.readText())
    }
}
