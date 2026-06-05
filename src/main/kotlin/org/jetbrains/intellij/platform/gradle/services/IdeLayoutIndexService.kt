// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import com.jetbrains.plugin.structure.ide.Ide
import com.jetbrains.plugin.structure.ide.ProductInfoBasedIde
import com.jetbrains.plugin.structure.ide.ProductInfoBasedIdeManager
import com.jetbrains.plugin.structure.ide.ProductInfoLayoutBasedPluginCollectionProvider
import com.jetbrains.plugin.structure.ide.ProductInfoLayoutComponentsPluginCollectionSource
import com.jetbrains.plugin.structure.ide.ProductInfoPluginCollectionSource
import com.jetbrains.plugin.structure.ide.ProductInfoPluginReaderPluginCollectionProvider
import com.jetbrains.plugin.structure.ide.UndeclaredInLayoutPluginReader
import com.jetbrains.plugin.structure.ide.createIde
import com.jetbrains.plugin.structure.ide.layout.LayoutComponentNameSource
import com.jetbrains.plugin.structure.ide.layout.LayoutComponents
import com.jetbrains.plugin.structure.ide.layout.MissingLayoutFileMode.SKIP_SILENTLY
import com.jetbrains.plugin.structure.ide.resolver.ValidatingLayoutComponentsProvider
import com.jetbrains.plugin.structure.intellij.platform.ProductInfo
import com.jetbrains.plugin.structure.intellij.platform.ProductInfoParser
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.module.IdeModule
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.plugin.structure.jar.CachingJarFileSystemProvider
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.Constants.IDEA_CORE
import org.jetbrains.intellij.platform.gradle.models.IdeLayoutIndex
import org.jetbrains.intellij.platform.gradle.models.fullVersion
import org.jetbrains.intellij.platform.gradle.models.json
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.resolvers.path.ProductInfoPathResolver
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import org.jetbrains.intellij.platform.gradle.utils.writeTextIfChanged
import java.nio.file.Path
import java.security.MessageDigest
import java.util.IdentityHashMap
import java.util.HexFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText

/**
 * Builds and caches a compact, serializable view of an extracted IDE layout.
 *
 * This avoids repeated `createIde()` traversals for callers that only need bundled plugin/module
 * metadata, dependency edges, and resolved artifact/classpath locations.
 */
internal abstract class IdeLayoutIndexService : BuildService<BuildServiceParameters.None> {

    private val log = Logger(javaClass)
    private val indices = ConcurrentHashMap<String, IdeLayoutIndex>()
    private val hexFormat = HexFormat.of()
    private val productInfoBasedIdeManager = ProductInfoBasedIdeManager(SKIP_SILENTLY)

    /**
     * Resolves an index for [platformPath], preferring the in-memory instance, then the serialized
     * cache on disk, and rebuilding from the extracted IDE only when needed.
     */
    internal fun resolve(platformPath: Path, cacheDirectory: Path) =
        indices.computeIfAbsent(platformPath.safePathString) {
            val cacheFile = cacheFile(platformPath, cacheDirectory)
            load(cacheFile)
                ?: create(platformPath).also { write(cacheFile, it) }
        }

    private fun load(cacheFile: Path): IdeLayoutIndex? {
        if (!cacheFile.exists()) {
            return null
        }

        return runCatching {
            json.decodeFromString<IdeLayoutIndex>(cacheFile.readText())
                .takeIf { it.schemaVersion == IdeLayoutIndex.SCHEMA_VERSION }
        }.onFailure {
            log.warn("Failed to load IDE layout index from '$cacheFile', it will be rebuilt.")
        }.getOrNull()
    }

    private fun create(platformPath: Path): IdeLayoutIndex {
        log.info("Creating IDE layout index from: $platformPath")

        if (!productInfoBasedIdeManager.supports(platformPath)) {
            return createIde {
                missingLayoutFileMode = SKIP_SILENTLY
                path = platformPath
            }.toLayoutIndex(platformPath)
        }

        val productInfo = ProductInfoParser().parse(ProductInfoPathResolver(platformPath).resolve())
        return createProductInfoBasedIndex(platformPath, productInfo, productInfo.toIdeVersion())
    }

    private fun createProductInfoBasedIndex(
        platformPath: Path,
        productInfo: ProductInfo,
        ideVersion: IdeVersion,
    ) = CachingJarFileSystemProvider().use { jarFileSystemProvider ->
        val layoutComponents = ValidatingLayoutComponentsProvider(SKIP_SILENTLY)
            .resolveLayoutComponents(productInfo, platformPath)
        ProductInfoBasedIde.of(
            platformPath,
            ideVersion,
            productInfo,
            mapOf(
                ProductInfoLayoutComponentsPluginCollectionSource(platformPath, ideVersion, layoutComponents) to
                    ProductInfoLayoutBasedPluginCollectionProvider(NoOpLayoutComponentsPluginReader, jarFileSystemProvider),
                ProductInfoPluginCollectionSource(platformPath, ideVersion, productInfo) to
                    ProductInfoPluginReaderPluginCollectionProvider(UndeclaredInLayoutPluginReader(setOf("AI", "CL"))),
            ),
        )
            .toLayoutIndex(platformPath)
    }

    private fun Ide.toLayoutIndex(platformPath: Path): IdeLayoutIndex {
        val productInfo = platformPath.productInfo()
        val bundledPlugins = bundledPlugins.mapNotNull { plugin ->
            val id = plugin.pluginId ?: plugin.pluginName ?: return@mapNotNull null
            plugin to id
        }
        // Preserve exact plugin identity while building dependency edges: IDs and aliases may collide.
        val entryKeys = bundledPlugins
            .mapIndexed { index, (plugin, _) -> plugin to "entry-$index" }
            .toMap(IdentityHashMap())

        return IdeLayoutIndex(
            fullVersion = productInfo.fullVersion,
            entries = bundledPlugins.map { (plugin, id) ->
                plugin.toEntry(
                    id = id,
                    ide = this,
                    platformPath = platformPath,
                    dependencyKeys = entryKeys,
                )
            },
        )
    }

    private fun write(cacheFile: Path, index: IdeLayoutIndex) {
        cacheFile.parent.createDirectories()
        cacheFile.writeTextIfChanged(json.encodeToString(index))
    }

    private fun cacheFile(platformPath: Path, cacheDirectory: Path): Path {
        val productInfo = platformPath.productInfo()
        val fingerprint = fingerprint(platformPath)

        return cacheDirectory.resolve("${productInfo.fullVersion}-${fingerprint.take(12)}.json")
    }

    /**
     * Hashes the parts of the extracted IDE layout that affect bundled plugin/module resolution.
     *
     * The hash becomes part of the cache filename so layout changes invalidate stale serialized
     * indices automatically.
     */
    private fun fingerprint(platformPath: Path): String {
        val productInfo = platformPath.productInfo()
        val pluginDirectories = runCatching {
            platformPath
                .resolve("plugins")
                .listDirectoryEntries()
                .map { it.fileName.toString() }
                .sorted()
        }.getOrDefault(emptyList())
        val payload = buildString {
            append(IdeLayoutIndex.SCHEMA_VERSION)
            append('\n')
            append(OperatingSystem.current().name)
            append('\n')
            append(productInfo.fullVersion)
            append('\n')
            productInfo.layout
                .sortedBy { it.name }
                .forEach {
                    append(it.name)
                    append(':')
                    append(it.kind)
                    append(':')
                    append(it.classPath.joinToString(","))
                    append('\n')
                }
            pluginDirectories.forEach {
                append(it)
                append('\n')
            }
        }
        return hexFormat.formatHex(MessageDigest.getInstance("SHA-256").digest(payload.toByteArray()))
    }
}

private object NoOpLayoutComponentsPluginReader : ProductInfoBasedIdeManager.PluginReader<LayoutComponents> {
    override fun readPlugins(
        idePath: Path,
        pluginMetadataSource: LayoutComponents,
        layoutComponentNameSource: LayoutComponentNameSource<LayoutComponents>,
        ideVersion: IdeVersion,
    ) = emptyList<IdePlugin>()

    override fun supports(pluginMetadataSource: Any) = true
}

private fun ProductInfo.toIdeVersion() = IdeVersion.createIdeVersion(
    buildString {
        if (productCode.isNotEmpty()) {
            append(productCode).append("-")
        }
        append(buildNumber)
    },
)

/**
 * Converts a live plugin-structure model entry into the serialized layout-index representation.
 */
private fun IdePlugin.toEntry(
    id: String,
    ide: Ide,
    platformPath: Path,
    dependencyKeys: IdentityHashMap<IdePlugin, String>,
) = IdeLayoutIndex.Entry(
    key = requireNotNull(dependencyKeys[this]),
    id = id,
    name = pluginName,
    isModule = this is IdeModule,
    originalFile = originalFile?.let { platformPath.relativize(it).invariantSeparatorsPathString },
    classpath = classpath.paths
        .map { platformPath.relativize(it).invariantSeparatorsPathString }
        .distinct(),
    // Dependencies are serialized by exact entry key to avoid collapsing duplicate IDs or aliases.
    dependencies = (modulesDescriptors.asSequence()
        .filter { it.moduleDefinition.loadingRule.required }
        .map { it.name } +
            dependsList.asSequence()
                .filter { !it.isOptional }
                .map { it.pluginId } +
            pluginMainModuleDependencies.asSequence().map { it.pluginId } +
            contentModuleDependencies.asSequence().map { it.moduleName })
        .filter(String::isNotBlank)
        .distinct()
        .mapNotNull { ide.findPluginById(it) ?: ide.findPluginByModule(it) }
        .filterNot { it.pluginId == IDEA_CORE }
        .toSet()
        .mapNotNull { dependencyKeys[it] },
    definedModules = pluginAliases
        .asSequence()
        .plus(contentModules.asSequence().map { it.name })
        .plus(id)
        .filter(String::isNotBlank)
        .distinct()
        .toList()
)
