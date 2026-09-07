// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import org.jetbrains.intellij.platform.gradle.Constants.Locations
import java.net.URL

data class Coordinates(val groupId: String, val artifactId: String) {

    override fun toString() = "$groupId:$artifactId"
}

fun Coordinates.resolveLatestVersion(repositoryUrl: String = Locations.MAVEN_REPOSITORY): String? {
    val host = repositoryUrl.trimEnd('/')
    val path = toString().replace(':', '/').replace('.', '/')
    val url = URL("$host/$path/maven-metadata.xml")
    return decode<MavenMetadata>(url).versioning?.latest
}

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
