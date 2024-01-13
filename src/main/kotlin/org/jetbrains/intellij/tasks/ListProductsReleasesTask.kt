// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.outputStream
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.*
import org.jetbrains.intellij.*
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_ANDROID_STUDIO
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.model.AndroidStudioReleases
import org.jetbrains.intellij.model.JetBrainsIdesReleases
import org.jetbrains.intellij.model.ProductRelease
import org.jetbrains.intellij.model.XmlExtractor

/**
 * List all available IntelliJ-based IDE releases with their updates.
 * The result list is used for testing the plugin with Plugin Verifier using the [RunPluginVerifierTask] task.
 *
 * Plugin Verifier requires a list of the IDEs that will be used for verifying your plugin build against.
 * The availability of the releases may change in time, i.e., due to security issues in one version – which will be later removed and replaced with an updated IDE release.
 *
 * With the [ListProductsReleasesTask] task, it is possible to list the currently available IDEs matching given conditions, like platform types, since/until release versions.
 * Such a list is fetched from the remote updates file: `https://www.jetbrains.com/updates/updates.xml`, parsed and filtered considering the specified [ListProductsReleasesTask.types], [ListProductsReleasesTask.sinceVersion], [ListProductsReleasesTask.untilVersion] (or [ListProductsReleasesTask.sinceBuild], [ListProductsReleasesTask.untilBuild]) properties.
 *
 * The result list is stored within the [outputFile], which is used as a source for the Plugin Verifier if the [RunPluginVerifierTask] task has no [RunPluginVerifierTask.ideVersions] property specified, the output of the [ListProductsReleasesTask] task is used.
 *
 * @see [PrintProductsReleasesTask]
 */
@CacheableTask
abstract class ListProductsReleasesTask : DefaultTask() {

    /**
     * Path to the products releases update files. By default, one is downloaded from [IntelliJPluginConstants.IDEA_PRODUCTS_RELEASES_URL].
     */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val ideaProductReleasesUpdateFiles: ConfigurableFileCollection

    /**
     * Path to the products releases update files. By default, one is downloaded from [IntelliJPluginConstants.ANDROID_STUDIO_PRODUCTS_RELEASES_URL].
     */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val androidStudioProductReleasesUpdateFiles: ConfigurableFileCollection

    /**
     * Path to the file, where the output list will be stored.
     *
     * Default value: `File("${project.buildDir}/listProductsReleases.txt")`
     */
    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    /**
     * List the types of IDEs that will be listed in results.
     */
    @get:Input
    @get:Optional
    abstract val types: ListProperty<String>

    /**
     * Lower boundary of the listed results in marketing product version format, like `2020.2.1`.
     * Takes the precedence over [sinceBuild] property.
     *
     * Default value: [IntelliJPluginExtension.version]
     */
    @get:Input
    @get:Optional
    abstract val sinceVersion: Property<String>

    /**
     * Upper boundary of the listed results in product marketing version format, like `2020.2.1`.
     * Takes the precedence over [untilBuild] property.
     *
     * Default value: `null`
     */
    @get:Input
    @get:Optional
    abstract val untilVersion: Property<String>

    /**
     * Lower boundary of the listed results in build number format, like `192`.
     *
     * Default value: [IntelliJPluginExtension.version]
     */
    @get:Input
    @get:Optional
    abstract val sinceBuild: Property<String>

    /**
     * Upper boundary of the listed results in build number format, like `192`.
     *
     * Default value: `null`
     */
    @get:Input
    @get:Optional
    abstract val untilBuild: Property<String>

    /**
     * Channels that product updates will be filtered with.
     *
     * Default value: `EnumSet.allOf(ListProductsReleasesTask.Channel)`
     */
    @get:Input
    @get:Optional
    abstract val releaseChannels: SetProperty<Channel>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "List all available IntelliJ-based IDE releases with their updates."
    }

    @TaskAction
    fun listProductsReleases() {
        val jetbrainsIdesReleases = XmlExtractor<JetBrainsIdesReleases>(context).let { extractor ->
            ideaProductReleasesUpdateFiles
                .singleFile
                .toPath()
                .let { extractor.fetch(it) }
                .or { JetBrainsIdesReleases() }
        }.let {
            sequence {
                it.products.forEach { product ->
                    product.channels.forEach channel@{ channelEntry ->
                        channelEntry.builds.forEach { build ->
                            product.codes.forEach codes@{ code ->
                                val channel = runCatching {
                                    Channel.valueOf(channelEntry.status.uppercase())
                                }.getOrElse { return@channel }

                                yield(
                                    ProductRelease(
                                        name = product.name,
                                        type = code,
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
        }.toList()

        val androidStudioReleases = XmlExtractor<AndroidStudioReleases>(context).let { extractor ->
            androidStudioProductReleasesUpdateFiles
                .singleFile
                .toPath()
                .let { extractor.fetch(it) }
                .or { AndroidStudioReleases() }
        }
            .items.mapNotNull { item ->
                val channel = runCatching { Channel.valueOf(item.channel.uppercase()) }.getOrNull() ?: return@mapNotNull null

                ProductRelease(
                    name = item.name,
                    type = PLATFORM_TYPE_ANDROID_STUDIO,
                    channel = channel,
                    build = item.platformBuild.toVersion(),
                    version = item.platformVersion.toVersion(),
                    id = item.version,
                )
            }

        val since = sinceVersion.orNull
            .or { sinceBuild.get() }
            .run(Version::parse)
        val until = untilVersion.orNull
            .or {
                untilBuild.orNull
                    .takeUnless { it.isNullOrBlank() || sinceVersion.isPresent }
            }
            ?.replace("*", "99999")
            ?.run(Version::parse)

        fun ProductRelease.testVersion(): Boolean {
            fun getComparativeVersion(version: Version) = when (version.major) {
                in 100..999 -> build
                else -> this.version
            }
            return getComparativeVersion(since) >= since && (until?.let { getComparativeVersion(it) <= it } ?: true)
        }

        val types = types.get()
        val channels = releaseChannels.get()
        val result = (jetbrainsIdesReleases + androidStudioReleases)
            .filter { it.type in types }
            .filter { it.channel in channels }
            .filter { it.testVersion() }
            .groupBy { "${it.type}-${it.version.major}.${it.version.minor}" }
            .values
            .map { it.maxBy { release -> release.version } }
            .map { "${it.type}-${it.id}" }


        outputFile.get().asPath.outputStream().use { os ->
            result.joinToString("\n").apply {
                os.write(toByteArray())
            }
        }
    }

    enum class Channel {
        EAP, MILESTONE, BETA, RELEASE, CANARY, PATCH, RC,
    }
}
