// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import com.jetbrains.plugin.structure.ide.*
import com.jetbrains.plugin.structure.ide.layout.LayoutComponentNameSource
import com.jetbrains.plugin.structure.ide.layout.LayoutComponents
import com.jetbrains.plugin.structure.ide.layout.MissingLayoutFileMode.SKIP_SILENTLY
import com.jetbrains.plugin.structure.ide.resolver.ValidatingLayoutComponentsProvider
import com.jetbrains.plugin.structure.intellij.platform.ProductInfo
import com.jetbrains.plugin.structure.intellij.platform.ProductInfoParser
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.ModuleDescriptor
import com.jetbrains.plugin.structure.intellij.plugin.module.IdeModule
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import com.jetbrains.plugin.structure.jar.SingletonCachingJarFileSystemProvider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.Constants.IDEA_CORE
import org.jetbrains.intellij.platform.gradle.models.*
import org.jetbrains.intellij.platform.gradle.resolvers.path.ProductInfoPathResolver
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import org.jetbrains.intellij.platform.gradle.utils.writeTextIfChanged
import java.nio.file.Path
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile
import kotlin.io.path.*

/**
 * Builds and caches a compact, serializable view of an extracted IDE layout.
 *
 * This avoids repeated `createIde()` traversals for callers that only need bundled plugin/module
 * metadata, dependency edges, and resolved artifact/classpath locations.
 */
internal abstract class IdeLayoutIndexService : BuildService<BuildServiceParameters.None> {

    private val log = Logger(javaClass)
    private val indices = ConcurrentHashMap<String, IdeLayoutIndex>()
    private val classClashes = ConcurrentHashMap<String, Map<String, Map<String, List<String>>>>()
    private val loggedMessages = ConcurrentHashMap.newKeySet<String>()
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

    /**
     * Resolves the obfuscated classes of the bundled plugins in [platformPath] clashing with classes of the core
     * or other bundled plugins, by plugin ID and by the ID of the clashing counterpart, preferring the in-memory instance,
     * then the serialized cache on disk, stored next to the index.
     *
     * Classes are compared by name and CRC for the JARs loaded by the IDE, see [IdeLayoutIndex.runtimeClasspath].
     * Only classes with names that look obfuscated are considered, i.e., `Z/Z/Z/Z/L.class` with every package segment
     * and the simple name at most two characters long, as other duplicated classes, like copies of libraries,
     * are expected to be compatible.
     * The counterpart is the core ([IDEA_CORE]) or, for a clash between bundled plugins, the plugin coming earlier
     * in `product-info.json`, i.e., on the test classpath, shadowing the classes of the reported one.
     *
     * See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2261
     */
    internal fun resolveClassClashes(platformPath: Path, cacheDirectory: Path) =
        classClashes.computeIfAbsent(platformPath.safePathString) {
            val cacheFile = cacheFile(platformPath, cacheDirectory, suffix = "-class-clashes")
            val fullVersion = platformPath.productInfo().fullVersion
            loadClassClashes(cacheFile)
                ?: createClassClashes(platformPath, resolve(platformPath, cacheDirectory)).also { clashes ->
                    cacheFile.parent.createDirectories()
                    cacheFile.writeTextIfChanged(json.encodeToString(IdeClassClashes(fullVersion = fullVersion, clashes = clashes)))
                }
        }

    /**
     * Runs [block] with [message] only once per build, e.g., to log the same warning for many tasks just once.
     */
    internal fun once(message: String, block: (String) -> Unit) {
        if (loggedMessages.add(message)) {
            block(message)
        }
    }

    private fun loadClassClashes(cacheFile: Path): Map<String, Map<String, List<String>>>? {
        if (!cacheFile.exists()) {
            return null
        }

        return runCatching {
            json.decodeFromString<IdeClassClashes>(cacheFile.readText())
                .takeIf { it.schemaVersion == IdeClassClashes.SCHEMA_VERSION }
                ?.clashes
        }.onFailure {
            log.warn("Failed to load IDE class clashes from '$cacheFile', they will be computed again.")
        }.getOrNull()
    }

    private fun createClassClashes(platformPath: Path, index: IdeLayoutIndex): Map<String, Map<String, List<String>>> {
        log.info("Looking for clashing classes of bundled plugins in: $platformPath")

        val productInfo = platformPath.productInfo()
        val layout = IdeLayoutIndex.Layout(productInfo)
        val libraryJars = runCatching { platformPath.resolve("lib").listDirectoryEntries("*.jar") }.getOrDefault(emptyList())
        val coreJars = layout.classpath.filterNot { it.startsWith("plugins/") }.map(platformPath::resolve) + libraryJars
        val coreClasses = hashMapOf<String, Long>()
        coreJars.distinct().forEach { jar ->
            jar.forEachObfuscatedClass { name, crc -> coreClasses.putIfAbsent(name, crc) }
        }

        val pluginClasses = hashMapOf<String, Pair<String, Long>>()
        val clashes = linkedMapOf<String, MutableMap<String, MutableSet<String>>>()
        fun clash(pluginId: String, counterpartId: String, name: String) {
            clashes.getOrPut(pluginId) { linkedMapOf() }.getOrPut(counterpartId) { sortedSetOf() } +=
                name.removeSuffix(".class").replace('/', '.')
        }

        // The bundled plugins are added to the test classpath in the order of product-info.json.
        productInfo.bundledPlugins
            .mapNotNull(index::findByIdOrModuleId)
            .filterNot { it.isModule || it.id == IDEA_CORE }
            .distinctBy { it.key }
            .forEach { plugin ->
                index.runtimeClasspath(plugin, layout)
                    .filter { it.startsWith("plugins/") }
                    .forEach { path ->
                        platformPath.resolve(path).forEachObfuscatedClass { name, crc ->
                            val coreCrc = coreClasses[name]
                            val (owner, ownerCrc) = pluginClasses.getOrPut(name) { plugin.id to crc }
                            when {
                                coreCrc != null && coreCrc != crc -> clash(plugin.id, IDEA_CORE, name)
                                owner != plugin.id && ownerCrc != crc -> clash(plugin.id, owner, name)
                            }
                        }
                    }
            }

        return clashes.mapValues { (_, counterparts) -> counterparts.mapValues { (_, classes) -> classes.toList() } }
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
    ): IdeLayoutIndex {
        val layoutComponents = ValidatingLayoutComponentsProvider(SKIP_SILENTLY)
            .resolveLayoutComponents(productInfo, platformPath)
        val jarFileSystemProvider = SingletonCachingJarFileSystemProvider
        return ProductInfoBasedIde.of(
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
        val contentModuleOwnerKeys = getContentModuleOwnerKeys(bundledPlugins, entryKeys)

        return IdeLayoutIndex(
            fullVersion = productInfo.fullVersion,
            entries = bundledPlugins.map { (plugin, id) ->
                plugin.toEntry(
                    id = id,
                    ide = this,
                    platformPath = platformPath,
                    dependencyKeys = entryKeys,
                    owners = when (plugin) {
                        is IdeModule -> contentModuleOwnerKeys[id].orEmpty()
                        else -> emptyList()
                    },
                )
            },
        )
    }

    /**
     * @return Map of each content module name to the entry keys of plugins whose `<content>` declares it.
     */
    private fun getContentModuleOwnerKeys(
        bundledPlugins: List<Pair<IdePlugin, String>>,
        entryKeys: IdentityHashMap<IdePlugin, String>,
    ): Map<String, List<String>> {
        return bundledPlugins
            .map { (plugin, _) -> plugin }
            .filterNot { it is IdeModule }
            .flatMap { plugin -> plugin.contentModules.map { it.name to requireNotNull(entryKeys[plugin]) } }
            .groupBy({ it.first }, { it.second })
    }

    private fun write(cacheFile: Path, index: IdeLayoutIndex) {
        cacheFile.parent.createDirectories()
        cacheFile.writeTextIfChanged(json.encodeToString(index))
    }

    private fun cacheFile(platformPath: Path, cacheDirectory: Path, suffix: String = ""): Path {
        val productInfo = platformPath.productInfo()
        val fingerprint = fingerprint(platformPath)

        return cacheDirectory.resolve("${productInfo.fullVersion}-${fingerprint.take(12)}$suffix.json")
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

/**
 * Calls [action] with the name and CRC of each class in this JAR with a name that looks obfuscated, see [isObfuscatedClass].
 * Missing or unreadable JARs are skipped.
 */
private fun Path.forEachObfuscatedClass(action: (String, Long) -> Unit) {
    if (!isRegularFile()) {
        return
    }

    runCatching {
        ZipFile(toFile()).use { zipFile ->
            zipFile.entries().asSequence()
                .filter { isObfuscatedClass(it.name) }
                .forEach { action(it.name, it.crc) }
        }
    }
}

/**
 * Whether the class file [name] looks obfuscated: it is located in a package, and all package segments
 * and the simple name are at most two characters long, e.g., `Z/Z/Z/Z/L.class`.
 */
private fun isObfuscatedClass(name: String): Boolean {
    if (!name.endsWith(".class")) {
        return false
    }

    val segments = name.removeSuffix(".class").split('/')
    return segments.size >= 2 && segments.all { it.length in 1..2 }
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

/**
 * Whether this content module's dependencies are required: either `loading="required"`, or any `required-if-available`.
 * The `required-if-available` target is treated as available, as `product-info.json` doesn't expose the modules
 * available in each product mode yet.
 */
private val ModuleDescriptor.isTreatedAsRequired: Boolean
    get() = with(moduleDefinition) {
        loadingRule.required || requiredIfAvailable != null
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
    owners: List<String>,
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
    dependencies = (sequenceOf<IdePlugin>(this) + modulesDescriptors.asSequence()
        .filter { it.isTreatedAsRequired }
        .map { it.module })
        .flatMap { module ->
            module.dependsList.asSequence().filter { !it.isOptional }.map { it.pluginId } +
                module.pluginMainModuleDependencies.asSequence().map { it.pluginId } +
                module.contentModuleDependencies.asSequence().map { it.moduleName }
        }
        .filter(String::isNotBlank)
        .distinct()
        .mapNotNull { ide.findPluginByIdOrModuleId(it)?.plugin }
        .filterNot { it.pluginId == IDEA_CORE || it == this } // skip dependency on the core and self-dependency
        .toSet()
        .mapNotNull { dependencyKeys[it] },
    definedModules = pluginAliases
        .asSequence()
        .plus(contentModules.asSequence().map { it.name })
        .plus(id)
        .filter(String::isNotBlank)
        .distinct()
        .toList(),
    owners = owners,
)
