package org.jetbrains.intellij.model

data class PerformanceTestResult(
    val testName: String,
    val statistic: PerformanceTestStatistic,
    val script: PerformanceTestScript,
)
