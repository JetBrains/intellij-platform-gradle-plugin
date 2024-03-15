// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.resolvers.Resolver
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

/**
 * Interface for resolving a [Path] to executables or other files of any kind.
 */
abstract class PathResolver : Resolver<Path> {

    /**
     * A sequence of possible locations of the [Path] we're looking for.
     */
    abstract val predictions: Sequence<Pair<String, () -> Path?>>

    /**
     * Resolves the [Path] of our [subject] going through all [predictions] sequence.
     *
     * @throws GradleException
     */
    @Throws(GradleException::class)
    override fun resolve() = predictions.resolve()
}

/**
 * This method of checking if the file exists is required to don't break the Gradle configuration cache and lazy resolving of some values.
 * Calling `Path.exists()` method simply fails.
 */
internal fun Path.takeIfExists() = takeIf {
    runCatching { parent.listDirectoryEntries().contains(this) }.getOrDefault(false)
}

/**
 * Resolves the fist matching entry using provided [glob].
 */
internal fun Path?.resolveEntry(glob: String = "*") = this?.listDirectoryEntries(glob)?.firstOrNull()
