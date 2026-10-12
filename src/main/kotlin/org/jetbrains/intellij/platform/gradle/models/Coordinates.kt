// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.utils.CachedHttpResource
import org.jetbrains.intellij.platform.gradle.utils.Http
import java.io.IOException
import java.nio.file.Path

data class Coordinates(val groupId: String, val artifactId: String) {

    override fun toString() = "$groupId:$artifactId"
}

/**
 * Resolves the latest version of the given [Coordinates] from the `maven-metadata.xml` of [repositoryUrl].
 *
 * When [cacheDirectory] is provided, the metadata payload is cached and conditionally revalidated with ETag
 * and Last-Modified headers. When [offline] is `true`, no network request is performed (as required by Gradle's
 * `--offline` mode) and any previously cached version is returned (or `null` if none exists). The [offline]
 * flag must be captured at configuration time from [org.gradle.StartParameter.isOffline] and passed in explicitly;
 * this function never reads `gradle.startParameter` on its own.
 */
fun Coordinates.resolveLatestVersion(
    repositoryUrl: String = Locations.MAVEN_REPOSITORY,
    cacheDirectory: Path? = null,
    offline: Boolean = false,
): String? {
    val host = repositoryUrl.trimEnd('/')
    val path = toString().replace(':', '/').replace('.', '/')
    val url = "$host/$path/maven-metadata.xml"

    val content = when {
        cacheDirectory != null -> {
            CachedHttpResource.read(
                url = url,
                cacheDirectory = cacheDirectory,
                extension = "xml",
                offline = offline,
            )
        }
        else -> {
            if (offline) {
                return null
            }
            Http.openConnection(url) { connection ->
                if (connection.responseCode !in 200..299) {
                    throw IOException(
                        "Failed to fetch Maven metadata from URL: $url with response code: ${connection.responseCode}"
                    )
                }
                connection.inputStream.bufferedReader().use { it.readText() }
            }
        }
    }

    return content?.let { decode<MavenMetadata>(it).versioning?.latest }
}

/**
 * Resolves the latest version of the given [Coordinates] from the `maven-metadata.xml` of [repositoryUrl].
 */
fun Coordinates.resolveLatestVersion(
    repositoryUrl: String,
    offline: Boolean,
) = resolveLatestVersion(repositoryUrl, cacheDirectory = null, offline = offline)

/**
 * Coordinates of all Kotlin stdlib modules that should be excluded from dependencies.
 */
val kotlinStdlib = setOf(
    Coordinates("org.jetbrains.kotlin", "kotlin-stdlib"),
    Coordinates("org.jetbrains.kotlin", "kotlin-stdlib-jdk8"),
)

/**
 * Coordinates of all Kotlin Coroutines modules that should be excluded from dependencies.
 *
 * This collection includes modules from:
 * - `org.jetbrains.kotlinx` group (standard coroutines)
 * - `com.intellij.platform` group (repacked coroutines in IntelliJ Platform)
 * - `org.jetbrains.intellij.deps.kotlinx` group (repacked coroutines in IntelliJ Platform since October 23rd, 2025)
 */
val coroutines = listOf(
    "org.jetbrains.kotlinx",
    "com.intellij.platform",
    "org.jetbrains.intellij.deps.kotlinx",
).flatMapTo(mutableSetOf()) { groupId ->
    listOf(
        "kotlinx-coroutines-core-jvm",
        "kotlinx-coroutines-jdk8",
        "kotlinx-coroutines-core",
        "kotlinx-coroutines-debug",
        "kotlinx-coroutines-guava",
        "kotlinx-coroutines-slf4j",
        "kotlinx-coroutines-test",
    ).map { artifactId -> Coordinates(groupId, artifactId) }
}
