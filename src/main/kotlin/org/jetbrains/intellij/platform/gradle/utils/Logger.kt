// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.plugins.IntelliJPlatformSettingsPlugin
import kotlin.reflect.KProperty

class Logger(cls: Class<*>) {

    private val logger: Logger = Logging.getLogger(cls)

    fun debug(message: String) = logger.debug("[$PLUGIN_ID] $message")
    fun error(message: String) = logger.error("[$PLUGIN_ID] $message")
    fun info(message: String) = logger.info("[$PLUGIN_ID] $message")
    fun lifecycle(message: String) = logger.lifecycle("[$PLUGIN_ID] $message")
    fun trace(message: String) = logger.trace("[$PLUGIN_ID] $message")
    fun quiet(message: String) = logger.quiet("[$PLUGIN_ID] $message")
    fun warn(message: String) = logger.warn("[$PLUGIN_ID] $message")

    operator fun getValue(intelliJPlatformSettingsPlugin: IntelliJPlatformSettingsPlugin, property: KProperty<*>): Any {
        TODO("Not yet implemented")
    }
}
