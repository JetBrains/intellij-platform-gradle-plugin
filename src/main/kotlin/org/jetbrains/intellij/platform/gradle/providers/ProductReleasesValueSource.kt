// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import kotlinx.serialization.Serializable
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.gradle.api.file.RegularFileProperty
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
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.toVersion

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
         * A file containing the XML with all available JetBrains IDEs releases.
         *
         * @see jetbrainsIdesUrl
         */
        val jetbrainsIdes: RegularFileProperty

        /**
         * A file containing the XML with all available Android Studio releases.
         *
         * @see androidStudioUrl
         */
        val androidStudio: RegularFileProperty

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

    override fun obtain() = with(parameters) {
        val jetbrainsIdesReleases = jetbrainsIdes.orNull
            ?.also { log.info("Reading JetBrains IDEs releases from: $it (jetbrainsIdesUrl=$jetbrainsIdesUrl)") }
            ?.let { decode<JetBrainsIdesReleases>(it.asPath) }
            ?.let {
                sequence {
                    it.products.forEach { product ->
                        product.channels.forEach channel@{ channelEntry ->
                            channelEntry.builds.forEach { build ->
                                product.codes.forEach codes@{ code ->
                                    val type = runCatching { code.toIntelliJPlatformType() }.getOrElse { return@codes }
                                    val channel = runCatching { Channel.valueOf(channelEntry.status.uppercase()) }.getOrElse { return@channel }

                                    yield(
                                        ProductRelease(
                                            name = product.name,
                                            type = type,
                                            channel = channel,
                                            build = build.fullNumber.toVersion(),
                                            version = build.version.toVersion(),
                                            id = when (channel) {
                                                Channel.RELEASE -> with(build.version.toVersion()) {
                                                    "$major.$minor" + (".$patch".takeIf { patch > 0 }.orEmpty())
                                                }

                                                else -> build.fullNumber
                                            }
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
            .orEmpty()
            .toList()

        val androidStudioReleases = androidStudio.orNull
            ?.also { log.info("Reading Android Studio releases from: ${androidStudio.orNull} (androidStudioUrl=$androidStudioUrl)") }
            ?.let { decode<AndroidStudioReleases>(it.asPath) }
            ?.items
            ?.mapNotNull { item ->
                val channel = runCatching { Channel.valueOf(item.channel.uppercase()) }.getOrNull() ?: return@mapNotNull null

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

        val since = sinceBuild.get().toVersion()
        val until = untilBuild.map { it.replace("*", "99999") }.orNull?.ifBlank { null }?.toVersion()
        fun ProductRelease.testVersion(): Boolean {
            fun getComparativeVersion(version: Version) = when (version.major) {
                in 100..999 -> build
                else -> this.version
            }
            return getComparativeVersion(since) >= since && (until?.let { getComparativeVersion(it) <= it } != false)
        }

        val types = types.get()
        val channels = channels.get()

        log.info("Filtering releases with since='$since', until='$until', types='${types.joinToString(",")}', channels='${channels.joinToString(",")}'")

        (jetbrainsIdesReleases + androidStudioReleases)
            .asSequence()
            .filter { it.type in types }
            .filter { it.channel in channels }
            .filter { it.testVersion() }
            .groupBy { "${it.type.code}-${it.version.major}.${it.version.minor}" }
            .values
            .map { it.maxBy { release -> release.build } }
            .map { "${it.type.code}-${it.id}" }
            .also { log.info("Resolved values: ${it.joinToString(",")}") }
    }
}

@Serializable
data class Data(
    @XmlSerialName("item")
    val items: List<Item>,
) {
    @Serializable
    data class Item(val name: String)
}
