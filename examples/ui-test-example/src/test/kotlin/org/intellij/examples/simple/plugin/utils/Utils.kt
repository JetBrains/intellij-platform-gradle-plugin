package org.intellij.examples.simple.plugin.utils

import com.intellij.openapi.project.DumbService
import com.jetbrains.test.RemoteRobot
import com.jetbrains.test.stepsProcessing.step
import com.jetbrains.test.utils.waitFor
import java.time.Duration

fun uiTest(url: String = "http://127.0.0.1:8082", test: RemoteRobot.() -> Unit) {
    RemoteRobot(url).apply(test)
}

