// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

@file:JvmName("Utils")

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.Logging
import org.jetbrains.intellij.platform.gradle.plugins.IntelliJPlatformPlugin

@Deprecated(message = "Use Logger")
fun error(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.ERROR, logCategory, message, e)

@Deprecated(message = "Use Logger")
fun warn(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.WARN, logCategory, message, e)

@Deprecated(message = "Use Logger")
fun info(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.INFO, logCategory, message, e)

@Deprecated(message = "Use Logger")
fun debug(logCategory: String? = null, message: String, e: Throwable? = null) = log(LogLevel.DEBUG, logCategory, message, e)

private val logger = Logging.getLogger(IntelliJPlatformPlugin::class.java)

private fun log(level: LogLevel, logCategory: String?, message: String, e: Throwable?) {
    val category = "gradle-intellij-plugin ${logCategory.orEmpty()}".trim()
    if (e != null && level != LogLevel.ERROR && !logger.isDebugEnabled) {
        logger.log(level, "[$category] $message. Run with --debug option to get more log output.")
    } else {
        logger.log(level, "[$category] $message", e)
    }
}

fun Project.logCategory(): String = path + if (path.endsWith(name)) " $name" else ""

fun Task.logCategory(): String = project.logCategory() + path.removePrefix(project.logCategory())
