// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import org.gradle.api.JavaVersion

/**
 * Java versions list used in IntelliJ Platform synchronized with [IntelliJ Platform SDK Docs](https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#intellij-platform-based-products-of-recent-ide-versions)
 */
val PlatformJavaVersions = mapOf(
    Version(242) to JavaVersion.VERSION_21,
    Version(222) to JavaVersion.VERSION_17,
    Version(203) to JavaVersion.VERSION_11,
    Version(0) to JavaVersion.VERSION_1_8,
)

internal fun String.toJavaVersion() = JavaVersion.toVersion(this)
