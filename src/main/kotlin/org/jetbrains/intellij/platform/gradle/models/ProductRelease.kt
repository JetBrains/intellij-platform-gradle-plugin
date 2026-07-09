// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import org.gradle.api.GradleException
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.Version

data class ProductRelease(
    val name: String,
    val channel: Channel,
    val type: IntelliJPlatformType,
    val version: Version,
    val build: Version,
    val platformVersion: Version = version,
    val platformBuild: Version = build,
    val downloads: List<Download> = emptyList(),
//    val id: String,
) {
    internal val notationVersion
        get() = when {
            channel == Channel.RELEASE -> version
            type == IntelliJPlatformType.AndroidStudio -> version
            else -> build
        }

    val notation
        get() = "$type-$notationVersion"

    internal fun matchesVersion(version: Version) = version == when {
        version.isBuildNumber() -> build
        else -> notationVersion
    }

    enum class Channel {
        EAP, MILESTONE, BETA, RELEASE, CANARY, PATCH, RC, PREVIEW;
    }

    data class Download(
        val kind: String?,
        val link: String,
        val checksum: String? = null,
        val checksumLink: String? = null,
    ) {
        internal data class Artifact(
            val downloadLinkVersion: String,
            val classifier: String?,
            val extension: String,
        )
    }
}

internal fun ProductRelease.resolveDownload(): ProductRelease.Download? {
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

    if (type == IntelliJPlatformType.AndroidStudio) {
        val arch = when {
            os == "mac" && architecture == "aarch64" -> "_arm"
            else -> ""
        }

        val candidates = downloads
            .filter { it.link.substringAfterLast('/').contains(os) }
            .filterNot { it.link.endsWith(".deb") || it.link.endsWith(".exe") } // Extracting of .deb and .exe archives is not supported.
            .toList()

        return candidates.firstOrNull { it.link.substringAfterLast('/').contains("$os$arch.") } ?: candidates.firstOrNull()
    } else {
        val arch = when {
            os == "windows" -> "Zip"
            os == "mac" && architecture == "aarch64" -> "M1"
            os == "linux" && architecture == "aarch64" -> "ARM64"
            else -> ""
        }

        return downloads.find { it.kind == "$os$arch" }
    }
}

internal fun ProductRelease.resolveDownloadArtifact(): ProductRelease.Download.Artifact? {
    val link = resolveDownload()?.link ?: return null
    val path = link.substringBefore('?').substringBefore('#')
    val segments = path.split('/').filter { it.isNotEmpty() }
    val fileName = segments.last()

    val extension = Constants.Configurations.Attributes.ArtifactType.Archives
        .map { it.toString() }
        .sortedByDescending { it.length }
        .first { fileName.endsWith(".$it") }

    val baseName = fileName.removeSuffix(".$extension")
    val revisionFromParent = segments.dropLast(1).lastOrNull { it.firstOrNull()?.isDigit() == true }

    val isAndroidStudio = "/android/studio/" in path

    val (name, rest) = when {
        isAndroidStudio -> {
            val name = "android-studio"
            val prefix = "$name-"
            require(baseName.startsWith(prefix)) { "Cannot resolve artifact from '$link'" }

            name to baseName.removePrefix(prefix)
        }
        else -> {
            val separator = baseName.indices.firstOrNull {
                baseName[it] == '-' && baseName.getOrNull(it + 1)?.isDigit() == true
            }
            require(separator != null && separator > 0) { "Cannot resolve artifact from '$link'" }

            baseName.substring(0, separator) to baseName.substring(separator + 1)
        }
    }

    if (isAndroidStudio && revisionFromParent != null) {
        val classifier = rest
            .removePrefix("$revisionFromParent-")
            .removePrefix("$revisionFromParent.")
            .takeIf { it != rest || rest != revisionFromParent }

        return ProductRelease.Download.Artifact(revisionFromParent, classifier, extension)
    }

    val classifierSeparator = when (name) {
        "JetBrainsClient" -> rest.indices.firstOrNull {
            (rest[it] == '-' || rest[it] == '.') && rest.getOrNull(it + 1)?.isLetter() == true
        } ?: -1
        else -> maxOf(rest.lastIndexOf('-'), rest.lastIndexOf('.')).takeIf {
            it >= 0 && rest.getOrNull(it + 1)?.isLetter() == true
        } ?: -1
    }

    return when {
        classifierSeparator < 0 -> ProductRelease.Download.Artifact(rest, null, extension)
        else -> ProductRelease.Download.Artifact(
            downloadLinkVersion = rest.substring(0, classifierSeparator),
            classifier = rest.substring(classifierSeparator + 1),
            extension = extension,
        )
    }
}
