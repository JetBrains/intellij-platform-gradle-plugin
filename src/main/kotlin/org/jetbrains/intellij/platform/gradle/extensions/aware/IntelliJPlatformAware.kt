// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions.aware

import org.gradle.kotlin.dsl.get
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.utils.asLenient
import org.jetbrains.intellij.platform.gradle.utils.platformPath

interface IntelliJPlatformAware : DependencyAware

internal val IntelliJPlatformAware.intelliJPlatformConfiguration
    get() = configurations[Configurations.INTELLIJ_PLATFORM].asLenient

internal val IntelliJPlatformAware.productInfo
    get() = intelliJPlatformConfiguration.productInfo()

internal val IntelliJPlatformAware.platformPath
    get() = intelliJPlatformConfiguration.platformPath()
