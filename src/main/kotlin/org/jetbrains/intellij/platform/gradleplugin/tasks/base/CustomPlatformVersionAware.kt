// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks.base

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradleplugin.model.ProductInfo
import org.jetbrains.intellij.platform.gradleplugin.model.productInfo

interface CustomPlatformVersionAware : PlatformVersionAware {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val customIntelliJPlatform: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    val customIntelliJPlatformProductInfo: ConfigurableFileCollection

    @get:Internal
    val customIntelliJPlatformDirectory
        get() = customIntelliJPlatform.single().toPath()

    @get:Internal
    val customProductInfo: ProductInfo
        get() = customIntelliJPlatformProductInfo.single().toPath().productInfo()

    @get:Input
    @get:Optional
    val type: Property<IntelliJPlatformType>

    @get:Input
    @get:Optional
    val version: Property<String>

    @get:InputDirectory
    @get:Optional
    val localPath: DirectoryProperty
}
