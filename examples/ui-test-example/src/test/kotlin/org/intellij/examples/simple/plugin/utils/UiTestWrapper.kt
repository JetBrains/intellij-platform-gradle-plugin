package org.intellij.examples.simple.plugin.utils

import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.stepsProcessing.StepLogger
import com.jetbrains.test.stepsProcessing.StepWorker


object StepsLogger {
    init {
        StepWorker.registerProcessor(StepLogger())
    }

    fun init() {}
}

fun uiTest(url: String = "http://127.0.0.1:8082", test: RemoteRobot.() -> Unit) {
    RemoteRobot(url).apply(test)
}

