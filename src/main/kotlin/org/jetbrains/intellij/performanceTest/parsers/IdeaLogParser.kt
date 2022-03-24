package org.jetbrains.intellij.performanceTest.parsers

import org.jetbrains.intellij.model.PerformanceTestStatistic
import java.io.File

class IdeaLogParser(private val logPath: String) {

    fun getTestStatistic() = PerformanceTestStatistic.Builder().apply {
        File(logPath).forEachLine {
            with(it) {
                when {
                    contains("##teamcity[buildStatisticValue") -> {
                        when {
                            contains("Average Responsiveness") -> averageResponsive(parseValue())
                            contains("Responsiveness") -> responsive(parseValue())
                            else -> totalTime(parseValue())
                        }
                    }
                }
            }

        }
    }.build()

    private fun String.parseValue() =
        "value='(?<time>\\d*)'".toRegex().find(this)?.groups?.get("time")?.value?.toLong()
}
