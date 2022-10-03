// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.newInstance
import org.jetbrains.intellij.*
import org.jetbrains.intellij.IntelliJPluginConstants.CACHE_REDIRECTOR
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_ANDROID_STUDIO
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_INTELLIJ_COMMUNITY
import org.jetbrains.intellij.utils.ArchiveUtils
import org.jetbrains.intellij.utils.DependenciesDownloader
import org.jetbrains.intellij.utils.ivyRepository
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

@CacheableTask
abstract class VerificationIdesDownloadTask @Inject constructor(
    objects: ObjectFactory,
) : DefaultTask() {

    /**
     * IDEs to check, in `intellij.version` format, i.e.: `["IC-2019.3.5", "PS-2019.3.2"]`.
     * Check the available build versions on [IntelliJ Platform Builds list](https://jb.gg/intellij-platform-builds-list).
     *
     * Default value: output of the [org.jetbrains.intellij.tasks.ListProductsReleasesTask] task
     */
    @get:Input
    @get:Optional
    abstract val ideVersions: ListProperty<String>

    /**
     * A fallback file with a list of the releases generated with [ListProductsReleasesTask].
     * Used if [ideVersions] is not provided.
     */
    @get:Input
    @get:Optional
    abstract val productsReleasesFile: Property<File>

    /**
     * A list of the paths to locally installed IDE distributions that should be used for verification
     * in addition to those specified in [ideVersions].
     */
    @get:InputFiles
    @get:Optional
    @get:PathSensitive(PathSensitivity.ABSOLUTE)
    abstract val localPaths: ListProperty<File>

    /**
     * The path to the directory where IDEs used for the verification will be downloaded.
     *
     * Default value: `System.getProperty("plugin.verifier.home.dir")/ides`, `System.getenv("XDG_CACHE_HOME")/pluginVerifier/ides`,
     * `System.getProperty("user.home")/.cache/pluginVerifier/ides` or system temporary directory.
     */
    @get:OutputDirectory
    @get:Optional
    abstract val downloadDir: DirectoryProperty

    private val dependenciesDownloader: DependenciesDownloader = objects.newInstance()
    private val archiveUtils: ArchiveUtils = objects.newInstance()

    private val logCategory = logCategory()

    init {
        group = IntelliJPluginConstants.PLUGIN_GROUP_NAME
    }

    @TaskAction
    fun downloadIdeFiles() {
        val downloadDir = downloadDir.asFile.get()

        val ideVersions = ideVersions.get().takeIf(List<String>::isNotEmpty) ?: run {
            when {
                localPaths.get().isEmpty() -> productsReleasesFile.get().takeIf(File::exists)?.readLines()
                else -> null
            }
        } ?: emptyList()

        info(logCategory, "ideVersions: $ideVersions")

        ideVersions.forEach { ideVersion ->

            resolveIdePath(ideVersion, downloadDir) { type, version, buildType ->
                val ideName = "$type-$version"
                val ideDir = downloadDir.resolve(ideName)
                info(logCategory, "Downloading IDE '$ideName' to: $ideDir")

                val url = resolveIdeUrl(type, version, buildType)
                val dependencyVersion =
                    listOf(type, version, buildType).filterNot(String::isNullOrEmpty).joinToString("-")
                val group = when (type) {
                    PLATFORM_TYPE_ANDROID_STUDIO -> "com.android"
                    else -> "com.jetbrains"
                }
                debug(logCategory, "Downloading IDE from $url")

                try {
                    val ideArchive = dependenciesDownloader.downloadFromRepository(logCategory, {
                        create(
                            group = group,
                            name = "ides",
                            version = dependencyVersion,
                            ext = "tar.gz",
                        )
                    }, {
                        ivyRepository(url)
                    }).first()

                    debug(logCategory, "IDE downloaded, extracting...")
                    archiveUtils.extract(ideArchive, ideDir, logCategory)
                    ideDir.listFiles()?.let { files ->
                        files.filter(File::isDirectory).forEach { container ->
                            container.listFiles()?.forEach { file ->
                                file.renameTo(ideDir.resolve(file.name))
                            }
                            container.deleteRecursively()
                        }
                    }
                } catch (e: Exception) {
                    warn(logCategory, "Cannot download '$ideName' from '$buildType' channel: $url", e)
                }

                debug(logCategory, "IDE extracted to: $ideDir")
                ideDir
            }
        }
    }

    /**
     * Resolves the IDE type and version. If only `version` is provided, `type` is set to "IC".
     *
     * @param ideVersion IDE version. Can be "2020.2", "IC-2020.2", "202.1234.56"
     * @return path to the resolved IDE
     */
    private fun resolveIdePath(
        ideVersion: String,
        downloadDir: File,
        block: (type: String, version: String, buildType: String) -> File,
    ): String {
        debug(logCategory, "Resolving IDE path for: $ideVersion")
        var (type, version) = ideVersion.trim().split('-', limit = 2) + null

        if (version == null) {
            debug(logCategory, "IDE type not specified, setting type to $PLATFORM_TYPE_INTELLIJ_COMMUNITY")
            version = type
            type = PLATFORM_TYPE_INTELLIJ_COMMUNITY
        }

        val ideName = "$type-$version"
        val ideDir = downloadDir.resolve(ideName)

        if (ideDir.exists()) {
            debug(logCategory, "IDE already available in: $ideDir")
            return ideDir.canonicalPath
        }

        val buildTypes = when (type) {
            PLATFORM_TYPE_ANDROID_STUDIO -> listOf("")
            else -> listOf("release", "rc", "eap", "beta")
        }

        buildTypes.forEach { buildType ->
            debug(logCategory, "Downloading IDE '$ideName' from '$buildType' channel to: $downloadDir")
            try {
                return block(type!!, version!!, buildType).canonicalPath.also {
                    debug(logCategory, "Resolved IDE '$ideName' path: $it")
                }
            } catch (e: IOException) {
                debug(
                    logCategory,
                    "Cannot download IDE '$ideName' from '$buildType' channel. Trying another channel...",
                    e
                )
            }
        }

        throw GradleException("IDE '$ideVersion' cannot be downloaded. Please verify the specified IDE version against the products available for testing: https://jb.gg/intellij-platform-builds-list")
    }

    /**
     * Resolves direct IDE download URL provided by the JetBrains Data Services.
     * The URL created with [IDEA_DOWNLOAD_URL] contains HTTP redirection, which is supposed to be resolved.
     * Direct download URL is prepended with [CACHE_REDIRECTOR] host for providing caching mechanism.
     *
     * @param type IDE type, i.e. IC, PS
     * @param version IDE version, i.e. 2020.2 or 203.1234.56
     * @param buildType release, rc, eap, beta
     * @return direct download URL prepended with [CACHE_REDIRECTOR] host
     */
    private fun resolveIdeUrl(type: String, version: String, buildType: String): String {
        val isAndroidStudio = type == PLATFORM_TYPE_ANDROID_STUDIO
        val url = when {
            isAndroidStudio -> "$ANDROID_STUDIO_DOWNLOAD_URL/$version/android-studio-$version-linux.tar.gz"
            else -> "$IDEA_DOWNLOAD_URL?code=$type&platform=linux&type=$buildType&${versionParameterName(version)}=$version"
        }

        debug(logCategory, "Resolving direct IDE download URL for: $url")

        var connection: HttpURLConnection? = null

        try {
            with(URL(url).openConnection() as HttpURLConnection) {
                connection = this
                instanceFollowRedirects = false
                inputStream.use {
                    if (
                        (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP)
                        && !isAndroidStudio
                    ) {
                        val redirectUrl = URL(getHeaderField("Location"))
                        disconnect()
                        debug(logCategory, "Resolved IDE download URL: $url")
                        return "$CACHE_REDIRECTOR/${redirectUrl.host}${redirectUrl.file}"
                    } else {
                        debug(logCategory, "IDE download URL has no redirection provided. Skipping")
                    }
                }
            }
        } catch (e: Exception) {
            info(logCategory, "Cannot resolve direct download URL for: $url")
            debug(logCategory, "Download exception stacktrace:", e)
            throw e
        } finally {
            connection?.disconnect()
        }

        return url
    }

    companion object {
        private const val IDEA_DOWNLOAD_URL = "https://data.services.jetbrains.com/products/download"
        private const val ANDROID_STUDIO_DOWNLOAD_URL = "https://redirector.gvt1.com/edgedl/android/studio/ide-zips"

        private val versionParameterRegex = Regex("\\d{3}(\\.\\d+)+")

        /**
         * Obtains the version parameter name used for downloading IDE artifact.
         *
         * Examples:
         * - 202.7660.26 -> build
         * - 2020.1, 2020.2.3 -> version
         *
         * @param version current version
         * @return version parameter name
         */
        private fun versionParameterName(version: String) = when {
            version.matches(versionParameterRegex) -> "build"
            else -> "version"
        }
    }
}
