package org.jetbrains.intellij.performanceTest.parsers

import org.jetbrains.intellij.model.PerformanceTestScript
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

class SimpleIJPerformanceParser(private val filePath: String) {

    fun parse() = PerformanceTestScript.Builder().apply {
        File(filePath).forEachLine {
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
