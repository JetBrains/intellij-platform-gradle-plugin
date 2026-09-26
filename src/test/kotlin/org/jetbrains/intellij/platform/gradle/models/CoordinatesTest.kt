// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CoordinatesTest {

    private val coordinates = Coordinates("org.example", "example-plugin")
    private lateinit var tempDir: Path
    private lateinit var cacheDir: Path

    @BeforeTest
    fun setUp() {
        tempDir = createTempDirectory("coordinates-test")
        cacheDir = tempDir.resolve("cache").createDirectories()
    }

    @AfterTest
    fun tearDown() {
        tempDir.toFile().deleteRecursively()
    }

    private val metadataXml = """
        <metadata>
          <groupId>org.example</groupId>
          <artifactId>example-plugin</artifactId>
          <versioning>
            <latest>2.1.0</latest>
            <release>2.1.0</release>
            <versions>
              <version>2.0.0</version>
              <version>2.1.0</version>
            </versions>
            <lastUpdated>20260926075800</lastUpdated>
          </versioning>
        </metadata>
    """.trimIndent()

    @Test
    fun `resolve latest version from Maven metadata`() = withServer(
        statusCode = 200,
        body = metadataXml,
    ) { repositoryUrl, requests ->
        assertEquals("2.1.0", coordinates.resolveLatestVersion(repositoryUrl))
        assertEquals(1, requests.get())
    }

    @Test
    fun `fail on unsuccessful Maven metadata response`() = withServer(statusCode = 503) { repositoryUrl, requests ->
        assertFailsWith<IOException> {
            coordinates.resolveLatestVersion(repositoryUrl)
        }
        assertEquals(1, requests.get())
    }

    @Test
    fun `skip Maven metadata request when offline`() = withServer(statusCode = 200) { repositoryUrl, requests ->
        assertEquals(null, coordinates.resolveLatestVersion(repositoryUrl, offline = true))
        assertEquals(0, requests.get())
    }

    @Test
    fun `resolve latest version with cache and 304 conditional request`() {
        val lastReceivedEtag = AtomicReference<String?>()
        val requests = AtomicInteger()

        withCustomServer({ exchange ->
            requests.incrementAndGet()
            val incomingEtag = exchange.requestHeaders.getFirst("If-None-Match")
            lastReceivedEtag.set(incomingEtag)

            if (incomingEtag == "\"v2.1.0-etag\"") {
                exchange.sendResponseHeaders(304, -1)
            } else {
                exchange.responseHeaders.set("ETag", "\"v2.1.0-etag\"")
                val response = metadataXml.toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
        }) { repositoryUrl ->
            assertEquals("2.1.0", coordinates.resolveLatestVersion(repositoryUrl, cacheDirectory = cacheDir))
            assertEquals(1, requests.get())
            assertEquals(null, lastReceivedEtag.get())

            assertEquals("2.1.0", coordinates.resolveLatestVersion(repositoryUrl, cacheDirectory = cacheDir))
            assertEquals(2, requests.get())
            assertEquals("\"v2.1.0-etag\"", lastReceivedEtag.get())
        }
    }

    @Test
    fun `resolve latest version from cache when offline`() {
        val requests = AtomicInteger()
        withServer(statusCode = 200, body = metadataXml) { repositoryUrl, serverRequests ->
            assertEquals("2.1.0", coordinates.resolveLatestVersion(repositoryUrl, cacheDirectory = cacheDir))
            assertEquals(1, serverRequests.get())

            assertEquals(
                "2.1.0",
                coordinates.resolveLatestVersion(repositoryUrl, cacheDirectory = cacheDir, offline = true),
            )
            assertEquals(1, serverRequests.get())
        }
    }

    @Test
    fun `resolve latest version falls back to cache on server error`() {
        val shouldFail = AtomicBoolean(false)
        val requests = AtomicInteger()

        withCustomServer({ exchange ->
            requests.incrementAndGet()
            if (shouldFail.get()) {
                exchange.sendResponseHeaders(500, -1)
            } else {
                val response = metadataXml.toByteArray()
                exchange.sendResponseHeaders(200, response.size.toLong())
                exchange.responseBody.use { it.write(response) }
            }
        }) { repositoryUrl ->
            assertEquals("2.1.0", coordinates.resolveLatestVersion(repositoryUrl, cacheDirectory = cacheDir))
            assertEquals(1, requests.get())

            shouldFail.set(true)
            assertEquals("2.1.0", coordinates.resolveLatestVersion(repositoryUrl, cacheDirectory = cacheDir))
            assertEquals(2, requests.get())
        }
    }

    private fun withServer(
        statusCode: Int,
        body: String = "",
        action: (repositoryUrl: String, requests: AtomicInteger) -> Unit,
    ) {
        val requests = AtomicInteger()
        withCustomServer({ exchange ->
            requests.incrementAndGet()
            val response = body.toByteArray()
            exchange.sendResponseHeaders(statusCode, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }) { repositoryUrl ->
            action(repositoryUrl, requests)
        }
    }

    private fun withCustomServer(
        handler: HttpHandler,
        action: (repositoryUrl: String) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/org/example/example-plugin/maven-metadata.xml", handler)
            start()
        }

        try {
            action("http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }
}
