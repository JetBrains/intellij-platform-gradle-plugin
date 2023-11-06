// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Extensions
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformRepositoriesExtension
import javax.inject.Inject

@Suppress("unused", "UnstableApiUsage")
abstract class IntelliJPlatformSettingsPlugin @Inject constructor(
    private val providers: ProviderFactory,
) : Plugin<Settings> {

    override fun apply(settings: Settings) {
        with(settings.dependencyResolutionManagement.repositories) {
            configureExtension<IntelliJPlatformRepositoriesExtension>(Extensions.INTELLIJ_PLATFORM, this, providers)
        }
    }
}
