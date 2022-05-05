// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.model

import kotlinx.serialization.Serializable

@Serializable
data class ProductInfo(
    var name: String? = null,
    var version: String? = null,
    var versionSuffix: String? = null,
    var buildNumber: String? = null,
    var productCode: String? = null,
    var dataDirectoryName: String? = null,
    var svgIconPath: String? = null,
    val launch: List<Launch> = mutableListOf(),
    val customProperties: List<CustomProperty> = mutableListOf(),
)

@Serializable
data class Launch(
    var os: String? = null,
    var launcherPath: String? = null,
    var javaExecutablePath: String? = null,
    var vmOptionsFilePath: String? = null,
)

@Serializable
data class CustomProperty(
    var key: String? = null,
    var value: String? = null
)
