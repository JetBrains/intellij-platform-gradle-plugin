// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Internal
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.registerTransform
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.resolvers.path.takeIfExists
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.platformPath
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * The artifact transformer collecting JAR files located within the IntelliJ Platform or Marketplace Plugin archives.
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CollectorTransformer : TransformAction<CollectorTransformer.Parameters> {

    interface Parameters : TransformParameters {

        @get:Internal
        val intellijPlatform: ConfigurableFileCollection
    }

    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val manager = IdePluginManager.createManager(createTempDirectory())
    private val log = Logger(javaClass)

    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val path = inputArtifact.asPath
            val productInfo = parameters.intellijPlatform.platformPath().productInfo()
            val plugin by lazy {
                val pluginPath = generateSequence(path) {
                    it.takeIf { it.resolve("lib").exists() } ?: it.listDirectoryEntries().singleOrNull()
                }.firstOrNull { it.resolve("lib").exists() } ?: throw GradleException("Could not resolve plugin directory: '$path'")

                val pluginCreationResult = manager.createPlugin(pluginPath, false)

                require(pluginCreationResult is PluginCreationSuccess)
                pluginCreationResult.plugin
            }

            val isIntelliJPlatform = path == parameters.intellijPlatform.platformPath()
            val isPlugin = !isIntelliJPlatform && runCatching { plugin }.isSuccess

            when {
                isIntelliJPlatform -> {
                    collectIntelliJPlatformJars(productInfo, path)
                        .forEach { outputs.file(it) }
                }

                isPlugin -> {
                    plugin.originalFile?.let { pluginPath ->
                        collectJars(
                            pluginPath.resolve("lib"),
                            pluginPath.resolve("lib/modules"),
                        ).forEach { outputs.file(it) }
                    }
                }

                else -> throw GradleException("Unknown input: $path")
            }
        }.onFailure {
            log.error("${javaClass.canonicalName} execution failed.", it)
        }
    }

    private fun collectJars(vararg paths: Path?) = paths
        .mapNotNull { it?.takeIfExists() }
        .flatMap { it.listDirectoryEntries("*.jar") }

    companion object {
        internal fun register(
            dependencies: DependencyHandler,
            compileClasspathConfiguration: Configuration,
            testCompileClasspathConfiguration: Configuration,
            intellijPlatformConfiguration: Configuration,
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

                parameters {
                    intellijPlatform = intellijPlatformConfiguration
                }
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
