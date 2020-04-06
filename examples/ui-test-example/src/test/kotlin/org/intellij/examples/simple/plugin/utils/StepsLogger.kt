package org.intellij.examples.simple.plugin.utils

import com.jetbrains.test.stepsProcessing.StepLogger
import com.jetbrains.test.stepsProcessing.StepWorker

object StepsLogger {
    private var initializaed = false
    @JvmStatic
    fun init() = synchronized(initializaed) {
        if (initializaed.not()) {
            StepWorker.registerProcessor(StepLogger())
            initializaed = true
        }
    }
}