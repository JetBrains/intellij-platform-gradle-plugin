// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ProviderFactory
import org.jetbrains.intellij.platform.gradleplugin.repositories.applyIntelliJPlatformSettings
import javax.inject.Inject

@Suppress("UnstableApiUsage", "unused")
abstract class IntelliJPlatformSettingsPlugin @Inject constructor(
    private val objects: ObjectFactory,
    private val providers: ProviderFactory,
) : Plugin<Settings> {

    override fun apply(settings: Settings) {
        settings.dependencyResolutionManagement.repositories.applyIntelliJPlatformSettings(objects, providers)
    }
}
