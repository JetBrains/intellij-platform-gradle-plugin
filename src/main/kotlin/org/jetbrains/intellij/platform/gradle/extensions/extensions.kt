// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.Project
import org.gradle.api.plugins.ExtensionContainer
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType

/**
 * Parses a string representation of an IntelliJ Platform type and version, such as `IU-2024.2`.
 *
 * @receiver The string representing the IntelliJ Platform notation.
 * @return A pair consisting of the corresponding [IntelliJPlatformType] and version string.
 */
internal fun String.parseIdeNotation() = trim().split('-').let {
    when {
        it.size == 2 -> it
            .let { (type, version) -> type.toIntelliJPlatformType(version) to version }
            .let { (type, version) -> requireNotNull(type) {
                "Could not determine IntelliJ Platform type for '$type' and version '$version'."
            } to version }
        else -> throw IllegalArgumentException("Invalid IntelliJ Platform notation: '$this'. Expected format: '<type>-<version>', like 'IU-2025.3'.")
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
