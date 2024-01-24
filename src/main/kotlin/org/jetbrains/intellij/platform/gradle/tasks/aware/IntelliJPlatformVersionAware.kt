// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.aware

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.assertSupportedVersion
import org.jetbrains.intellij.platform.gradle.model.productInfo
import java.nio.file.Path

interface IntelliJPlatformVersionAware {

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
    fun assertIntelliJPlatformSupportedVersion() = productInfo.assertSupportedVersion()
}
