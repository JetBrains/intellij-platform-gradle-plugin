// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.models.AndroidStudioReleases
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource.Parameters
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath

/**
 * Fetches the Android Studio releases list from [Locations.PRODUCTS_RELEASES_ANDROID_STUDIO] and finds the release matching the [Parameters.androidStudioVersion].
 * From the resolved [AndroidStudioReleases.Item], filters out the [AndroidStudioReleases.Item.Download.link] matching current OS and architecture.
 */
abstract class AndroidStudioDownloadLinkValueSource : ValueSource<String, Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * A file containing the XML with all available Android Studio releases.
         *
         * @see Locations.PRODUCTS_RELEASES_ANDROID_STUDIO
         */
        val androidStudio: RegularFileProperty

        /**
         * The requested Android Studio IDE version.
         */
        val androidStudioVersion: Property<String>
    }

    private val log = Logger(javaClass)

    override fun obtain() = runCatching {
        val androidStudioReleases = parameters.androidStudio.orNull?.asPath
            ?.also { log.info("Reading Android Studio releases from: $it") }
            ?.let { decode<AndroidStudioReleases>(it) }
        requireNotNull(androidStudioReleases) { "Failed to decode Android Studio releases from: ${parameters.androidStudio.orNull}" }

        val os = with(OperatingSystem.current()) {
            when {
                isMacOsX -> "mac"
                isLinux -> "linux"
                isWindows -> "windows"
                else -> throw GradleException("Failed to obtain platform OS for: $this")
            }
        }

        val version = parameters.androidStudioVersion.orNull
        val item = androidStudioReleases.items.find { it.version == version }
        requireNotNull(item) { "Failed to find Android Studio release for version: $version" }

        item.downloads
            .asSequence()
            .map { it.link }
            .filter { link -> link.substringAfterLast('/').contains(os) }
            .filterNot { link -> link.endsWith(".deb") || link.endsWith(".exe") } // Extracting of .deb and .exe archives is not supported.
            .sortedWith(compareByDescending { link ->
                val arch = when {
                    os == "mac" && System.getProperty("os.arch") == "aarch64" -> "_arm"
                    else -> ""
                }
                link.substringAfterLast('/').contains("$os$arch.")
            })
            .firstOrNull()
            ?: throw GradleException("Failed to obtain download link for version: $version")
    }.onFailure {
        log.error("${javaClass.canonicalName} execution failed.", it)
    }.getOrNull()
}
