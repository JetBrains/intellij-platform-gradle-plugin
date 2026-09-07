// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.intellij.lang.annotations.Language
import java.nio.file.Path
import java.util.regex.Pattern
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private val NON_UNIX_LINE_SEPARATORS = Pattern.compile("\r\n|\r")
internal fun String.normaliseLineSeparators() = NON_UNIX_LINE_SEPARATORS.matcher(this).replaceAll("\n")

infix fun String.containsText(string: String) =
    assert(normaliseLineSeparators().contains(string)) { "expected:<$string> but was:<$this>" }

infix fun Path.containsText(string: String) =
    readText().containsText(string)

infix fun String.notContainsText(string: String) =
    assert(!normaliseLineSeparators().contains(string)) { "expected:<$string> but was: <$this>" }

fun BuildResult.assertTaskOutcome(task: String, outcome: TaskOutcome) = assertEquals(outcome, task(":$task")?.outcome)

/**
 * Checks if the given [actual] value contains the [expected] part.
 */
fun assertContains(expected: String, actual: String) = assertTrue(
    actual.normaliseLineSeparators().contains(expected),
    """
    expected:<$expected> but was:<$actual>
    """.trimIndent(),
)

/**
 * Checks if the given [actual] value doesn't contain the [expected] part.
 */
fun assertNotContains(expected: String, actual: String) = assertFalse(
    actual.normaliseLineSeparators().contains(expected),
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
    assertEquals(expected.trim(), path?.readText()?.normaliseLineSeparators()?.trim())
