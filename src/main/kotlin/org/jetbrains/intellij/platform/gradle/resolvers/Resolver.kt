// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.utils.Logger

/**
 * Interface for resolving an instance of [T] object.
 */
abstract class Resolver<T> {

    abstract val subject: String

    open val subjectInput: Any?
        get() = null

    private val log: Logger
        get() = Logger(javaClass)

    /**
     * Resolves the [T] object.
     *
     * @return The resolved object.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    abstract fun resolve(): T

    /**
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    protected fun Sequence<Pair<String, () -> T?>>.resolve() = this
        .also { log.debug("Resolving '$subject'.") }
        .firstNotNullOfOrNull { (label, block) ->
            block()?.also { log.debug("'$label' resolved as: $it") }
        }
        .run { requireNotNull(this) { "Cannot resolve '$subject'" + subjectInput?.let { " with: '$it'" }.orEmpty() } }
}
