// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.settings

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.jetbrains.intellij.platform.gradle.Constants.Plugins
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import org.jetbrains.intellij.platform.gradle.plugins.checkGradleVersion
import org.jetbrains.intellij.platform.gradle.utils.Logger

@Suppress("unused", "UnstableApiUsage")
abstract class IntelliJPlatformSettingsPlugin : Plugin<Settings> {

    private val log = Logger(javaClass)

    override fun apply(settings: Settings) {
        log.info("Configuring plugin: ${Plugins.SETTINGS}")

        checkGradleVersion()

        IntelliJPlatformRepositoriesExtension.register(settings, target = settings.dependencyResolutionManagement.repositories)
    }
}
