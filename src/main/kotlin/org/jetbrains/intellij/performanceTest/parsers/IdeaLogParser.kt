// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
