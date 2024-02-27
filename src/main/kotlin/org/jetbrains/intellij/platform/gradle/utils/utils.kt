// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.file.RegularFile
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.jetbrains.intellij.platform.gradle.Constants
import kotlin.io.path.absolute

fun <T> T?.or(other: T): T = this ?: other

inline fun <T> T?.or(block: () -> T): T = this ?: block()

inline fun <T> T?.ifNull(block: () -> Unit): T? = this ?: block().let { null }

inline fun <T> T?.throwIfNull(block: () -> Exception) = this ?: throw block()

internal val FileSystemLocation.asPath
    get() = asFile.toPath().absolute()

internal val <T : FileSystemLocation> Provider<T>.asFile
    get() = get().asFile

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
