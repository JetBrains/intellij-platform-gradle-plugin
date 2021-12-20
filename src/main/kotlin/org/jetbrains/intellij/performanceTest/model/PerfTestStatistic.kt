package org.jetbrains.intellij.performanceTest.model

class PerfTestStatistic private constructor(val totalTime: Long?, val responsive: Long?, val averageResponsive: Long?) {
    data class Builder(var totalTime: Long? = null, var responsive: Long? = null, var averageResponsive: Long? = null) {
        fun totalTime(value: Long?): Builder = apply { this.totalTime = value }
        fun responsive(value: Long?): Builder = apply { this.responsive = value }
        fun averageResponsive(value: Long?): Builder = apply { this.averageResponsive = value }
        fun build() = PerfTestStatistic(totalTime, responsive, averageResponsive)
    }
}
