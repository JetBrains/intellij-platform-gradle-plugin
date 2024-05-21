// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.helpers

import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.provider.ProviderFactory
import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.utils.asLenient
import org.jetbrains.intellij.platform.gradle.utils.platformPath

internal class IntelliJPlatformHelper(
    private val configurations: ConfigurationContainer,
    private val providers: ProviderFactory,
) {

    private val intelliJPlatformConfiguration
        get() = configurations[Configurations.INTELLIJ_PLATFORM].asLenient

    internal val productInfo
        get() = providers.provider { intelliJPlatformConfiguration.productInfo() }

    internal val platformPath
        get() = providers.provider { intelliJPlatformConfiguration.platformPath() }
}
