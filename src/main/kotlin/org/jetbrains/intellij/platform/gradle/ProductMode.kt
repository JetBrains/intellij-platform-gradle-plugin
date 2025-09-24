// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

/**
 * Describes a mode in which a product may be started.
 */
enum class ProductMode {

    /**
     * Indicates that this process performs all necessary tasks to provide smart features itself. This is the default mode for all IDEs.
     */
    MONOLITH,
    /**
     * Indicates that this process doesn't perform heavy tasks like code analysis, and takes necessary information from another process.
     * Currently, this is used by JetBrains Client process connected to a remote development host or CodeWithMe session.
     */
    FRONTEND,
    /**
     * Indicates that this process doesn't perform heavy tasks like code analysis and takes necessary information from another process.
     * Currently, this is used by an IDE running as a remote development host.
     */
    BACKEND;
}
