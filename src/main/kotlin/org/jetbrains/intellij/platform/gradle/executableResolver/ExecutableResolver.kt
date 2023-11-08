// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.executableResolver

import java.nio.file.Path

interface ExecutableResolver {

    fun resolveExecutable(): Path?

    fun resolveDirectory(): Path?
}
