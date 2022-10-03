// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.dependency

import org.jetbrains.intellij.collectJars
import java.io.File

data class IdeaExtraDependency(
    val name: String,
    val jarFiles: Set<File>,
) {
    constructor(name: String, classes: File) : this(
        name = name,
        jarFiles = when {
            classes.isDirectory -> collectJars(classes).toSet()
            else -> setOf(classes)
        }
    )
}
