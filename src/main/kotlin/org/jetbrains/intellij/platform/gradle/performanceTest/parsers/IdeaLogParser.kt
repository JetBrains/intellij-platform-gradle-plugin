// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.performanceTest.parsers

import org.jetbrains.intellij.platform.gradle.models.PerformanceTestStatistic
import kotlin.io.path.Path
import kotlin.io.path.forEachLine

class IdeaLogParser(private val logPath: String) {

    fun getTestStatistic() = PerformanceTestStatistic.Builder().apply {
        Path(logPath).forEachLine {
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

    private fun String.parseValue() = "value='(?<time>\\d*)'".toRegex().find(this)?.groups?.get("time")?.value?.toLong()
}
