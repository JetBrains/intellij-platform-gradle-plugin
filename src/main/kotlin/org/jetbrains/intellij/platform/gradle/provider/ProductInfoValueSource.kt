// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.provider

import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.jetbrains.intellij.platform.gradle.model.ProductInfo
import org.jetbrains.intellij.platform.gradle.model.productInfo

abstract class ProductInfoValueSource : ValueSource<ProductInfo, ProductInfoValueSource.Parameters> {

    interface Parameters : ValueSourceParameters {
        val productInfoConfiguration: ConfigurableFileCollection
    }

    override fun obtain() = parameters.productInfoConfiguration.single().toPath().productInfo()
}
