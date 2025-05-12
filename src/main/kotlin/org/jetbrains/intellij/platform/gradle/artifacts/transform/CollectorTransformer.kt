// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

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
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.artifacts.LocalIvyArtifactPathComponentMetadataRule
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.resolvers.path.takeIfExists
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.utils.*
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries

/**
 * The artifact transformer collecting JAR files located within the IntelliJ Platform or plugin archives.
 * @see LocalIvyArtifactPathComponentMetadataRule
 */
@DisableCachingByDefault(because = "Not worth caching")
abstract class CollectorTransformer : TransformAction<TransformParameters.None> {

    /**
     * The input artifact provided for transformation.
     */
    @get:InputArtifact
    @get:Classpath
    abstract val inputArtifact: Provider<FileSystemLocation>

    private val manager = IdePluginManager.createManager(createTempDirectory())
    private val log = Logger(javaClass)

    /**
     * The transform action determines if the [inputArtifact] is the currently used IntelliJ Platform or a plugin.
     *
     * @throws GradleException
     */
    @Throws(GradleException::class)
    override fun transform(outputs: TransformOutputs) {
        runCatching {
            val path = inputArtifact.asPath
            val plugin by lazy {
                val pluginPath = path.resolvePluginPath()
                manager.safelyCreatePlugin(pluginPath, suppressPluginProblems = true).getOrThrow()
            }

            val productInfo = runCatching { path.resolvePlatformPath().productInfo() }.getOrNull()
            val isIntelliJPlatform = productInfo != null
            val isPlugin = !isIntelliJPlatform && runCatching { plugin }.isSuccess

            when {
                isIntelliJPlatform -> {
                    collectIntelliJPlatformJars(requireNotNull(productInfo), path)
                        .forEach { outputs.file(it) }
                }

                /**
                 * Normally, we get into here for third-party JetBrains Marketplace plugins.
                 * https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html#non-bundled-plugin
                 *
                 * For other plugins, we never (usually?) get into this block because their Ivy artifacts already list jars,
                 * instead of pointing to a directory, see: [org.jetbrains.intellij.platform.gradle.artifacts.LocalIvyArtifactPathComponentMetadataRule]
                 */
                isPlugin -> {
                    plugin.originalFile?.let { pluginPath ->
                        val jars = collectJars(pluginPath)
                        jars.forEach {
                            outputs.file(it)
                        }
                    }
                }

                else -> throw GradleException("Unknown input: $path")
            }
        }.onFailure {
            log.error("${javaClass.canonicalName} execution failed.", it)
        }
    }

    companion object {
        internal fun collectJars(path: Path): List<Path> {
            val libPath = path.resolve(Sandbox.Plugin.LIB)
            val libModulesPath = libPath.resolve(Sandbox.Plugin.MODULES)

            return listOf(libPath, libModulesPath)
                .mapNotNull { it.takeIfExists() }
                .flatMap { it.listDirectoryEntries("*.jar") }
        }

        internal fun register(dependencies: DependencyHandler) {
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
        .plus(
            when (productInfo.productCode.toIntelliJPlatformType()) {
                IntelliJPlatformType.Rider ->
                    productInfo.layout
                        .filter { it.name == "intellij.rider" }
                        .flatMap { it.classPath }

                IntelliJPlatformType.CLion ->
                    productInfo.layout
                        .filter { it.name == "com.intellij.clion" }
                        .flatMap { it.classPath }

                else -> emptyList()
            }
        )
        .plus(
            productInfo.layout
                .filter { it.name == "com.intellij" }
                .flatMap { it.classPath }
        )
        .minus("lib/junit4.jar") // exclude `junit4.jar` from the list as JUnit shouldn't be in the classpath
        .minus("lib/testFramework.jar") // same for the Test Framework fat jar
        .map { intellijPlatformPath.resolve(it) }
        .mapNotNull { it.takeIf { it.exists() } }
        .toSet()

internal fun collectBundledPluginsJars(intellijPlatformPath: Path) =
    intellijPlatformPath
        .resolve("plugins")
        .listDirectoryEntries()
        .asSequence()
        .flatMap { listOf(it.resolve(Sandbox.Plugin.LIB), it.resolve(Sandbox.Plugin.LIB_MODULES)) }
        .mapNotNull { it.takeIf { it.exists() } }
        .flatMap { it.listDirectoryEntries("*.jar") }
        .toSet()
