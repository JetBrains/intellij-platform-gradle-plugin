// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.forEachDirectoryEntry
import kotlin.io.path.listDirectoryEntries

/**
 * The artifact transformer collecting JAR files located within the IntelliJ Platform or Marketplace Plugin archives.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CollectorTransformer : TransformAction<TransformParameters.None> {

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val log = Logger(javaClass)

    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val path = inputArtifact.asPath
            val productInfo = runCatching { path.productInfo() }.getOrNull()

            when (productInfo) {
                null -> {
                    path.forEachDirectoryEntry { entry ->
                        listOfNotNull(
                            entry.resolve("lib"),
                            entry.resolve("lib/modules")
                        ).flatMap {
                            it.listDirectoryEntries("*.jar")
                        }.forEach {
                            outputs.file(it)
                        }
                    }
                }

                else -> {
                    collectIntelliJPlatformJars(path.productInfo(), path)
                        .forEach { outputs.file(it) }
                }
            }
        }.onFailure {
            log.error("${javaClass.canonicalName} execution failed.", it)
        }
    }

    companion object {
        internal fun register(
            dependencies: DependencyHandler,
            compileClasspathConfiguration: Configuration,
            testCompileClasspathConfiguration: Configuration,
        ) {
            Attributes.ArtifactType.values().forEach {
                dependencies.artifactTypes.maybeCreate(it.toString())
                    .attributes.attribute(Attributes.collected, false)
            }

            compileClasspathConfiguration
                .attributes
                .attribute(Attributes.collected, true)

            testCompileClasspathConfiguration
                .attributes
                .attribute(Attributes.collected, true)

            dependencies.registerTransform(CollectorTransformer::class) {
                from
                    .attribute(Attributes.extracted, true)
                    .attribute(Attributes.collected, false)
                to
                    .attribute(Attributes.extracted, true)
                    .attribute(Attributes.collected, true)
            }
        }

    }
}

internal fun collectIntelliJPlatformJars(productInfo: ProductInfo, intellijPlatformPath: Path) =
    productInfo.launch
        .asSequence()
        .filter { it.os == ProductInfo.Launch.OS.current }
        .flatMap { it.bootClassPathJarNames }
        .map { "lib/$it" }
        .plus( // TODO: pick only relevant entries. TBD with VK
            productInfo.layout
                .asSequence()
                .filter { it.kind == ProductInfo.LayoutItemKind.productModuleV2 }
                .flatMap { it.classPath }
        )
        .map { intellijPlatformPath.resolve(it) }
        .mapNotNull { it.takeIf { it.exists() } }
        .toSet()

internal fun collectBundledPluginsJars(intellijPlatformPath: Path) =
    intellijPlatformPath
        .resolve("plugins")
        .listDirectoryEntries()
        .asSequence()
        .flatMap { it.resolve("lib") + it.resolve("lib/modules") }
        .mapNotNull { it.takeIf { it.exists() } }
        .flatMap { it.listDirectoryEntries("*.jar") }
        .toSet()
