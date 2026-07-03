// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.invocation.Gradle
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel
import org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.net.URI
import java.nio.file.Path
import java.security.MessageDigest
import java.time.LocalDate
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.io.path.createDirectories
import kotlin.io.path.readText
import kotlin.io.path.writeText

abstract class ProductReleasesService @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val providerFactory: ProviderFactory,
) : BuildService<ProductReleasesService.Parameters> {

    interface Parameters : BuildServiceParameters {
        /**
         * The URL to the resource containing the JSON with JetBrains IDEs releases.
         *
         * @see GradleProperties.ProductsReleasesCdnBuildsUrl
         */
        @get:Input
        val jetbrainsIdesUrl: Property<String>

        /**
         * The URL to the resource containing the JSON with all Android Studio releases.
         *
         * @see GradleProperties.ProductsReleasesAndroidStudioUrl
         */
        @get:Input
        val androidStudioUrl: Property<String>

        /**
         * Directory used to cache raw product release JSON listings across Gradle invocations.
         */
        @get:Input
        val cacheDirectory: Property<String>
    }

    private val log = Logger(javaClass)
    private val productReleases = ConcurrentHashMap<String, List<ProductRelease>>()

    internal fun resolve(type: IntelliJPlatformType, version: Version) = resolve {
        types = listOf(type)
    }.map { productReleases ->
        productReleases.find { it.matchesVersion(version) }
    }

    internal fun resolve(configure: ProductReleasesValueSource.FilterParameters.() -> Unit) =
        objectFactory.newInstance<ProductReleasesValueSource.FilterParameters>()
            .apply(configure)
            .let { parameters ->
                providerFactory.provider { resolve(parameters) }
            }

    internal fun resolve(
        filter: ProductReleasesValueSource.FilterParameters,
        loader: (String) -> String? = { URI(it).toURL().readText() },
    ) = filter.types.get()
        .map { loadProductReleases(it, loader) }
        .flatten()
        .run {
            val since = filter.sinceBuild.orNull?.ifBlank { "0" }?.toVersion()
            val until = filter.untilBuild.orNull?.ifBlank { null }?.replace("*", "99999")?.toVersion()

            fun ProductRelease.testVersion(): Boolean {
                fun getComparativeVersion(version: Version) = when (version.major) {
                    in 100..999 -> build
                    else -> this.version
                }
                return (since?.let { getComparativeVersion(it) >= it } != false) && (until?.let {
                    getComparativeVersion(
                        it,
                    ) <= it
                } != false)
            }

            val codes = filter.types.get().map { it.code }.toSet()
            val selectedChannels = filter.channels.get().toSet()
            fun ProductRelease.testType(): Boolean {
                if (type.code in codes) {
                    return true
                }

                // Allow IU if IC is present and version is 253+ (2025.3+)
                if (type.code == "IU" && "IC" in codes && build.major >= 253) {
                    return true
                }

                // Allow PY if PC is present and version is 253+ (2025.3+)
                if (type.code == "PY" && "PC" in codes && build.major >= 253) {
                    return true
                }

                return false
            }

            log.info(
                "Filtering releases with since='$since', until='$until', types='${codes.joinToString(",")}', channels='${
                    selectedChannels.joinToString(",")
                }'",
            )

            asSequence()
                .filter { it.testType() }
                .filter { selectedChannels.isEmpty() || it.channel in selectedChannels }
                .filter { it.testVersion() }
                .toSet()
        }

    private fun loadProductReleases(type: IntelliJPlatformType, loader: (String) -> String?): List<ProductRelease> {
        val url = when (type) {
            IntelliJPlatformType.AndroidStudio -> parameters.androidStudioUrl.orNull
            else -> parameters.jetbrainsIdesUrl.orNull?.replace("{type}", type.code)
        } ?: return emptyList()

        return productReleases.computeIfAbsent("${type.code}:$url") {
            loadListing(url, loader)?.let { content ->
                when (type) {
                    IntelliJPlatformType.AndroidStudio -> decode<AndroidStudioReleases>(content).toProductReleases()
                    else -> decode<List<JetBrainsProductReleases>>(content, stringFormat = json)
                        .firstOrNull()
                        ?.toProductReleases()
                        .orEmpty()
                }
            }.orEmpty()
        }
    }

    private fun loadListing(url: String, loader: (String) -> String?): String? {
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
            loader(url)?.also {
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
        } ?: cachedContent
    }

    private fun cacheFile(url: String): Path {
        val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        val fileName = "${HexFormat.of().formatHex(digest)}.json"
        return Path.of(parameters.cacheDirectory.get()).resolve(fileName)
    }

    private fun lockFile(cacheFile: Path) = cacheFile.resolveSibling("${cacheFile.fileName}.lock")
}

internal fun Gradle.productReleasesService(providers: ProviderFactory, rootProjectDirectory: Path) =
    registerClassLoaderScopedBuildService(ProductReleasesService::class) {
        parameters {
            jetbrainsIdesUrl = providers[GradleProperties.ProductsReleasesCdnBuildsUrl]
            androidStudioUrl = providers[GradleProperties.ProductsReleasesAndroidStudioUrl]
            cacheDirectory =
                providers.intellijPlatformProductReleasesCachePath(rootProjectDirectory).map { it.safePathString }
        }
    }

private val jetBrainsProductCodeAliases = mapOf(
    "IIC" to "IC",
    "IIU" to "IU",
    "PCC" to "PC",
    "PCP" to "PY",
)

internal fun JetBrainsProductReleases.toProductReleases() =
    releases.mapNotNull { release ->
        val channel = runCatching {
            Channel.valueOf(release.type.uppercase())
        }.getOrNull() ?: return@mapNotNull null

        val platformType = runCatching {
            (jetBrainsProductCodeAliases[code] ?: code)
                .toIntelliJPlatformType(release.build)
                .validateVersion(release.build)
        }.getOrNull() ?: return@mapNotNull null

        ProductRelease(
            type = platformType,
            name = "...",
            channel = channel,
            version = release.version.toVersion(),
            build = release.build.toVersion(),
            downloads = release.downloads.map { (kind, download) ->
                ProductRelease.Download(
                    kind = kind,
                    link = download.link,
                    checksumLink = download.checksumLink,
                )
            },
        )
    }

internal fun AndroidStudioReleases.toProductReleases() =
    items.mapNotNull { item ->
        val channel = runCatching {
            Channel.valueOf(item.channel.uppercase())
        }.getOrNull() ?: return@mapNotNull null

        ProductRelease(
            type = IntelliJPlatformType.AndroidStudio,
            name = item.name,
            channel = channel,
            version = item.version.toVersion(),
            build = item.build.toVersion(),
            platformVersion = item.platformVersion.toVersion(),
            platformBuild = item.platformBuild.toVersion(),
            downloads = item.downloads.map { download ->
                ProductRelease.Download(
                    kind = null,
                    link = download.link,
                    checksum = download.checksum,
                )
            },
        )
    }

internal fun Set<ProductRelease>.latestReleases(): Set<ProductRelease> {
    val latestReleases = linkedMapOf<String, ProductRelease>()

    forEach { release ->
        val key = "${release.type.code}-${release.version.major}.${release.version.minor}"
        val current = latestReleases[key]
        if (current == null || release.build > current.build) {
            latestReleases[key] = release
        }
    }

    return latestReleases.values.toSet()
}
