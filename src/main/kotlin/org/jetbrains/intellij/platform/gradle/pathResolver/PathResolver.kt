// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.pathResolver

import org.gradle.api.GradleException
import java.nio.file.Path

/**
 * Interface for resolving a [Path] to the executables or other files of any kind.
 */
interface PathResolver {

    /**
     * Resolves the path.
     *
     * @return the resolved path, or null if resolution failed.
     * @throws GradleException
     */
    @Throws(GradleException::class)
    fun resolve(): Path
}
