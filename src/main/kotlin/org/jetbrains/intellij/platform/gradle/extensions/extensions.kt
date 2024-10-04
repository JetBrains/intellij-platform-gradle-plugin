// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:Suppress("MayBeConstant", "ObjectPropertyName")

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.IVY_FILES_DIRECTORY
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.createDirectories

/**
 * Returns the IntelliJ Platform Gradle Plugin cache directory for the current project.
 */
internal fun ProviderFactory.intellijPlatformCachePath(path: Path) =
    get(GradleProperties.IntellijPlatformCache).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: path.resolve(CACHE_DIRECTORY) }
        .createDirectories()
        .absolute()

/**
 * Represents the local platform artifacts directory path which contains Ivy XML files.
 *
 * @see [GradleProperties.LocalPlatformArtifacts]
 */
internal fun ProviderFactory.localPlatformArtifactsPath(path: Path) =
    get(GradleProperties.LocalPlatformArtifacts).orNull
        .takeUnless { it.isNullOrBlank() }
        ?.let { Path(it) }
        .run { this ?: intellijPlatformCachePath(path).resolve(IVY_FILES_DIRECTORY) }
        .createDirectories()
        .absolute()

/**
 * Parses a string representation of an IntelliJ Platform type and version, such as `IU-2024.2`.
 * If the type is missing, returns the default [IntelliJPlatformType.IntellijIdeaCommunity].
 *
 * @receiver The string representing the IntelliJ Platform notation.
 * @return A pair consisting of the corresponding [IntelliJPlatformType] and version string.
 */
internal fun String.parseIdeNotation() = trim().split('-').let {
    when {
        it.size == 2 -> it.first().toIntelliJPlatformType() to it.last()
        else -> IntelliJPlatformType.IntellijIdeaCommunity to it.first()
    }
}

/**
 * Parses the plugin notation into the `<id, version, channel>` triple.
 *
 * Possible notations are `id:version` or `id:version@channel`.
 *
 * @receiver The string representing the plugin version notation.
 * @return A triple consisting of the id, version, and channel.
 */
internal fun String.parsePluginNotation() = trim()
    .takeIf { it.isNotEmpty() }
    ?.split(":", "@")
    ?.run {
        val pluginId = getOrNull(0).orEmpty()
        val version = getOrNull(1).orEmpty()
        val group = getOrNull(2)?.let { "@$it" }.orEmpty()

        Triple(pluginId, version, group)
    }

/**
 * An interface to unify how IntelliJ Platform Gradle Plugin extensions are registered.
 * Because [ExtensionContainer.create] accepts extension arguments provided with no strong typing,
 */
internal interface Registrable<T> {
    fun register(project: Project, target: Any): T
}
