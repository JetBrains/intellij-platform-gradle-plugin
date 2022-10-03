// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.model

import kotlinx.serialization.Serializable
import org.gradle.api.GradleException
import org.gradle.internal.os.OperatingSystem

@Serializable
internal data class ProductInfo(
    var name: String? = null,
    var version: String? = null,
    var versionSuffix: String? = null,
    var buildNumber: String? = null,
    var productCode: String? = null,
    var dataDirectoryName: String? = null,
    var svgIconPath: String? = null,
    var productVendor: String? = null,
    val launch: List<Launch> = mutableListOf(),
    val customProperties: List<CustomProperty> = mutableListOf(),
    val bundledPlugins: List<String> = mutableListOf(),
    val fileExtensions: List<String> = mutableListOf(),
    val modules: List<String> = mutableListOf(),
) {
    val currentLaunch: Launch
        get() = with(OperatingSystem.current()) {
            launch.find {
                when {
                    isLinux -> OS.Linux
                    isWindows -> OS.Windows
                    isMacOsX -> OS.macOS
                    else -> OS.Linux
                } == it.os
            } ?: throw GradleException("Could not find launch information for the current OS: $name")
        }
}

@Serializable
internal data class Launch(
    var os: OS? = null,
    var launcherPath: String? = null,
    var javaExecutablePath: String? = null,
    var vmOptionsFilePath: String? = null,
    var startupWmClass: String? = null,
    val bootClassPathJarNames: List<String> = mutableListOf(),
    val additionalJvmArguments: List<String> = mutableListOf(),
)

@Serializable
internal data class CustomProperty(
    var key: String? = null,
    var value: String? = null,
)

@Suppress("EnumEntryName")
internal enum class OS {
    Linux, Windows, macOS
}
