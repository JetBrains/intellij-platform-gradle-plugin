package org.jetbrains.intellij.performanceTest.model

data class PerfTestResult(
    val testName: String,
    val statistic: PerfTestStatistic,
    val script: PerfTestScript
)
