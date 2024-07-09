// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.utils.Logger

/**
 * Interface for resolving an instance of [T] object.
 */
interface Resolver<T> {

    val subject: String

    val subjectInput: Any?
        get() = null

    private val log: Logger
        get() = Logger(javaClass)

    /**
     * Resolves the [T] object.
     *
     * @return the resolved object.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    fun resolve(): T

    /**
     * @throws IllegalArgumentException
     */
    @Throws(IllegalArgumentException::class)
    fun Sequence<Pair<String, () -> T?>>.resolve() = this
        .also { log.debug("Resolving '$subject'.") }
        .firstNotNullOfOrNull { (label, block) ->
            block()?.also { log.debug("'$label' resolved as: $it") }
        }
        .let { requireNotNull(it) { "Cannot resolve '$subject'" + subjectInput?.let { " with: '$it'" }.orEmpty() } }
}
