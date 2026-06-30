// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.validateVersion

internal data class ProductReleaseCatalogEntry(
    val type: IntelliJPlatformType,
    val name: String?,
    val channel: ProductRelease.Channel,
    val version: String,
    val build: String,
    val platformVersion: String = version,
    val platformBuild: String = build,
    val downloads: List<ProductDownload> = emptyList(),
)

internal data class ProductDownload(
    val kind: String?,
    val link: String,
    val checksum: String? = null,
    val checksumLink: String? = null,
)

internal fun JetBrainsProductReleases.toCatalogEntries(type: IntelliJPlatformType) =
    releases.mapNotNull { release ->
        val channel = runCatching {
            ProductRelease.Channel.valueOf(release.type.uppercase())
        }.getOrNull() ?: return@mapNotNull null

        val platformType = runCatching {
            type.toIntelliJPlatformType(release.build).validateVersion(release.build)
        }.getOrNull() ?: return@mapNotNull null

        ProductReleaseCatalogEntry(
            type = platformType,
            name = null,
            channel = channel,
            version = release.version,
            build = release.build,
            downloads = release.downloads.map { (kind, download) ->
                ProductDownload(
                    kind = kind,
                    link = download.link,
                    checksumLink = download.checksumLink,
                )
            },
        )
    }

internal fun AndroidStudioReleases.toCatalogEntries() =
    items.mapNotNull { item ->
        val channel = runCatching {
            ProductRelease.Channel.valueOf(item.channel.uppercase())
        }.getOrNull() ?: return@mapNotNull null

        ProductReleaseCatalogEntry(
            type = IntelliJPlatformType.AndroidStudio,
            name = item.name,
            channel = channel,
            version = item.version,
            build = item.build,
            platformVersion = item.platformVersion,
            platformBuild = item.platformBuild,
            downloads = item.downloads.map { download ->
                ProductDownload(
                    kind = null,
                    link = download.link,
                    checksum = download.checksum,
                )
            },
        )
    }
