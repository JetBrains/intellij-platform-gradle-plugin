// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import java.io.IOException
import java.net.HttpURLConnection
import java.nio.file.Path
import java.security.MessageDigest
import java.util.HexFormat
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.readText
import kotlin.io.path.writeText

internal object CachedHttpResource {

    /**
     * Reads a remote or cached resource from [url] using persistent file caching and conditional HTTP requests.
     *
     * In online mode, sends conditional `If-None-Match` (ETag) and `If-Modified-Since` (Last-Modified) headers
     * when cached content exists. Reuses the cache on `304 Not Modified` without transferring payload, or falls
     * back to the existing cache when a network or server error occurs.
     */
    fun read(
        url: String,
        cacheDirectory: Path,
        extension: String = "json",
        offline: Boolean = false,
        onCacheHit: (cacheFile: Path, is304: Boolean) -> Unit = { _, _ -> },
        onRefresh: (content: String, cacheFile: Path) -> Unit = { _, _ -> },
        onOfflineCacheHit: (cacheFile: Path) -> Unit = { _ -> },
        onOfflineCacheMiss: () -> Unit = {},
        onErrorFallback: (url: String, cacheFile: Path, error: Throwable) -> Unit = { _, _, _ -> },
    ): String? {
        val cacheFile = cacheFile(url, cacheDirectory, extension)
        val cachedContent = runCatching { cacheFile.readText() }.getOrNull()

        if (offline) {
            if (cachedContent != null) {
                onOfflineCacheHit(cacheFile)
                return cachedContent
            }
            onOfflineCacheMiss()
            return null
        }

        return try {
            if (Http.isHttp(url)) {
                readHttp(url, cacheFile, cachedContent, onCacheHit, onRefresh)
            } else {
                Http.readUrl(url).also {
                    writeCache(cacheFile, it)
                    onRefresh(it, cacheFile)
                }
            }
        } catch (e: Exception) {
            if (cachedContent == null) {
                throw e
            }
            onErrorFallback(url, cacheFile, e)
            cachedContent
        }
    }

    /**
     * Computes the deterministic cache file path for [url] within [cacheDirectory].
     */
    fun cacheFile(url: String, cacheDirectory: Path, extension: String = "json"): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val fileName = "${HexFormat.of().formatHex(digest)}.$extension"
        return cacheDirectory.resolve(fileName)
    }

    private fun readHttp(
        url: String,
        cacheFile: Path,
        cachedContent: String?,
        onCacheHit: (cacheFile: Path, is304: Boolean) -> Unit,
        onRefresh: (content: String, cacheFile: Path) -> Unit,
    ): String {
        val etagFile = etagFile(cacheFile)
        val lastModifiedFile = lastModifiedFile(cacheFile)

        return Http.openConnection(
            url = url,
            configure = {
                if (cachedContent != null) {
                    readMetadata(etagFile)?.let { setRequestProperty("If-None-Match", it) }
                    readMetadata(lastModifiedFile)?.let { setRequestProperty("If-Modified-Since", it) }
                }
            },
        ) { connection ->
            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    onCacheHit(cacheFile, true)
                    cachedContent ?: throw IOException("Received 304 Not Modified but cached content is missing")
                }

                in 200..299 -> {
                    val content = connection.inputStream.bufferedReader().use { it.readText() }
                    writeCache(cacheFile, content)
                    writeMetadata(etagFile, connection.getHeaderField("ETag"))
                    writeMetadata(lastModifiedFile, connection.getHeaderField("Last-Modified"))
                    onRefresh(content, cacheFile)
                    content
                }

                else -> throw IOException("Failed to fetch from URL: $url with response code: $responseCode")
            }
        }
    }

    private fun writeCache(cacheFile: Path, content: String) {
        cacheFile.parent.createDirectories()
        cacheFile.writeText(content)
    }

    private fun readMetadata(file: Path) = runCatching { file.readText().trim() }
        .getOrNull()
        ?.takeIf { it.isNotBlank() }

    private fun writeMetadata(file: Path, value: String?) {
        if (value == null) {
            runCatching { file.deleteIfExists() }
        } else {
            file.writeText(value)
        }
    }

    private fun etagFile(cacheFile: Path) = cacheFile.resolveSibling("${cacheFile.fileName}.etag")

    private fun lastModifiedFile(cacheFile: Path) = cacheFile.resolveSibling("${cacheFile.fileName}.last-modified")
}
