// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import java.net.HttpURLConnection
import java.net.URI

internal object Http {

    const val CONNECT_TIMEOUT = 10_000
    const val READ_TIMEOUT = 30_000

    /**
     * Opens an [HttpURLConnection] for [url] with default connect and read timeouts, configures it,
     * invokes [block], and guarantees deterministic connection and error stream cleanup in a `finally` block.
     */
    fun <T> openConnection(
        url: String,
        configure: (HttpURLConnection.() -> Unit)? = null,
        block: (HttpURLConnection) -> T,
    ): T {
        val connection = URI(url).toURL().openConnection().apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
            if (this is HttpURLConnection) {
                configure?.invoke(this)
            }
        }

        return try {
            block(connection as HttpURLConnection)
        } finally {
            if (connection is HttpURLConnection) {
                runCatching { connection.errorStream?.close() }
                connection.disconnect()
            }
        }
    }

    /**
     * Checks if the given [url] uses the HTTP or HTTPS protocol scheme.
     */
    fun isHttp(url: String): Boolean {
        val scheme = runCatching { URI(url).scheme }.getOrNull()
        return scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
    }

    /**
     * Reads the entire text content from [url] using default connect and read timeouts.
     */
    fun readUrl(url: String): String {
        val connection = URI(url).toURL().openConnection().apply {
            connectTimeout = CONNECT_TIMEOUT
            readTimeout = READ_TIMEOUT
        }
        return connection.inputStream.bufferedReader().use { it.readText() }
    }
}
