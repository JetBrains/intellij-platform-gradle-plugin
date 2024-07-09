// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("MayBeConstant", "ObjectPropertyName")

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.Directory
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.ProviderFactory
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.File
import java.nio.file.Path
import kotlin.io.path.*

@Throws(IllegalArgumentException::class)
internal fun resolvePath(localPath: Any) = when (localPath) {
    is String -> localPath
    is File -> localPath.absolutePath
    is Directory -> localPath.asPath.pathString
    else -> throw IllegalArgumentException("Invalid argument type: '${localPath.javaClass}'. Supported types: String, File, or Directory.")
}.let { Path(it) }

/**
 * Resolves the artifact path for the given [localPath] as it may accept different data types.
 *
 * @param localPath The local path of the artifact. Accepts either [String], [File], or [Directory].
 * @return The resolved artifact path as a Path object.
 * @throws IllegalArgumentException if the [localPath] is not of supported types.
 */
@Throws(IllegalArgumentException::class)
internal fun resolveArtifactPath(localPath: Any) = resolvePath(localPath)
    .let { it.takeUnless { OperatingSystem.current().isMacOsX && it.extension == "app" } ?: it.resolve("Contents") }
    .takeIf { it.exists() && it.isDirectory() }
    .let { requireNotNull(it) { "Specified localPath '$localPath' doesn't exist or is not a directory." } }

/**
 * Returns the Gradle project cache directory.
 */
internal fun ProviderFactory.intellijPlatformCachePath(rootProjectDirectory: Path) =
    gradleProperty(GradleProperties.INTELLIJ_PLATFORM_CACHE).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: rootProjectDirectory.resolve(CACHE_DIRECTORY) }
        .createDirectories()
        .absolute()

/**
 * Represents the local platform artifacts directory path which contains Ivy XML files.
 *
 * @see [createIvyDependencyFile]
 * @see [IntelliJPlatformRepositoriesExtension.jetbrainsCdn]
 * @see [GradleProperties.LOCAL_PLATFORM_ARTIFACTS]
 */
internal fun ProviderFactory.localPlatformArtifactsPath(rootProjectDirectory: Path) =
    gradleProperty(GradleProperties.LOCAL_PLATFORM_ARTIFACTS).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: intellijPlatformCachePath(rootProjectDirectory).resolve("localPlatformArtifacts") }
        .createDirectories()
        .absolute()

/**
 * Retrieves URLs from registered repositories.
 */
internal fun String.parseIdeNotation() = trim().split('-').let {
    when {
        it.size == 2 -> it.first().toIntelliJPlatformType() to it.last()
        else -> IntelliJPlatformType.IntellijIdeaCommunity to it.first()
    }
}

/**
 * Parses the plugin notation into the `<id, version, channel` triple.
 *
 * Possible notations are `id:version` or `id:version@channel`.
 */
internal fun String.parsePluginNotation() = trim()
    .takeIf { it.isNotEmpty() }
    ?.split(":", "@")
    ?.run { Triple(getOrNull(0).orEmpty(), getOrNull(1).orEmpty(), getOrNull(2).orEmpty()) }

/**
 * An interface to unify how IntelliJ Platform Gradle Plugin extensions are registered.
 * Because [ExtensionContainer.create] accepts extension arguments provided with no strong typing,
 */
internal interface Registrable<T> {
    fun register(project: Project, target: Any): T
}

/**
 * Type alias for a lambda function that takes a [Dependency] and performs some actions on it.
 */
internal typealias DependencyAction = (Dependency.() -> Unit)
