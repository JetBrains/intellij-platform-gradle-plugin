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

open class ListProductsReleasesTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    /**
     * Path to the products releases update file. By default, falls back to the Maven cache.
     *
     * Default value: _Maven cache_
     */
    @get:Input
    @get:Optional
    val updatePaths = objectFactory.listProperty<String>()

    /**
     * For Android Studio releases, a separated storage for the updates is used.
     *
     * Default value: `https://raw.githubusercontent.com/JetBrains/intellij-sdk-docs/main/topics/_generated/android_studio_releases.xml`
     */
    @get:Input
    @get:Optional
    val androidStudioUpdatePath = objectFactory.property<String>()

    /**
     * Path to the file, where the output list will be stored.
     *
     * Default value: `File("${project.buildDir}/listProductsReleases.txt")`
     */
    @get:OutputFile
    val outputFile: RegularFileProperty = objectFactory.fileProperty()

    @get:Input
    @get:Optional
    val types = objectFactory.listProperty<String>()

    /**
     * Lower boundary of the listed results in marketing product version format, like `2020.2.1`.
     * Takes the precedence over [sinceBuild] property.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.version]
     */
    @get:Input
    @get:Optional
    val sinceVersion = objectFactory.property<String>()

    /**
     * Upper boundary of the listed results in product marketing version format, like `2020.2.1`.
     * Takes the precedence over [untilBuild] property.
     *
     * Default value: `null`
     */
    @get:Input
    @get:Optional
    val untilVersion = objectFactory.property<String>()

    /**
     * Lower boundary of the listed results in build number format, like `192`.
     *
     * Default value: [org.jetbrains.intellij.IntelliJPluginExtension.version]
     */
    @get:Input
    @get:Optional
    val sinceBuild = objectFactory.property<String>()

    /**
     * Upper boundary of the listed results in build number format, like `192`.
     *
     * Default value: `null`
     */
    @get:Input
    @get:Optional
    val untilBuild = objectFactory.property<String>()

    /**
     * Channels that product updates will be filtered with.
     *
     * Default value: `EnumSet.allOf(ListProductsReleasesTask.Channel)`
     */
    @get:Input
    @get:Optional
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
