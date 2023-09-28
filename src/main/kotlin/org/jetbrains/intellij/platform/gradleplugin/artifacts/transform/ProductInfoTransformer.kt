// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.artifacts.transform

import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.resolveProductInfoPath

@DisableCachingByDefault(because = "Not worth caching")
abstract class ProductInfoTransformer : TransformAction<TransformParameters.None> {

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asPath

        println("ProductInfoTransformer input = ${input}")
        val productInfo = input.resolveProductInfoPath()
        println("productInfo = ${productInfo}")

        outputs.file(productInfo)
    }
}

internal fun DependencyHandler.applyProductInfoTransformer() {
    artifactTypes.maybeCreate(ZIP_TYPE)
        .attributes
        .attribute(Attributes.productInfo, false)

    registerTransform(ProductInfoTransformer::class) {
        from
            .attribute(Attributes.extracted, true)
            .attribute(Attributes.collected, false)
            .attribute(Attributes.productInfo, false)
        to
            .attribute(Attributes.extracted, true)
            .attribute(Attributes.collected, false)
            .attribute(Attributes.productInfo, true)
    }
}
