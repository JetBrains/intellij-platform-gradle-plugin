// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.GradleException
import org.gradle.api.provider.ProviderFactory

data class Variant(val os: String, val arch: String)

val variants = listOf(
    Variant("linux", "x86_64"),
    Variant("linux", "arm64"),
    Variant("mac", "x86_64"),
    Variant("mac", "arm64"),
    Variant("windows", "x86_64"),
    Variant("windows", "arm64")
)

internal fun ProviderFactory.currentVariant() =
    systemProperty("os.name").zip(systemProperty("os.arch")) { osName, architecture ->
        currentVariant(osName, architecture)
    }

internal fun currentVariant(osName: String, architecture: String) = Variant(
    os = when {
        osName.contains("linux", ignoreCase = true) -> "linux"
        osName.contains("mac", ignoreCase = true) || osName.contains("darwin", ignoreCase = true) -> "mac"
        osName.contains("windows", ignoreCase = true) -> "windows"
        else -> throw GradleException("Unsupported operating system for native variants: '$osName'.")
    },
    arch = when (architecture.lowercase()) {
        "amd64", "x86_64", "x64" -> "x86_64"
        "aarch64", "arm64" -> "arm64"
        else -> throw GradleException("Unsupported architecture for native variants: '$architecture'.")
    },
)
