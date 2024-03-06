// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

data class PerformanceTestResult(
    val testName: String,
    val statistic: PerformanceTestStatistic,
    val script: PerformanceTestScript,
)
