// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.util.HexFormat
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * Loads a raw product releases listing while hiding the cache file implementation details from configuration cache
 * file-system input tracking.
 */
internal abstract class ProductReleasesListingValueSource :
    ValueSource<String, ProductReleasesListingValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        @get:Input
        val url: Property<String>

        @get:Input
        val cacheDirectory: Property<String>
    }

    private val log = Logger(javaClass)

    override fun obtain(): String? {
        val url = parameters.url.get()
        val cacheFile = cacheFile(url)
        val lockFile = lockFile(cacheFile)
        val today = LocalDate.now().toString()
        val cachedContent = runCatching { cacheFile.readText() }.getOrNull()
        val lastUpdate = runCatching { lockFile.readText().trim() }.getOrNull()

        if (cachedContent != null && lastUpdate == today) {
            log.info("Reading product releases listing from cache: $cacheFile")
            return cachedContent
        }

        return try {
            URI(url).toURL().readText().also {
                log.info("Reading product releases listing from URL: $url")
                cacheFile.parent.createDirectories()
                cacheFile.writeText(it)
                lockFile.writeText(today)
            }
        } catch (e: Exception) {
            if (cachedContent == null) {
                throw e
            }

            log.warn("Failed to refresh product releases listing from URL: $url. Using cached listing: $cacheFile", e)
            cachedContent
        }
    }

    private fun cacheFile(url: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val fileName = "${HexFormat.of().formatHex(digest)}.json"
        return Path.of(parameters.cacheDirectory.get()).resolve(fileName)
    }

    private fun lockFile(cacheFile: Path) = cacheFile.resolveSibling("${cacheFile.fileName}.lock")
}
