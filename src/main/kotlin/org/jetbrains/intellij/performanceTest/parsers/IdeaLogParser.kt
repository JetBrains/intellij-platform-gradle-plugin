package org.jetbrains.intellij.performanceTest.parsers

import org.jetbrains.intellij.performanceTest.model.PerfTestStatistic
import java.io.File

class IdeaLogParser(private val logPath: String) {

    fun getTestStatistic(): PerfTestStatistic {
        val perfTestStatisticBuilder = PerfTestStatistic.Builder()
        File(logPath).forEachLine {
            when {
                it.contains("##teamcity[buildStatisticValue") -> {
                    when {
                        it.contains("Average Responsiveness") -> perfTestStatisticBuilder.averageResponsive(parseValue(it))
                        it.contains("Responsiveness") -> perfTestStatisticBuilder.responsive(parseValue(it))
                        else -> perfTestStatisticBuilder.totalTime(parseValue(it))
                    }
                }
            }
        }

        return perfTestStatisticBuilder.build()
    }

    private fun parseValue(rawString: String): Long? {
        return "value='(?<time>[0-9]*)'".toRegex().find(rawString)?.groups?.get("time")?.value?.toLong()
    }
}
