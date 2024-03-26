// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.resources.ResourceHandler
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import java.nio.file.Path
import kotlin.io.path.absolute

inline fun <T> T?.throwIfNull(block: () -> Exception) = this ?: throw block()

internal val FileSystemLocation.asPath
    get() = asFile.toPath().absolute()

internal val <T : FileSystemLocation> Provider<T>.asPath
    get() = get().asFile.toPath().absolute()

internal fun ConfigurationContainer.create(name: String, description: String, configuration: Configuration.() -> Unit = {}) =
    maybeCreate(name).apply {
        isVisible = false
        isCanBeConsumed = false
        isCanBeResolved = true

        this.description = description
        configuration()
    }

internal val Configuration.asLenient
    get() = incoming.artifactView { lenient(true) }.files

internal val ALL_TASKS
    get() = Constants.Tasks::class.java.declaredFields
        .filter { it.name != "INSTANCE" }
        .map { it.get(null).toString() }
        .minus("INSTANCE")

fun <T> Property<T>.isSpecified() = isPresent && when (val value = orNull) {
    null -> false
    is String -> value.isNotEmpty()
    is RegularFile -> value.asFile.exists()
    else -> true
}

/**
 * Retrieves the [Path] of the IntelliJ Platform with [Configurations.INTELLIJ_PLATFORM] configuration.
 *
 * @receiver The [Configuration] to retrieve the product information from.
 * @return The [Path] of the IntelliJ Platform
 */
fun FileCollection.platformPath() = runCatching {
    val entries = toList()

    when (entries.size) {
        0 -> throw IllegalArgumentException("No IntelliJ Platform dependency found.")
        1 -> entries.single().toPath()
        else -> throw IllegalArgumentException("More than one IntelliJ Platform dependency found.")
    }
}.onFailure {
    throw GradleException(
        """
        ${it.message}
        Please ensure there is a single IntelliJ Platform dependency defined in your project and that the necessary repositories, where it can be located, are added.
        See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
    """.trimIndent()
    )
}.getOrThrow()

// TODO: migrate to `project.resources.binary` whenever it's available. Ref: https://github.com/gradle/gradle/issues/25237
internal fun ResourceHandler.resolve(url: String) = text
    .fromUri(url)
    .runCatching { asFile("UTF-8") }
    .onFailure { Logger(javaClass).error("Cannot resolve product releases", it) }
    .getOrNull()
