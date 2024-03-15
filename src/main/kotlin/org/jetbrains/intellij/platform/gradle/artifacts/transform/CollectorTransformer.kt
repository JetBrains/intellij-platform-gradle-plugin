// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.DIRECTORY_TYPE
import org.gradle.api.artifacts.type.ArtifactTypeDefinition.ZIP_TYPE
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.JETBRAINS_MARKETPLACE_MAVEN_GROUP
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.runLogging
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * The artifact transformer collecting JAR files located within the IntelliJ Platform or Marketplace Plugin archives.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CollectorTransformer : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) = runLogging {
        val input = inputArtifact.asPath

        if (input.name.startsWith(JETBRAINS_MARKETPLACE_MAVEN_GROUP)) {
            // Plugin dependency
            input.forEachDirectoryEntry { entry ->
                entry.resolve("lib")
                    .listDirectoryEntries("*.jar")
                    .forEach { outputs.file(it) }
            }
        } else {
            // IntelliJ Platform SDK dependency
            collectIntelliJPlatformJars(input.productInfo(), input)
                .forEach { outputs.file(it) }
        }
    }
}

internal fun collectIntelliJPlatformJars(productInfo: ProductInfo, intellijPlatformPath: Path): Set<Path> {
    return productInfo.launch
        .filter { it.os == ProductInfo.Launch.OS.current }
        .flatMap { it.bootClassPathJarNames }
        .asSequence()
        .map { intellijPlatformPath.resolve("lib/$it") }
        .mapNotNull { it.takeIf { it.exists() } }
        .toSet()
}

internal fun collectBundledPluginsJars(intellijPlatformPath: Path) =
    intellijPlatformPath
        .resolve("plugins")
        .listDirectoryEntries()
        .asSequence()
        .map { it.resolve("lib") }
        .mapNotNull { it.takeIf { it.exists() } }
        .flatMap { it.listDirectoryEntries("*.jar") }
        .toSet()

internal fun DependencyHandler.applyCollectorTransformer(
    compileClasspathConfiguration: Configuration,
    testCompileClasspathConfiguration: Configuration,
) {
    // ZIP archives fetched from the IntelliJ Maven repository
    artifactTypes.maybeCreate(ZIP_TYPE)
        .attributes
        .attribute(Attributes.collected, false)

    // Local IDEs pointed with intellijPlatformLocal dependencies helper
    artifactTypes.maybeCreate(DIRECTORY_TYPE)
        .attributes
        .attribute(Attributes.collected, false)

    compileClasspathConfiguration
        .attributes
        .attribute(Attributes.collected, true)

    testCompileClasspathConfiguration
        .attributes
        .attribute(Attributes.collected, true)

    registerTransform(CollectorTransformer::class) {
        from
            .attribute(Attributes.extracted, true)
            .attribute(Attributes.collected, false)
        to
            .attribute(Attributes.extracted, true)
            .attribute(Attributes.collected, true)
    }
}
