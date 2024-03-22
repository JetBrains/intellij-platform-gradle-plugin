// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.intellij.lang.annotations.Language
import java.nio.file.Path
import kotlin.io.path.*


/**
 * Ensures that the given file exists by creating it and all of its parents.
 */
fun ensureFileExists(path: Path) = path.apply {
    parent.createDirectories()
    if (!exists()) {
        createFile()
    }
}

// Methods can be simplified when the following tickets will be handled:
// https://youtrack.jetbrains.com/issue/KT-24517
// https://youtrack.jetbrains.com/issue/KTIJ-1001
fun Path.xml(
    @Language("XML") content: String,
    override: Boolean = false,
    prepend: Boolean = false,
) = append(content, override, prepend)

fun Path.java(
    @Language("Java") content: String,
    override: Boolean = false,
    prepend: Boolean = false,
) = append(content, override, prepend)

fun Path.kotlin(
    @Language("kotlin") content: String,
    override: Boolean = false,
    prepend: Boolean = false,
) = append(content, override, prepend)

fun Path.properties(
    @Language("Properties") content: String,
    override: Boolean = false,
    prepend: Boolean = false,
) = append(content, override, prepend)

private fun Path.append(
    content: String,
    override: Boolean,
    prepend: Boolean,
) = ensureFileExists(this).also {
    when {
        prepend -> writeText(content + System.lineSeparator() + readText() + System.lineSeparator())
        override -> writeText(content + System.lineSeparator())
        else -> appendText(content + System.lineSeparator())
    }
}
