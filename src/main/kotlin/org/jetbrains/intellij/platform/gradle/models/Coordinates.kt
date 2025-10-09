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
 * This collection includes modules from both:
 * - `org.jetbrains.kotlinx` group (standard coroutines)
 * - `com.intellij.platform` group (repacked coroutines in IntelliJ Platform)
 */
val coroutines = setOf(
    // KotlinX Coroutines
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-core"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-debug"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-guava"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-slf4j"),
    Coordinates("org.jetbrains.kotlinx", "kotlinx-coroutines-test"),

    // KotlinX Coroutines repacked in the IntelliJ Platform
    Coordinates("com.intellij.platform", "kotlinx-coroutines-core-jvm"),
    Coordinates("com.intellij.platform", "kotlinx-coroutines-jdk8"),
    Coordinates("com.intellij.platform", "kotlinx-coroutines-core"),
    Coordinates("com.intellij.platform", "kotlinx-coroutines-debug"),
    Coordinates("com.intellij.platform", "kotlinx-coroutines-guava"),
    Coordinates("com.intellij.platform", "kotlinx-coroutines-slf4j"),
    Coordinates("com.intellij.platform", "kotlinx-coroutines-test"),
)
