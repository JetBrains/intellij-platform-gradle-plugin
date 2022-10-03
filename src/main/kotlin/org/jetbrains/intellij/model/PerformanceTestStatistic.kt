// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.model

internal class PerformanceTestStatistic private constructor(
    val totalTime: Long?,
    val responsive: Long?,
    val averageResponsive: Long?,
) {

    data class Builder(
        var totalTime: Long? = null,
        var responsive: Long? = null,
        var averageResponsive: Long? = null,
    ) {
        fun totalTime(value: Long?) = apply { totalTime = value }

        fun responsive(value: Long?) = apply { responsive = value }

        fun averageResponsive(value: Long?) = apply { averageResponsive = value }

        fun build() = PerformanceTestStatistic(totalTime, responsive, averageResponsive)
    }
}
