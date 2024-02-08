// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.provider

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.*
import org.gradle.api.resources.ResourceHandler
import org.jetbrains.intellij.model.JetBrainsIdesReleases
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.model.AndroidStudioReleases
import org.jetbrains.intellij.platform.gradle.model.ProductRelease
import org.jetbrains.intellij.platform.gradle.model.ProductRelease.Channel
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import org.jetbrains.intellij.platform.gradle.provider.ProductReleasesValueSource.FilterParameters
import org.jetbrains.intellij.platform.gradle.tasks.PrintProductsReleasesTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.*

/**
 * Provides a complete list of binary IntelliJ Platform product releases matching the given [FilterParameters] criteria.
 *
 * Its main purpose is to feed the IntelliJ Plugin Verifier with a list of all compatible IDEs for the binary plugin verification.
 *
 * @see PrintProductsReleasesTask
 * @see VerifyPluginTask
 * @see Locations.PRODUCTS_RELEASES_JETBRAINS_IDES
 * @see Locations.PRODUCTS_RELEASES_ANDROID_STUDIO
 */
abstract class ProductReleasesValueSource : ValueSource<List<String>, ProductReleasesValueSource.Parameters> {

    interface Parameters : FilterParameters {
        /**
         * A file containing the XML with all available JetBrains IDEs releases.
         *
         * @see Locations.PRODUCTS_RELEASES_JETBRAINS_IDES
         */
        val jetbrainsIdes: RegularFileProperty

        /**
         * A file containing the XML with all available Android Studio releases.
         *
         * @see Locations.PRODUCTS_RELEASES_ANDROID_STUDIO
         */
        val androidStudio: RegularFileProperty
    }

    interface FilterParameters : ValueSourceParameters {
        /**
         * Build number from which the binary IDE releases will be matched.
         */
        val sinceBuild: Property<String>

        /**
         * Build number until which the binary IDE releases will be matched.
         */
        val untilBuild: Property<String>

        /**
         * A list of [IntelliJPlatformType] types to match.
         */
        val types: ListProperty<IntelliJPlatformType>

        /**
         * A list of [Channel] types of binary releases to search in.
         */
        val channels: ListProperty<Channel>
    }

    override fun obtain(): List<String>? = with(parameters) {
        val jetbrainsIdesReleases = XmlExtractor<JetBrainsIdesReleases>()
            .fetch(jetbrainsIdes.asPath)
            .or { JetBrainsIdesReleases() }
            .let {
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
            .toList()

        val androidStudioReleases = XmlExtractor<AndroidStudioReleases>()
            .fetch(androidStudio.asPath)
            .or { AndroidStudioReleases() }
            .items.mapNotNull { item ->
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

        val since = sinceBuild.map { it.toVersion() }.get()
        val until = untilBuild.map { it.replace("*", "99999").toVersion() }.orNull
        fun ProductRelease.testVersion(): Boolean {
            fun getComparativeVersion(version: Version) = when (version.major) {
                in 100..999 -> build
                else -> this.version
            }
            return getComparativeVersion(since) >= since && (until?.let { getComparativeVersion(it) <= it } ?: true)
        }

        val types = types.get()
        val channels = channels.get()

        (jetbrainsIdesReleases + androidStudioReleases)
            .filter { it.type in types }
            .filter { it.channel in channels }
            .filter { it.testVersion() }
            .groupBy { "${it.type.code}-${it.version.major}.${it.version.minor}" }
            .values
            .map { it.maxBy { release -> release.version } }
            .map { "${it.type.code}-${it.id}" }
    }
}

/**
 * Factory for creating the [ProductReleasesValueSource] instance to simplify providing product releases XML files and default [FilterParameters] filter values.
 */
@Suppress("FunctionName")
fun ProductReleasesValueSource(
    providers: ProviderFactory,
    resources: ResourceHandler,
    extensionProvider: Provider<IntelliJPlatformExtension>,
    configure: FilterParameters.() -> Unit = {},
) = providers.of(ProductReleasesValueSource::class.java) {
    val log = Logger(ProductReleasesValueSource::class.java)

    // TODO: migrate to `project.resources.binary` whenever it's available. Ref: https://github.com/gradle/gradle/issues/25237
    fun String.resolve() = resources.text
        .fromUri(this)
        .runCatching { asFile("UTF-8") }
        .onFailure { log.error("Cannot resolve product releases", it) }
        .getOrThrow()

    val ideaVersionProvider = extensionProvider.map { it.pluginConfiguration.ideaVersion }

    with(parameters) {
        jetbrainsIdes.set(Locations.PRODUCTS_RELEASES_JETBRAINS_IDES.resolve())
        androidStudio.set(Locations.PRODUCTS_RELEASES_ANDROID_STUDIO.resolve())
        channels.convention(providers.provider { ProductRelease.Channel.values().toList() })
        types.convention(extensionProvider.flatMap {
            it.productInfo.map { productInfo ->
                listOf(productInfo.productCode.toIntelliJPlatformType())
            }
        })
        sinceBuild.convention(ideaVersionProvider.flatMap { it.sinceBuild })
        untilBuild.convention(ideaVersionProvider.flatMap { it.untilBuild })
        configure()
    }
}

/**
 * Extension function for the [IntelliJPlatformExtension.VerifyPlugin.Ides] extension to let filter IDE binary releases just using [FilterParameters].
 */
@Suppress("FunctionName")
fun IntelliJPlatformExtension.VerifyPlugin.Ides.ProductReleasesValueSource(configure: FilterParameters.() -> Unit = {}) =
    ProductReleasesValueSource(
        providers,
        resources,
        extensionProvider,
        configure,
    )
