// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import java.nio.file.Path
import kotlin.io.path.*

/**
 * Ensures that the given file exists by creating it and all of its parents.
 */
fun Path.ensureExists() = apply {
    parent.createDirectories()
    if (!exists()) {
        createFile()
    }
}

infix fun Path.write(content: String) = ensureExists().apply { appendText(content + "\n") }
infix fun Path.overwrite(content: String) = ensureExists().apply { writeText(content + "\n") }
infix fun Path.prepend(content: String) = ensureExists().apply { writeText(content + "\n" + readText() + "\n") }
