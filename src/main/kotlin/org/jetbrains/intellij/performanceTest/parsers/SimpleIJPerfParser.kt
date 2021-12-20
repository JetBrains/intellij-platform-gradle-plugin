package org.jetbrains.intellij.performanceTest.parsers

import org.jetbrains.intellij.performanceTest.model.PerfTestScript
import java.io.File
import java.time.Duration
import java.util.concurrent.TimeUnit

class SimpleIJPerfParser(private val filePath: String) {

    fun parse(): PerfTestScript {
        val perfTestScriptBuilder = PerfTestScript.Builder()
        File(filePath).forEachLine {
            when {
                it.contains(Keywords.PROJECT) -> perfTestScriptBuilder.projectName(it.substringAfter("${Keywords.PROJECT} "))
                it.contains(Keywords.ASSERT_TIMEOUT) -> perfTestScriptBuilder.assertionTimeout(convertToMillis(it.substringAfter("${Keywords.ASSERT_TIMEOUT} ")))
                else -> perfTestScriptBuilder.appendScriptContent(it)
            }
        }

        return perfTestScriptBuilder.build()
    }

    private fun convertToMillis(value: String): Long {
        when {
            value.endsWith("ms") -> return value.removeSuffix("ms").toLong()
            value.endsWith("s") -> return TimeUnit.MILLISECONDS.convert(
                Duration.ofSeconds(
                    value.removeSuffix("s").toLong()
                )
            )
            value.endsWith("M") -> return TimeUnit.MILLISECONDS.convert(
                Duration.ofMinutes(
                    value.removeSuffix("M").toLong()
                )
            )
            value.endsWith("H") -> return TimeUnit.MILLISECONDS.convert(
                Duration.ofHours(
                    value.removeSuffix("H").toLong()
                )
            )
        }

        return value.takeIf { it.isNotBlank() }?.trim()?.toLong() ?: throw RuntimeException("Value $value can't be converted to milliseconds")
    }

    private class Keywords {
        companion object {
            const val PROJECT = "%%project"
            const val ASSERT_TIMEOUT = "%%assertTimeout"
        }
    }

}
