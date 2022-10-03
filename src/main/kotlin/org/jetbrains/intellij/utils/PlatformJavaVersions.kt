// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.utils

import org.gradle.api.JavaVersion
import org.jetbrains.intellij.Version

// Java versions list used in IntelliJ Platform synchronized with:
// https://plugins.jetbrains.com/docs/intellij/build-number-ranges.html#intellij-platform-based-products-of-recent-ide-versions
internal val PlatformJavaVersions = mapOf(
    Version(222) to JavaVersion.VERSION_17,
    Version(203) to JavaVersion.VERSION_11,
    Version(0) to JavaVersion.VERSION_1_8,
)
