// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.resolvers.Resolver
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.ifNull
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries

/**
 * Interface for resolving a [Path] to executables or other files of any kind.
 *
 * @param subject The name of the resource
 */
abstract class PathResolver(
    protected val subject: String,
) : Resolver<Path> {

    private val log = Logger(javaClass)

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
    override fun resolve() = predictions
        .also { log.debug("Resolving '$subject'.") }
        .mapNotNull { (label, block) ->
            block()
                .ifNull { log.debug("Could not resolve '$label'") }
                ?.also { log.debug("'$label' resolved as: $it") }
        }
        .firstOrNull()
        ?.also { log.debug("Resolved '$subject': $it") }
        .throwIfNull { GradleException("Cannot resolve '$subject'") }

    /**
     * This method of checking if the file exists is required to don't break the Gradle configuration cache and lazy resolving of some values.
     * Calling `Path.exists()` method simply fails.
     */
    protected fun Path.takeIfExists() = takeIf {
        runCatching { parent.listDirectoryEntries().contains(this) }.getOrDefault(false)
    }
}
