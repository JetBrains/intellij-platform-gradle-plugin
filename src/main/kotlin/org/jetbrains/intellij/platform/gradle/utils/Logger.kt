// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.logging.Logging
import org.jetbrains.intellij.platform.gradle.Constants.Plugin

class Logger(cls: Class<*>) {

    private val logger = Logging.getLogger(cls)
    private val prefix = Plugin.LOG_PREFIX

    fun debug(message: String, e: Throwable? = null) = logger.debug("$prefix $message", e)
    fun error(message: String, e: Throwable? = null) = logger.error("$prefix $message", e)
    fun info(message: String, e: Throwable? = null) = logger.info("$prefix $message", e)
    fun lifecycle(message: String, e: Throwable? = null) = logger.lifecycle("$prefix $message", e)
    fun trace(message: String, e: Throwable? = null) = logger.trace("$prefix $message", e)
    fun quiet(message: String, e: Throwable? = null) = logger.quiet("$prefix $message", e)
    fun warn(message: String, e: Throwable? = null) = logger.warn("$prefix $message", e)
}
