// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.AndroidStudioReleases
import org.jetbrains.intellij.platform.gradle.models.JetBrainsIdesReleases
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.tasks.PrintProductsReleasesTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import org.jetbrains.intellij.platform.gradle.validateVersion
import java.net.URI

/**
 * Provides a complete list of binary IntelliJ Platform product releases matching the given [FilterParameters] criteria.
 *
 * Its main purpose is to feed the IntelliJ Plugin Verifier with a list of all compatible IDEs for the binary plugin verification.
 *
 * @see PrintProductsReleasesTask
 * @see VerifyPluginTask
 * @see GradleProperties.ProductsReleasesJetBrainsIdesUrl
 * @see GradleProperties.ProductsReleasesAndroidStudioUrl
 */
abstract class ProductReleasesValueSource : ValueSource<List<String>, ProductReleasesValueSource.Parameters> {

    interface Parameters : FilterParameters {
        /**
         * The URL to the resource containing the XML with all JetBrains IDEs releases.
         *
         * @see GradleProperties.ProductsReleasesJetBrainsIdesUrl
         */
        val jetbrainsIdesUrl: Property<String>

        /**
         * The URL to the resource containing the XML with all Android Studio releases.
         *
         * @see GradleProperties.ProductsReleasesAndroidStudioUrl
         */
        val androidStudioUrl: Property<String>
    }

    interface FilterParameters : ValueSourceParameters {
        /**
         * The build number from which the binary IDE releases will be matched.
         */
        @get:Input
        @get:Optional
        val sinceBuild: Property<String>

        /**
         * Build number until which the binary IDE releases will be matched.
         */
        @get:Input
        @get:Optional
        val untilBuild: Property<String>

        /**
         * A list of [IntelliJPlatformType] types to match.
         */
        @get:Input
        @get:Optional
        val types: ListProperty<IntelliJPlatformType>

        /**
         * A list of [Channel] types of binary releases to search in.
         */
        @get:Input
        @get:Optional
        val channels: ListProperty<Channel>
    }

    private val log = Logger(javaClass)

    override fun obtain() = loadProductReleases(
        jetbrainsIdesUrl = parameters.jetbrainsIdesUrl.orNull,
        androidStudioUrl = parameters.androidStudioUrl.orNull,
        loader = { URI(it).toURL().readText() },
        log = log,
    ).toNotations(parameters, log)
}

internal fun loadProductReleases(
    jetbrainsIdesUrl: String?,
    androidStudioUrl: String?,
    loader: (String) -> String?,
    log: Logger,
): List<ProductRelease> {
    val jetbrainsIdesReleases = jetbrainsIdesUrl
        ?.let(loader)
        ?.also { log.info("Reading JetBrains IDEs releases from URL: $jetbrainsIdesUrl") }
        ?.let { decode<JetBrainsIdesReleases>(it) }
        ?.let { releases ->
            sequence {
                releases.products.forEach { product ->
                    product.channels.forEach channel@{ channelEntry ->
                        channelEntry.builds.forEach { build ->
                            product.codes.forEach codes@{ code ->
                                val type = runCatching {
                                    code.toIntelliJPlatformType(build.fullNumber).validateVersion(build.fullNumber)
                                }.getOrNull() ?: return@codes

                                val channel =
                                    runCatching { Channel.valueOf(channelEntry.status.uppercase()) }.getOrElse { return@channel }

                                yield(
                                    ProductRelease(
                                        name = product.name,
                                        type = type,
                                        channel = channel,
                                        build = build.fullNumber.toVersion(),
                                        version = build.version.toVersion(),
                                        id = when (channel) {
                                            Channel.RELEASE -> with(build.version.toVersion()) {
                                                val id = "$major.$minor" + ".$patch".takeIf { patch > 0 }.orEmpty()
                                                version.takeIf { it.startsWith(id) } ?: id
                                            }

                                            else -> build.fullNumber
                                        },
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
        .orEmpty()
        .toList()

    val androidStudioReleases = androidStudioUrl
        ?.let(loader)
        ?.also { log.info("Reading Android Studio releases from: $androidStudioUrl") }
        ?.let { decode<AndroidStudioReleases>(it) }
        ?.items
        ?.mapNotNull { item ->
            val channel =
                runCatching { Channel.valueOf(item.channel.uppercase()) }.getOrNull() ?: return@mapNotNull null

            ProductRelease(
                name = item.name,
                type = IntelliJPlatformType.AndroidStudio,
                channel = channel,
                build = item.platformBuild.toVersion(),
                version = item.platformVersion.toVersion(),
                id = item.version,
            )
        }
        .orEmpty()

    return jetbrainsIdesReleases + androidStudioReleases
}

internal fun List<ProductRelease>.toNotations(
    parameters: ProductReleasesValueSource.FilterParameters,
    log: Logger,
): List<String> {
    val since = parameters.sinceBuild.get().toVersion()
    val until = parameters.untilBuild.orNull?.ifBlank { null }?.replace("*", "99999")?.toVersion()

    fun ProductRelease.testVersion(): Boolean {
        fun getComparativeVersion(version: Version) = when (version.major) {
            in 100..999 -> build
            else -> this.version
        }
        return getComparativeVersion(since) >= since && (until?.let { getComparativeVersion(it) <= it } != false)
    }

    val codes = parameters.types.get().map { it.code }.toSet()
    val selectedChannels = parameters.channels.get().toSet()
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

    val latestReleases = linkedMapOf<String, ProductRelease>()
    asSequence()
        .filter { it.testType() }
        .filter { it.channel in selectedChannels }
        .filter { it.testVersion() }
        .forEach { release ->
            val key = "${release.type.code}-${release.version.major}.${release.version.minor}"
            val current = latestReleases[key]
            if (current == null || release.build > current.build) {
                latestReleases[key] = release
            }
        }

    return latestReleases
        .values
        .map { "${it.type.code}-${it.id}" }
        .also { log.info("Resolved values: ${it.joinToString(",")}") }
}
