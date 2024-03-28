// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.intellij.lang.annotations.Language
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Checks if the given [actual] value contains the [expected] part.
 */
fun assertContains(expected: String, actual: String) = assertTrue(
    actual.contains(expected),
    """
    expected:<$expected> but was:<$actual>
    """.trimIndent(),
)

/**
 * Checks if the given [actual] value doesn't contain the [expected] part.
 */
fun assertNotContains(expected: String, actual: String) = assertFalse(
    actual.contains(expected),
    """
    expected:<$expected> but was:<$actual>
    """.trimIndent(),
)

/**
 * Checks if the given [path] exists in the filesystem.
 */
fun assertExists(path: Path) =
    assert(path.exists()) { "expect that '$path' exists" }

fun assertFileContent(path: Path?, @Language("xml") expected: String) =
    assertEquals(expected.trim(), path?.readText()?.replace("\r", "")?.trim())
