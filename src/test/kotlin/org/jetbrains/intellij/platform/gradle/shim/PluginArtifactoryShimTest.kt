// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.shim

import com.sun.net.httpserver.HttpServer
import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.api.credentials.HttpHeaderCredentials
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.intellij.platform.gradle.CustomPluginRepositoryType
import org.jetbrains.intellij.platform.gradle.artifacts.repositories.PluginArtifactRepository
import java.net.*
import java.util.*
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginArtifactoryShimTest {

    private val upstreamServers = mutableListOf<HttpServer>()
    private val shimServers = mutableListOf<Shim.Server>()

    @AfterTest
    fun tearDown() {
        shimServers.forEach { it.close() }
        upstreamServers.forEach { it.stop(0) }
    }

    @Test
    fun `password credentials are forwarded to listing and same-host downloads`() {
        val expectedAuthorization = "Basic ${Base64.getEncoder().encodeToString("user:password".toByteArray())}"

        assertRequests(
            configureCredentials = { repository ->
                repository.credentials(PasswordCredentials::class.java) {
                    username = "user"
                    password = "password"
                }
            },
            expectedHeaders = mapOf("Authorization" to expectedAuthorization),
        )
    }

    @Test
    fun `header credentials are forwarded to listing and same-host downloads`() {
        assertRequests(
            configureCredentials = { repository ->
                repository.credentials(HttpHeaderCredentials::class.java) {
                    name = "X-Repository-Token"
                    value = "token"
                }
            },
            expectedHeaders = mapOf("X-Repository-Token" to "token"),
        )
    }

    @Test
    fun `empty password credentials do not produce an authorization header`() {
        assertRequests(
            configureCredentials = { repository ->
                repository.credentials(PasswordCredentials::class.java) {}
            },
            expectedHeaders = emptyMap(),
        )
    }

    private fun assertRequests(
        configureCredentials: (PluginArtifactRepository) -> Unit,
        expectedHeaders: Map<String, String>,
    ) {
        val requests = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        val pluginZip = "plugin archive".toByteArray()
        val listing = """
            <plugins>
              <plugin id="test.plugin" version="1.0" url="/plugin.zip" />
            </plugins>
        """.trimIndent().toByteArray()
        val upstream = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { exchange ->
                requests += exchange.requestURI.path to exchange.requestHeaders.entries
                    .associate { (name, values) -> name to values.first() }
                val content = when (exchange.requestURI.path) {
                    "/plugins.xml" -> listing
                    "/plugin.zip" -> pluginZip
                    else -> ByteArray(0)
                }
                exchange.sendResponseHeaders(if (content.isEmpty()) 404 else 200, content.size.toLong())
                exchange.responseBody.use { it.write(content) }
            }
            start()
            upstreamServers += this
        }
        val repository = ProjectBuilder.builder().build().objects.newInstance(
            PluginArtifactRepository::class.java,
            "Test custom plugin repository",
            URI("http://127.0.0.1:${upstream.address.port}/plugins.xml"),
            CustomPluginRepositoryType.SIMPLE,
            true,
        ).apply(configureCredentials)
        val shim = PluginArtifactoryShim(repository, availablePort()).start().also(shimServers::add)
        val pluginPath = "/com.jetbrains.plugins/test.plugin/1.0"

        val descriptor =
            URL(shim.url.resolve("$pluginPath/descriptor.ivy").toString()).openConnection() as HttpURLConnection
        descriptor.apply {
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        assertEquals(200, descriptor.responseCode)
        assertTrue(descriptor.inputStream.use { it.readBytes().isNotEmpty() })

        val download = URL(shim.url.resolve("$pluginPath/download").toString()).openConnection() as HttpURLConnection
        download.apply {
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        assertEquals(pluginZip.toList(), download.getInputStream().use { it.readBytes().toList() })

        assertEquals(listOf("/plugins.xml", "/plugin.zip"), requests.map { it.first })
        requests.forEach { (_, headers) ->
            expectedHeaders.forEach { (name, value) ->
                assertEquals(value, headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value)
            }
            if (expectedHeaders.isEmpty()) {
                assertEquals(
                    null,
                    headers.entries.firstOrNull { it.key.equals("Authorization", ignoreCase = true) }?.value,
                )
            }
        }
    }

    private fun availablePort() = ServerSocket(0).use { it.localPort }
}
