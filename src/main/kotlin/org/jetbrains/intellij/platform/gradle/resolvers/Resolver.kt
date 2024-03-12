// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.ifNull
import org.jetbrains.intellij.platform.gradle.utils.throwIfNull

/**
 * Interface for resolving an instance of [T] object.
 */
interface Resolver<T> {

    val subject: String

    val log: Logger
        get() = Logger(javaClass)

    /**
     * Resolves the [T] object.
     *
     * @return the resolved object.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    fun resolve(): T

    fun Sequence<Pair<String, () -> T?>>.resolve() = this
        .also { log.debug("Resolving '$subject'.") }
        .mapNotNull { (label, block) ->
            block()
                .ifNull { log.debug("Could not resolve '$label'") }
                ?.also { log.debug("'$label' resolved as: $it") }
        }
        .firstOrNull()
        ?.also { log.debug("Resolved '$subject': $it") }
        .throwIfNull { GradleException("Cannot resolve '$subject'") }
}
