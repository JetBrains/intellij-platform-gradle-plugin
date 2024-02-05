// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.provider

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.productInfo

/**
 * Shorthand value source provider for obtaining the [ProductInfo] out of the [Configurations.INTELLIJ_PLATFORM] configuration.
 */
abstract class ProductInfoValueSource : ValueSource<ProductInfo, ProductInfoValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        /**
         * The [Configurations.INTELLIJ_PLATFORM] configuration holding the currently used IntelliJ Platform.
         */
        val intelliJPlatformConfiguration: ConfigurableFileCollection
    }

    override fun obtain() = parameters.intelliJPlatformConfiguration.productInfo()
}
