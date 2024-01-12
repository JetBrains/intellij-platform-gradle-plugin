// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.base

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.toVersion
import java.nio.file.Path

interface PlatformVersionAware {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatformConfiguration: ConfigurableFileCollection

    @get:Internal
    val platformPath: Path
        get() = intelliJPlatformConfiguration.single().toPath()

    @get:Internal
    val productInfo: ProductInfo
        get() = platformPath.productInfo()

    @Throws(IllegalArgumentException::class)
    fun assertPlatformVersion() {
        val build = productInfo.buildNumber.toVersion()
        val version = productInfo.version.toVersion()

        if (build < Version(223)) {
            throw IllegalArgumentException("The minimal supported IntelliJ Platform version is 2022.3 (223.0), which is higher than provided: $version ($build)")
        }
    }
}
