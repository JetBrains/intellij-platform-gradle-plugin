// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
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
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.artifacts.LocalIvyArtifactPathComponentMetadataRule
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.resolvers.path.ModuleDescriptorsPathResolver
import org.jetbrains.intellij.platform.gradle.resolvers.path.takeIfExists
import org.jetbrains.intellij.platform.gradle.utils.*
import java.nio.file.Path
import java.util.jar.JarFile
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
                    requireNotNull(productInfo)

                    when (productInfo.type) {
                        IntelliJPlatformType.JetBrainsClient -> collectModuleDescriptorJars(productInfo, path)
                        else -> collectIntelliJPlatformJars(productInfo, path)
                    }.forEach {
                        outputs.file(it)
                    }
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
            when (productInfo.type) {
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
        // exclude tests-related jars from the list as JUnit and Test Framework shouldn't be in the classpath
        .filterNot { it in listOf("lib/junit4.jar", "lib/junit.jar", "lib/testFramework.jar") }
        .map { intellijPlatformPath.resolve(it) }
        .mapNotNull { it.takeIf { it.exists() } }
        .toSet()

internal fun collectBundledPluginsJars(platformPath: Path) =
    platformPath
        .resolve("plugins")
        .listDirectoryEntries()
        .asSequence()
        .flatMap { listOf(it.resolve(Sandbox.Plugin.LIB), it.resolve(Sandbox.Plugin.LIB_MODULES)) }
        .mapNotNull { it.takeIf { it.exists() } }
        .flatMap { it.listDirectoryEntries("*.jar") }
        .toSet()

internal fun collectModuleDescriptorJars(
    productInfo: ProductInfo,
    platformPath: Path,
    architecture: String? = null,
): List<Path> = runCatching {
    val moduleDescriptorsFile = ModuleDescriptorsPathResolver(platformPath).resolve()
    val jarFile = JarFile(moduleDescriptorsFile.toFile())

    val rootModuleName = productInfo.run {
        when (architecture) {
            null -> launch.first()
            else -> launchFor(architecture)
        }
    }.additionalJvmArguments.let { args ->
        val prefix = "-Dintellij.platform.root.module="
        args
            .find { it.startsWith(prefix) }
            ?.removePrefix(prefix)
            .let { requireNotNull(it) }
    }

    val modules = jarFile
        .entries()
        .asSequence()
        .filter { it.name.endsWith(".xml") }
        .map { jarFile.getInputStream(it) }
        .mapNotNull { decode<ModuleDescriptor>(it) }
        .associateBy { it.name }

    val visitedModules = mutableSetOf<String>()

    fun collectResourcesFromModule(moduleName: String): Collection<Path> {
        if (moduleName in visitedModules) {
            return emptyList()
        }

        visitedModules += moduleName
        val module = modules[moduleName] ?: return emptyList()
        val moduleResources = module.path?.let { platformPath.resolve(it).takeIfExists() }
        val dependencyResources = module.dependencies
            .map { it.name }
            .flatMap { collectResourcesFromModule(it) }

        return (listOfNotNull(moduleResources) + dependencyResources).toSet()
    }

    val rootModule = requireNotNull(modules[rootModuleName]?.path)
    val rootModuleJar = JarFile(platformPath.resolve(rootModule).toFile())

    val productModules = rootModuleJar.getEntry("META-INF/$rootModuleName/product-modules.xml")
        ?.let { rootModuleJar.getInputStream(it) }
        ?.let { decode<ProductModules>(it) }

    val productModuleJars = productModules?.include?.fromModules.orEmpty()
        .flatMap { collectResourcesFromModule(it.value) }

    collectResourcesFromModule(rootModuleName) + productModuleJars
}.getOrDefault(emptyList())
