// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.performanceTest.parsers

import com.jetbrains.plugin.structure.base.utils.forEachLine
import org.jetbrains.intellij.platform.gradleplugin.model.PerformanceTestStatistic
import java.nio.file.Path

class IdeaLogParser(private val logPath: String) {

    fun getTestStatistic() = PerformanceTestStatistic.Builder().apply {
        Path.of(logPath).forEachLine {
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
