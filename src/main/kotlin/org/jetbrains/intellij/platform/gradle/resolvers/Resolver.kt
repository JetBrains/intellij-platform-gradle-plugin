// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers

import org.gradle.api.GradleException

/**
 * Interface for resolving an instance of [T] object.
 */
interface Resolver<T> {

    /**
     * Resolves the [T] object.
     *
     * @return the resolved object.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    fun resolve(): T
}
