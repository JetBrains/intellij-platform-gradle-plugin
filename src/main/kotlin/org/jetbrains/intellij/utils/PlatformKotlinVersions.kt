// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.utils

import org.jetbrains.intellij.Version

// Bundled Kotlin versions list synchronized with:
// https://plugins.jetbrains.com/docs/intellij/kotlin.html#kotlin-standard-library
internal val PlatformKotlinVersions = mapOf(
    Version(222) to Version(1, 6, 21),
    Version(221) to Version(1, 6, 20),
    Version(213) to Version(1, 5, 10),
    Version(212) to Version(1, 5, 10),
    Version(211) to Version(1, 4, 32),
    Version(203) to Version(1, 4, 0),
    Version(202) to Version(1, 3, 70),
    Version(201) to Version(1, 3, 70),
    Version(193) to Version(1, 3, 31),
    Version(192) to Version(1, 3, 3),
    Version(191) to Version(1, 3, 11),
)
