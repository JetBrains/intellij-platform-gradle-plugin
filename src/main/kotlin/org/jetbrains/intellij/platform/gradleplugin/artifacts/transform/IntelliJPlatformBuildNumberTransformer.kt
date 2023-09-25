// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.artifacts.transform

import org.gradle.api.Project
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradleplugin.asPath
import org.jetbrains.intellij.platform.gradleplugin.or
import kotlin.io.path.exists

@DisableCachingByDefault(because = "Not worth caching")
abstract class IntelliJPlatformBuildNumberTransformer : TransformAction<TransformParameters.None> {

    @get:Classpath
    @get:InputArtifact
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asPath

        val buildFile = input
            .resolve("Resources/build.txt")
            .takeIf { OperatingSystem.current().isMacOsX && it.exists() }
            .or { input.resolve("build.txt") }

        outputs.file(buildFile)
    }
}


internal fun Project.applyIntellijPlatformBuildNumberTransformer() {
    project.dependencies {
        attributesSchema {
            attribute(Attributes.buildNumber)
        }

        artifactTypes.maybeCreate(ZIP_TYPE)
            .attributes
            .attribute(Attributes.buildNumber, false)

        registerTransform(IntelliJPlatformBuildNumberTransformer::class) {
            from
                .attribute(Attributes.extracted, true)
                .attribute(Attributes.collected, false)
                .attribute(Attributes.buildNumber, false)
            to
                .attribute(Attributes.extracted, true)
                .attribute(Attributes.collected, false)
                .attribute(Attributes.buildNumber, true)
        }
    }
}
