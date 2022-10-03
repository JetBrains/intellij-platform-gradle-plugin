// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.performanceTest.parsers

import com.jetbrains.plugin.structure.base.utils.forEachLine
import org.jetbrains.intellij.model.PerformanceTestScript
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.TimeUnit

internal class SimpleIJPerformanceParser(private val filePath: String) {

    fun parse() = PerformanceTestScript.Builder().apply {
        Path.of(filePath).forEachLine {
            with(it) {
                when {
                    contains(Keywords.PROJECT) -> projectName(substringAfter("${Keywords.PROJECT} "))
                    contains(Keywords.ASSERT_TIMEOUT) -> assertionTimeout(substringAfter("${Keywords.ASSERT_TIMEOUT} ").convertToMillis())
                    else -> appendScriptContent(this)
                }
            }
        }
    }.build()
}

private fun String.convertToMillis() = when {
    endsWith("ms") -> removeSuffix("ms").toLong()

    endsWith("s") -> TimeUnit.MILLISECONDS.convert(
        Duration.ofSeconds(
            removeSuffix("s").toLong()
        )
    )

    endsWith("M") -> TimeUnit.MILLISECONDS.convert(
        Duration.ofMinutes(
            removeSuffix("M").toLong()
        )
    )

    endsWith("H") -> TimeUnit.MILLISECONDS.convert(
        Duration.ofHours(
            removeSuffix("H").toLong()
        )
    )

    else -> takeIf { it.isNotBlank() }?.trim()?.toLong()
} ?: throw RuntimeException("Value $this can't be converted to milliseconds")

private class Keywords {
    companion object {
        const val PROJECT = "%%project"
        const val ASSERT_TIMEOUT = "%%assertTimeout"
    }
}
