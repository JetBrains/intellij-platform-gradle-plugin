// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.jetbrains.intellij.platform.gradle.utils.CachedHttpResource
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.offlineResolutionErrorMessage
import java.nio.file.Path

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

        /**
         * Whether Gradle runs in offline mode. Captured at configuration time from
         * [org.gradle.StartParameter.isOffline] so this [ValueSource] never reads `gradle.startParameter` itself.
         */
        @get:Input
        val offline: Property<Boolean>
    }

    private val log = Logger(javaClass)

    override fun obtain(): String? {
        val url = parameters.url.get()
        val cacheDirectory = Path.of(parameters.cacheDirectory.get())
        val offline = parameters.offline.getOrElse(false)

        return CachedHttpResource.read(
            url = url,
            cacheDirectory = cacheDirectory,
            offline = offline,
            onCacheHit = { file, is304 ->
                if (is304) {
                    log.info("Product releases listing not modified: $url (304), reusing cached listing: $file")
                }
            },
            onRefresh = { _, _ ->
                log.info("Reading product releases listing from URL: $url")
            },
            onOfflineCacheHit = { file ->
                log.info("Offline mode: reusing cached product releases listing: $file")
            },
            onOfflineCacheMiss = {
                log.warn(offlineResolutionErrorMessage("product releases listing from $url"))
            },
            onErrorFallback = { _, file, e ->
                log.warn("Failed to refresh product releases listing from URL: $url. Using cached listing: $file", e)
            },
        )
    }
}
