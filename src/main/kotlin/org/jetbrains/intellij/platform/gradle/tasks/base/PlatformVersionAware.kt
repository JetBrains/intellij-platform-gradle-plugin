// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.base

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.Version
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.toVersion

interface PlatformVersionAware {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val intelliJPlatform: ConfigurableFileCollection

    @get:Internal
    val productInfo: Property<ProductInfo>
    
    @Throws(IllegalArgumentException::class)
    fun assertPlatformVersion() {
        val build = productInfo.map { it.buildNumber.toVersion() }.get()
        val version = productInfo.map { it.version.toVersion() }.get()

        if (build < Version(223)) {
            throw IllegalArgumentException("The minimal supported IntelliJ Platform version is 2022.3 (233.0), which is higher than provided: $version ($build)")
        }
    }
}
