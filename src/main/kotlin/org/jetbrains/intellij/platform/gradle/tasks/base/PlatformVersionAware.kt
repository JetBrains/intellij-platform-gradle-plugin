// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.base

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.Version
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.productInfo

interface PlatformVersionAware {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatform: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatformProductInfo: ConfigurableFileCollection

    @get:Internal
    val productInfo: ProductInfo
        get() = intelliJPlatformProductInfo.single().toPath().productInfo()

    @get:Internal
    val platformBuild: Version
        get() = Version.parse(productInfo.buildNumber)

    @get:Internal
    val platformVersion: Version
        get() = Version.parse(productInfo.version)

    @get:Internal
    val platformType: IntelliJPlatformType
        get() = IntelliJPlatformType.fromCode(productInfo.productCode)

    fun assertPlatformVersion() {
        if (platformBuild < Version(223)) {
            throw IllegalArgumentException("The minimal supported IntelliJ Platform version is 2022.3 (233.0), which is higher than provided: $platformVersion ($platformBuild)")
        }
    }
}
