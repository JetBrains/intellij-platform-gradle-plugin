// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Input
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel
import org.jetbrains.intellij.platform.gradle.providers.*
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import org.jetbrains.intellij.platform.gradle.validateVersion
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.collections.asSequence
import kotlin.collections.map
import kotlin.collections.toSet
import kotlin.text.get

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
    }

    private val log = Logger(javaClass)
    private val productReleases = ConcurrentHashMap<String, List<ProductRelease>>()

    internal fun resolve(type: IntelliJPlatformType, version: Version) = resolve {
        types = listOf(type)
    }.map { productReleases ->
        productReleases.find {
            version == when {
                version.major in 100..999 -> it.build
                type == IntelliJPlatformType.AndroidStudio -> it.version
                it.channel == Channel.RELEASE -> it.version
                else -> it.build
            }
        }
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
        .mapNotNull { type ->
            // TODO: read from cache first? with productReleases
            when (type) {
                IntelliJPlatformType.AndroidStudio -> {
                    parameters.androidStudioUrl.orNull
                        ?.also { log.info("Reading Android Studio releases from: $it") }
                        ?.let(loader)
                        ?.let { decode<AndroidStudioReleases>(it) }
                        ?.toProductReleases()
                }

                else -> {
                    parameters.jetbrainsIdesUrl.orNull
                        ?.replace("{type}", type.code)
                        ?.let(loader)
                        ?.also { log.info("Reading JetBrains IDEs releases from URL: $it") }
                        ?.let { decode<List<JetBrainsProductReleases>>(it, stringFormat = json) }
                        ?.firstOrNull()
                        ?.toProductReleases()
                }
            }
        }
        .flatten()
        .run {
            val since = filter.sinceBuild.orNull?.ifBlank { "0" }?.toVersion()
            val until = filter.untilBuild.orNull?.ifBlank { null }?.replace("*", "99999")?.toVersion()

            fun ProductRelease.testVersion(): Boolean {
                fun getComparativeVersion(version: Version) = when (version.major) {
                    in 100..999 -> build
                    else -> this.version
                }
                return (since?.let { getComparativeVersion(it) >= it} != false) && (until?.let { getComparativeVersion(it) <= it } != false)
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
            ProductRelease.Channel.valueOf(item.channel.uppercase())
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
