// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.gradle.kotlin.dsl.setProperty
import org.jetbrains.intellij.Version
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.model.AndroidStudioReleases
import org.jetbrains.intellij.model.ProductsReleases
import org.jetbrains.intellij.model.XmlExtractor
import javax.inject.Inject

@Suppress("UnstableApiUsage")
open class ListProductsReleasesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    @Input
    @Optional
    val updatePaths = objectFactory.listProperty<String>()

    @Input
    @Optional
    val androidStudioUpdatePath = objectFactory.property<String>()

    @OutputFile
    val outputFile: RegularFileProperty = objectFactory.fileProperty()

    @Input
    @Optional
    val types = objectFactory.listProperty<String>()

    @Input
    @Optional
    val sinceVersion = objectFactory.property<String>()

    @Input
    @Optional
    val untilVersion = objectFactory.property<String>()

    @Input
    @Optional
    val sinceBuild = objectFactory.property<String>()

    @Input
    @Optional
    val untilBuild = objectFactory.property<String>()

    @Input
    @Optional
    val releaseChannels = objectFactory.setProperty<Channel>()

    private val context = logCategory()

    @TaskAction
    fun list() {
        val releases = XmlExtractor<ProductsReleases>(context).let {
            updatePaths.get().mapNotNull(it::fetch)
        }
        val androidStudioReleases = XmlExtractor<AndroidStudioReleases>(context).let {
            androidStudioUpdatePath.get().let(it::fetch)
        } ?: AndroidStudioReleases()

        val since = (sinceVersion.orNull ?: sinceBuild.get()).run(Version::parse)
        val until = (untilVersion.orNull ?: untilBuild.orNull.takeUnless { it.isNullOrBlank() || sinceVersion.isPresent })?.run {
            replace("*", "9999").run(Version::parse)
        }
        val types = types.get()
        val channels = releaseChannels.get()

        fun testVersion(version: Version?, build: Version?): Boolean {
            val a = when (since.major) {
                in 100..999 -> build
                else -> version
            }
            val b = when (until?.major) {
                in 100..999 -> build
                else -> version
            }

            return a != null && b != null && a >= since && (until == null || b <= until)
        }

        val result = releases.map(ProductsReleases::products).flatten().asSequence()
            .flatMap { product -> product.codes.map { it to product }.asSequence() }
            .filter { (type) -> types.contains(type) }
            .flatMap { (type, product) -> product.channels.map { type to it }.asSequence() }
            .filter { (_, channel) -> channels.contains(Channel.valueOf(channel.status.toUpperCase())) }
            .flatMap { (type, channel) ->
                channel.builds.map {
                    type to (it.version.run(Version::parse) to it.number.run(Version::parse))
                }.asSequence()
            }
            .filter { (_, version) -> testVersion(version.first, version.second) }
            .groupBy { (type, version) -> "$type-${version.first.major}.${version.first.minor}" }
            .mapNotNull {
                it.value.maxByOrNull { (_, version) ->
                    version.first.patch
                }?.let { (type, version) -> "$type-${version.first.asRelease()}" }
            }
            .distinct()
            .toList()

        val androidStudioResult = when (types.contains("AI")) {
            true -> androidStudioReleases.items
                .asSequence()
                .filter { item ->
                    val version = item.platformVersion?.let(Version::parse)
                    val build = item.platformBuild?.let(Version::parse)
                    testVersion(version, build)
                }
                .filter { channels.contains(Channel.valueOf(it.channel.toUpperCase())) }
                .groupBy { it.version.split('.').dropLast(1).joinToString(".") }
                .mapNotNull { entry ->
                    entry.value.maxByOrNull {
                        it.version.split('.').last().toInt()
                    }
                }
                .map { "AI-${it.version}" }
                .toList()

            false -> emptyList()
        }

        outputFile.get().asFile.outputStream().use { os ->
            (result + androidStudioResult).joinToString("\n").apply {
                TeeOutputStream(System.out, os).write(toByteArray())
            }
        }
    }

    private fun Version.asRelease() = "$major.$minor" + (".$patch".takeIf { patch > 0 }.orEmpty())

    enum class Channel {
        EAP, MILESTONE, BETA, RELEASE, CANARY, PATCH, RC,
    }
}
