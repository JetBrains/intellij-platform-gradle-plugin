// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.tasks.base

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradleplugin.model.ProductInfo
import org.jetbrains.intellij.platform.gradleplugin.productInfo

abstract class IdeVersionAwareTask : DefaultTask() {

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val intelliJPlatformProductInfo: ConfigurableFileCollection

    @get:Internal
    val productInfo: ProductInfo
        get() = intelliJPlatformProductInfo.also {
            println("intelliJPlatformProductInfo = \n${intelliJPlatformProductInfo.joinToString("\n")}")
        }.single().toPath().productInfo()

    @get:Internal
    val ideVersion: IdeVersion
        get() = IdeVersion.createIdeVersion(productInfo.buildNumber)
}
