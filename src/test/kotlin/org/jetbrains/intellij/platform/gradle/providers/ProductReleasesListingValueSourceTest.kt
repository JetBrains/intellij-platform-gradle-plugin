// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import org.gradle.testfixtures.ProjectBuilder
import java.net.InetSocketAddress
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.io.path.*
import kotlin.test.*

class ProductReleasesListingValueSourceTest {

    private lateinit var tempDir: Path
    private lateinit var cacheDir: Path
    private val project = ProjectBuilder.builder().build()
    private val servers = mutableListOf<HttpServer>()

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("product-releases-test")
        cacheDir = tempDir.resolve("cache").createDirectories()
    }

    @AfterTest
    fun tearDown() {
        servers.forEach { it.stop(0) }
        servers.clear()
        tempDir.toFile().deleteRecursively()
    }

    private fun obtain(
        url: String,
        offline: Boolean = false,
    ): String? = project.providers.of(ProductReleasesListingValueSource::class.java) {
        parameters.url.set(url)
        parameters.cacheDirectory.set(cacheDir.toString())
        parameters.offline.set(offline)
    }.orNull

    private fun Map<String, List<String>>.firstHeader(name: String) = entries
        .firstOrNull { it.key.equals(name, ignoreCase = true) }
        ?.value
        ?.firstOrNull()

    private fun startServer(handler: HttpHandler) = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        .apply {
            createContext("/releases.json", handler)
            start()
            servers += this
        }

    private fun serverUrl(server: HttpServer) = "http://127.0.0.1:${server.address.port}/releases.json"

    private fun cacheFile(url: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        return cacheDir.resolve("${HexFormat.of().formatHex(digest)}.json")
    }

    @Test
    fun `initial fetch saves content and validators`() {
        val responseBody = """[{"code":"IC","releases":[]}]"""
        val etag = "\"etag-123\""
        val lastModified = "Mon, 01 Jan 2026 00:00:00 GMT"

        val server = startServer { exchange ->
            exchange.responseHeaders.add("ETag", etag)
            exchange.responseHeaders.add("Last-Modified", lastModified)
            val bytes = responseBody.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val result = obtain(serverUrl(server))

        assertEquals(responseBody, result)

        val jsonFiles = cacheDir.listDirectoryEntries("*.json")
        val etagFiles = cacheDir.listDirectoryEntries("*.etag")
        val lastModifiedFiles = cacheDir.listDirectoryEntries("*.last-modified")

        assertEquals(1, jsonFiles.size)
        assertEquals(1, etagFiles.size)
        assertEquals(1, lastModifiedFiles.size)

        assertEquals(responseBody, jsonFiles.single().readText())
        assertEquals(etag, etagFiles.single().readText().trim())
        assertEquals(lastModified, lastModifiedFiles.single().readText().trim())
    }

    @Test
    fun `subsequent fetch revalidates cached content`() {
        val responseBody = """[{"code":"IC","releases":[]}]"""
        val etag = "\"etag-123\""
        val requestCount = AtomicInteger(0)

        val server = startServer { exchange ->
            requestCount.incrementAndGet()
            if (exchange.requestHeaders.getFirst("If-None-Match") == etag) {
                exchange.sendResponseHeaders(304, -1)
            } else {
                exchange.responseHeaders.add("ETag", etag)
                val bytes = responseBody.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }

        val url = serverUrl(server)
        val result1 = obtain(url)
        assertEquals(responseBody, result1)
        assertEquals(1, requestCount.get())

        val result2 = obtain(url)
        assertEquals(responseBody, result2)
        assertEquals(2, requestCount.get())
    }

    @Test
    fun `cached response sends conditional headers and reuses content on 304 Not Modified`() {
        val responseBody = """[{"code":"IC","releases":[]}]"""
        val etag = "\"etag-123\""
        val lastModified = "Mon, 01 Jan 2026 00:00:00 GMT"
        val receivedHeaders = CopyOnWriteArrayList<Map<String, List<String>>>()

        val server = startServer { exchange ->
            receivedHeaders.add(exchange.requestHeaders.toMap())
            if (exchange.requestHeaders.getFirst("If-None-Match") == etag) {
                exchange.sendResponseHeaders(304, -1)
            } else {
                exchange.responseHeaders.add("ETag", etag)
                exchange.responseHeaders.add("Last-Modified", lastModified)
                val bytes = responseBody.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }

        val url = serverUrl(server)
        val result1 = obtain(url)
        assertEquals(responseBody, result1)

        val result2 = obtain(url)
        assertEquals(responseBody, result2)
        assertEquals(2, receivedHeaders.size)

        val conditionalRequestHeaders = receivedHeaders[1]
        assertEquals(etag, conditionalRequestHeaders.firstHeader("If-None-Match"))
        assertEquals(lastModified, conditionalRequestHeaders.firstHeader("If-Modified-Since"))
    }

    @Test
    fun `cached response updates content when server returns 200 OK`() {
        val initialBody = """[{"code":"IC","version":"1"}]"""
        val updatedBody = """[{"code":"IC","version":"2"}]"""
        val initialEtag = "\"etag-1\""
        val updatedEtag = "\"etag-2\""
        val currentBody = AtomicReference(initialBody)
        val currentEtag = AtomicReference(initialEtag)

        val server = startServer { exchange ->
            val etag = currentEtag.get()
            val body = currentBody.get()
            exchange.responseHeaders.add("ETag", etag)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }

        val url = serverUrl(server)
        val result1 = obtain(url)
        assertEquals(initialBody, result1)

        currentBody.set(updatedBody)
        currentEtag.set(updatedEtag)

        val result2 = obtain(url)
        assertEquals(updatedBody, result2)

        val jsonFile = cacheDir.listDirectoryEntries("*.json").single()
        val etagFile = cacheDir.listDirectoryEntries("*.etag").single()
        assertEquals(updatedBody, jsonFile.readText())
        assertEquals(updatedEtag, etagFile.readText().trim())
    }

    @Test
    fun `fallback to cached content when server returns 500 error`() {
        val responseBody = """[{"code":"IC","releases":[]}]"""
        val shouldFail = AtomicReference(false)

        val server = startServer { exchange ->
            if (shouldFail.get()) {
                exchange.sendResponseHeaders(500, -1)
            } else {
                val bytes = responseBody.toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }

        val url = serverUrl(server)
        val result1 = obtain(url)
        assertEquals(responseBody, result1)

        shouldFail.set(true)

        val result2 = obtain(url)
        assertEquals(responseBody, result2)
    }

    @Test
    fun `throws exception on server failure when no cached content exists`() {
        val server = startServer { exchange ->
            exchange.sendResponseHeaders(500, -1)
        }

        assertFailsWith<Exception> {
            obtain(serverUrl(server))
        }
    }

    @Test
    fun `offline mode reuses cached content without network call`() {
        val cachedBody = """[{"code":"IC","cached":true}]"""
        val url = "http://invalid-host-that-does-not-exist:9999/releases.json"
        cacheFile(url).writeText(cachedBody)

        assertEquals(cachedBody, obtain(url, offline = true))
    }

    @Test
    fun `offline mode without cache returns null`() {
        val result = obtain("http://offline-no-cache.url/releases.json", offline = true)
        assertNull(result)
    }

    @Test
    fun `file URL is read and cached correctly`() {
        val initialContent = """[{"code":"IC","version":"1"}]"""
        val updatedContent = """[{"code":"IC","version":"2"}]"""
        val file = tempDir.resolve("local-releases.json").apply { writeText(initialContent) }
        val url = file.toUri().toString()

        assertEquals(initialContent, obtain(url))

        file.writeText(updatedContent)
        assertEquals(updatedContent, obtain(url))

        assertEquals(updatedContent, cacheFile(url).readText())
    }
}
