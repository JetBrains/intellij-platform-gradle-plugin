// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.jbr

import java.io.File

internal data class Jbr(
    val version: String,
    val javaHome: File,
    val javaExecutable: String?,
)
