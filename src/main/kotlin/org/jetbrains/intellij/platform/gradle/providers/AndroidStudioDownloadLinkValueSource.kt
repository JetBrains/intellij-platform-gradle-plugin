// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.GradleException
import org.gradle.api.provider.Property
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.AndroidStudioReleases
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource.Parameters
import org.jetbrains.intellij.platform.gradle.utils.Logger
import java.net.URL

/**
 * Fetches the Android Studio releases list from [GradleProperties.ProductsReleasesAndroidStudioUrl] and finds the release matching the [Parameters.androidStudioVersion].
 * From the resolved [AndroidStudioReleases.Item], filters out the [AndroidStudioReleases.Item.Download.link] matching current OS and architecture.
 */
abstract class AndroidStudioDownloadLinkValueSource : ValueSource<String, Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * A file containing the XML with all available Android Studio releases.
         *
         * @see GradleProperties.ProductsReleasesAndroidStudioUrl
         */
        val androidStudioUrl: Property<String>

        /**
         * The requested Android Studio IDE version.
         */
        val androidStudioVersion: Property<String>
    }

    private val log = Logger(javaClass)

    override fun obtain() = runCatching {
        val androidStudioReleases = loadAndroidStudioReleases(
            androidStudioUrl = parameters.androidStudioUrl.orNull,
            loader = { URL(it).readText() },
            log = log,
        )
        requireNotNull(androidStudioReleases) { "Failed to decode Android Studio releases from: ${parameters.androidStudioUrl.orNull}" }
        androidStudioReleases.resolveDownloadLink(parameters.androidStudioVersion.orNull)
    }.onFailure {
        log.error("${javaClass.canonicalName} execution failed.", it)
    }.getOrNull()
}

internal fun loadAndroidStudioReleases(androidStudioUrl: String?, loader: (String) -> String?, log: Logger) =
    androidStudioUrl
        ?.also { log.info("Reading Android Studio releases from: $it") }
        ?.let(loader)
        ?.let { decode<AndroidStudioReleases>(it) }

internal fun AndroidStudioReleases.resolveDownloadLink(version: String?): String {
    val operatingSystem = OperatingSystem.current()
    val architecture = System.getProperty("os.arch")

    val os = with(operatingSystem) {
        when {
            isMacOsX -> "mac"
            isLinux -> "linux"
            isWindows -> "windows"
            else -> throw GradleException("Failed to obtain platform OS for: $this")
        }
    }

    val item = items.find {
        it.version == version || it.build == version || it.build == "${IntelliJPlatformType.AndroidStudio.code}-$version"
    }
    requireNotNull(item) { "Failed to find Android Studio release for version: $version" }

    val arch = when {
        os == "mac" && architecture == "aarch64" -> "_arm"
        else -> ""
    }

    val candidates = item.downloads
        .asSequence()
        .map { it.link }
        .filter { link -> link.substringAfterLast('/').contains(os) }
        .filterNot { link -> link.endsWith(".deb") || link.endsWith(".exe") } // Extracting of .deb and .exe archives is not supported.
        .toList()

    return candidates.firstOrNull { link ->
        link.substringAfterLast('/').contains("$os$arch.")
    } ?: candidates.firstOrNull()
    ?: throw GradleException("Failed to obtain download link for version: $version")
}
