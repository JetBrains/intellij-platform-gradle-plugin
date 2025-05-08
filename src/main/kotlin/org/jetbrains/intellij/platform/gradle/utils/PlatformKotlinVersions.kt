// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

/**
 * Bundled Kotlin versions for supported target platforms, synchronized with [IntelliJ Platform SDK Docs](https://jb.gg/intellij-platform-kotlin-stdlib)
 **/
val PlatformKotlinVersions = mapOf(
    Version(252) to Version(2, 1, 20),
    Version(251) to Version(2, 1, 10),
    Version(243) to Version(2, 0, 21),
    Version(242) to Version(1, 9, 24),
    Version(241) to Version(1, 9, 22),
    Version(233) to Version(1, 9, 10),
    Version(232) to Version(1, 8, 20),
    Version(231) to Version(1, 8, 0),
    Version(223) to Version(1, 7, 0)
)
