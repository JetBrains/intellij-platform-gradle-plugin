// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.provider

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.model.AndroidStudioReleases
import org.jetbrains.intellij.platform.gradle.model.JetBrainsIdesReleases
import org.jetbrains.intellij.platform.gradle.model.ProductRelease
import org.jetbrains.intellij.platform.gradle.model.ProductRelease.Channel
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import org.jetbrains.intellij.platform.gradle.utils.Version

abstract class ProductReleasesValueSource : ValueSource<List<String>, ProductReleasesValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        val jetbrainsIdes: RegularFileProperty
        val androidStudio: RegularFileProperty
        val sinceBuild: Property<String>
        val untilBuild: Property<String>

        // TODO: make it a list!!!
        val type: Property<IntelliJPlatformType>
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

        (jetbrainsIdesReleases + androidStudioReleases)
            .filter { it.type == type.get() }
            .filter { it.channel in channels.get() }
            .filter { it.testVersion() }
            .groupBy { "${it.type.code}-${it.version.major}.${it.version.minor}" }
            .values
            .map { it.maxBy { release -> release.version } }
            .map { "${it.type.code}-${it.id}" }
    }
}
