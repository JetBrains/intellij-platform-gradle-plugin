// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

data class Variant(val os: String, val arch: String)

val variants = listOf(
    Variant("linux", "x86_64"),
    Variant("linux", "arm64"),
    Variant("mac", "x86_64"),
    Variant("mac", "arm64"),
    Variant("windows", "x86_64"),
    Variant("windows", "arm64")
)
