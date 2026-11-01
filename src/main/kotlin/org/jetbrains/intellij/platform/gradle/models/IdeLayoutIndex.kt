// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.intellij.platform.gradle.Constants.IDEA_CORE

/**
 * Serialized snapshot of bundled IDE plugins and modules.
 *
 * Lookups by [Entry.id] and [Entry.definedModules] intentionally preserve the first matching entry
 * for compatibility with the IDE model. The separate [Entry.key] keeps an exact identity for the
 * internal dependency graph, where duplicate IDs or aliases must not collapse into one entry.
 */
@Serializable
internal data class IdeLayoutIndex(
    val schemaVersion: Int = SCHEMA_VERSION,
    val fullVersion: String,
    val entries: List<Entry>,
) {

    @Transient
    // Dependency edges are stored by exact entry key because IDs and module aliases are not unique.
    private val entriesByKey: Map<String, Entry> = entries.associateBy(Entry::key)

    @Transient
    private val entriesById: Map<String, Entry> = buildMap {
        this@IdeLayoutIndex.entries.forEach { entry ->
            putIfAbsent(entry.id, entry)
        }
    }

    @Transient
    private val entriesByModuleId: Map<String, Entry> = buildMap {
        this@IdeLayoutIndex.entries.forEach { entry ->
            entry.definedModules.forEach { moduleId ->
                putIfAbsent(moduleId, entry)
            }
        }
    }

    @Transient
    // The owner's classpath contains its content modules,
    // which may be present without it, so they don't prove the owner is present.
    private val contentModulesClasspath: Map<String, Set<String>> = bundledModules
        .flatMap { module -> module.owners.map { it to module.classpath } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, classpaths) -> classpaths.flatten().toSet() }

    fun findById(id: String) = entriesById[id]

    fun findByIdOrModuleId(id: String) = findById(id) ?: findByModuleId(id)

    /**
     * Returns the exact serialized entry referenced by [Entry.dependencies].
     */
    fun findByKey(key: String) = entriesByKey[key]

    fun findByModuleId(moduleId: String) = entriesByModuleId[moduleId]

    val bundledPlugins
        get() = entries.asSequence().filterNot { it.isModule }

    val bundledModules
        get() = entries.asSequence().filter { it.isModule }

    /**
     * Returns the JARs of [entry] the IDE loads in production, relative to the IntelliJ Platform root:
     * - the JARs listed by the `product-info.json` [layout] for the entry, its content modules, and the core,
     * - for an entry missing from the [layout], the JARs located directly in its `lib` directory,
     *   and the JARs of its content modules located in `lib/modules`.
     *
     * Other JARs of the plugin directory are skipped, e.g., JARs carrying descriptors of JetBrains Client plugins,
     * which, when present on the classpath, are loaded as separate plugins conflicting with the bundled ones.
     * See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2250
     */
    fun runtimeClasspath(entry: Entry, layout: Layout) = entry.classpath.filter { path ->
        when {
            !path.startsWith("plugins/") -> true
            path in layout.classpath -> true
            entry.id in layout.names -> false
            else -> isLoadedFromPluginDirectory(entry, path)
        }
    }

    /**
     * Whether the IDE loads [path] for [entry] when the plugin is not described by the `product-info.json` layout:
     * `plugins/<plugin>/lib/<name>.jar` or `plugins/<plugin>/lib/modules/<module>.jar` of a declared content module.
     */
    private fun isLoadedFromPluginDirectory(entry: Entry, path: String): Boolean {
        val segments = path.split('/')
        return when {
            segments.size == 4 && segments[2] == "lib" -> true
            segments.size == 5 && segments[2] == "lib" && segments[3] == "modules" ->
                path in contentModulesClasspath[entry.key].orEmpty() ||
                    segments[4].removeSuffix(".jar").let { it == entry.id || it in entry.definedModules }

            else -> false
        }
    }

    /**
     * Returns the bundled plugins present on [classpath], and the plugins declaring the bundled modules present on it,
     * as the IDE loads a content module only with the plugin declaring it in `<content>`.
     * For a module declared by several plugins, none of them present, a single owner is returned, see [ownerOf].
     * The core is never returned.
     *
     * @param classpath Paths relative to the IntelliJ Platform root, like [Entry.classpath].
     */
    fun findPresentPlugins(classpath: Set<String>): List<Entry> {
        val presentPlugins = bundledPlugins.filter { isPresent(it, classpath) }.toList()
        val presentKeys = presentPlugins.map { it.key }.toSet()
        val presentModules = bundledModules.filter { isPresent(it, classpath) }.toList()
        val presentModulesOwners = presentModules
            .filterNot { module -> module.owners.any { it in presentKeys } }
            .mapNotNull { module -> ownerOf(module, module.owners.mapNotNull(::findByKey), presentModules) }
        return (presentPlugins + presentModulesOwners)
            .filterNot { it.id == IDEA_CORE }
            .distinctBy { it.key }
    }

    /**
     * Resolves [module] to the one of its [owners] declaring the [presentModules] requiring it,
     * as a private module may be declared by several plugins.
     * Falls back to the first owner if no such module is present.
     */
    private fun ownerOf(module: Entry, owners: List<Entry>, presentModules: List<Entry>): Entry? {
        val requiringModulesOwners = presentModules
            .filter { module.key in it.dependencies }
            .flatMap { it.owners }
            .toSet()
        return owners.firstOrNull { it.key in requiringModulesOwners } ?: owners.firstOrNull()
    }

    /**
     * Resolves the bundled plugins to add to the test classpath.
     *
     * All [bundledPluginIds] are included, except:
     * - the [excludes],
     * - the plugins with [clashes], unless declared explicitly, see [declaredPlugins],
     * - the plugins requiring an excluded plugin, transitively, see [dependencies].
     *
     * The [declaredPlugins] and [requiredPlugins], with all their dependencies, are never excluded.
     *
     * @param bundledPluginIds IDs of the bundled plugins, as listed in `product-info.json`, in the classpath order.
     * @param excludes IDs or aliases of the excluded plugins, mapped to the reason of their exclusion.
     * @param clashes Classes of the bundled plugins clashing with classes of the core ([IDEA_CORE])
     * or of the bundled plugins coming earlier in [bundledPluginIds], by plugin ID and by the ID of the clashing counterpart.
     * @param declaredPlugins Plugins declared explicitly as dependencies, already present on the classpath
     * before the IntelliJ Platform JARs.
     * @param requiredPlugins Plugins required by the plugin dependencies.
     */
    fun resolveTestPlugins(
        bundledPluginIds: List<String>,
        excludes: Map<String, String>,
        clashes: Map<String, Map<String, List<String>>>,
        declaredPlugins: Collection<Entry>,
        requiredPlugins: Collection<Entry>,
    ): TestPlugins {
        val plugins = bundledPluginIds
            .mapNotNull(::findByIdOrModuleId)
            .distinctBy { it.key }
        val declaredKeys = declaredPlugins.map { it.key }.toSet()
        val protectedKeys = dependencyClosure(declaredPlugins + requiredPlugins).map { it.key }.toSet()
        val excluded = linkedMapOf<String, TestPlugins.Exclusion>()
        val kept = mutableListOf<TestPlugins.Exclusion>()
        val clashing = mutableListOf<TestPlugins.Clash>()

        fun exclude(exclusion: TestPlugins.Exclusion) {
            when (exclusion.entry.key) {
                in protectedKeys -> kept += exclusion
                else -> excluded.putIfAbsent(exclusion.entry.key, exclusion)
            }
        }

        excludes.forEach { (id, reason) ->
            val entry = findByIdOrModuleId(id)?.takeUnless { it.isModule || it.id == IDEA_CORE } ?: return@forEach
            exclude(TestPlugins.Exclusion(entry, reason))
        }

        plugins.forEach { plugin ->
            clashes[plugin.id].orEmpty().forEach clash@{ (counterpartId, classes) ->
                val counterpart = findByIdOrModuleId(counterpartId)?.takeUnless { it.id == IDEA_CORE }
                if (classes.isEmpty() || plugin.key in excluded || counterpart?.key in excluded) {
                    return@clash
                }

                // Classes of the declared plugins win, as they come before the IntelliJ Platform and other bundled plugins.
                val shadowed = when {
                    counterpart != null && plugin.key in declaredKeys && counterpart.key !in declaredKeys -> counterpart
                    else -> plugin
                }
                val other = when {
                    shadowed.key != plugin.key -> "'${plugin.id}'"
                    counterpart != null -> "'${counterpart.id}'"
                    else -> "the IntelliJ Platform"
                }
                when (shadowed.key) {
                    in declaredKeys -> clashing += TestPlugins.Clash(shadowed, classes, declared = true)
                    in protectedKeys -> clashing += TestPlugins.Clash(shadowed, classes, declared = false)
                    else -> exclude(TestPlugins.Exclusion(shadowed, "${classes.size} obfuscated classes clash with $other", clashes = classes))
                }
            }
        }

        // Each excluded plugin may make other plugins miss their dependencies, so repeat until nothing more is excluded.
        do {
            val excludedCount = excluded.size
            plugins
                .filterNot { it.key in excluded || it.key in protectedKeys }
                .forEach { plugin ->
                    val dependency = plugin.dependencies
                        .mapNotNull(::findByKey)
                        .firstOrNull { isExcluded(it, excluded.keys) }
                        ?: return@forEach
                    val root = excluded[dependency.key]?.let { it.root ?: it.entry }
                        ?: dependency.owners.firstNotNullOfOrNull { excluded[it] }?.let { it.root ?: it.entry }
                    excluded[plugin.key] = TestPlugins.Exclusion(plugin, "requires excluded '${dependency.id}'", root = root)
                }
        } while (excluded.size > excludedCount)

        return TestPlugins(
            included = plugins.filterNot { it.key in excluded },
            excluded = excluded.values.toList(),
            kept = kept,
            clashes = clashing,
        )
    }

    /**
     * Whether [entry] is unavailable because of the [excludedKeys]: an excluded plugin, or a module with all its owners excluded.
     */
    private fun isExcluded(entry: Entry, excludedKeys: Set<String>) = when {
        entry.isModule -> entry.owners.isNotEmpty() && entry.owners.all { it in excludedKeys }
        else -> entry.key in excludedKeys
    }

    /**
     * Whether [entry] is present on [classpath].
     */
    private fun isPresent(entry: Entry, classpath: Set<String>) = when {
        // a module may consist of many JARs, and it is on the classpath only if its all JARs are on the classpath:
        entry.isModule -> entry.classpath.isNotEmpty() && classpath.containsAll(entry.classpath)
        else -> entry.id == IDEA_CORE ||
            // any non-module JAR means the whole plugin was added to the classpath:
            entry.classpath.any { it in classpath && it !in contentModulesClasspath[entry.key].orEmpty() }
    }

    /**
     * Returns [entries] with all their [Entry.dependencies], transitively.
     * As the IDE loads a content module only with the plugin declaring it, an owner of each module is included, too,
     * unless the module is declared by the entry requiring it or by an entry already included.
     */
    private fun dependencyClosure(entries: Collection<Entry>): Collection<Entry> {
        val visited = linkedMapOf<String, Entry>()
        val queue = ArrayDeque(entries.map { it to null as String? })

        while (queue.isNotEmpty()) {
            val (entry, requiredBy) = queue.removeFirst()
            if (visited.putIfAbsent(entry.key, entry) != null) {
                continue
            }

            queue += entry.dependencies.mapNotNull(::findByKey).map { it to entry.key }
            if (entry.isModule && entry.owners.none { it == requiredBy || it in visited }) {
                entry.owners.firstNotNullOfOrNull(::findByKey)?.let { queue += it to entry.key }
            }
        }
        return visited.values
    }

    /**
     * JARs and names of the components listed in the `product-info.json` layout.
     */
    internal class Layout(productInfo: ProductInfo) {
        val classpath = productInfo.layout.flatMap { it.classPath }.toSet()
        val names = productInfo.layout.map { it.name }.toSet()
    }

    /**
     * Bundled plugins resolved for the test classpath, see [resolveTestPlugins].
     *
     * @property included Plugins to add to the test classpath.
     * @property excluded Plugins excluded from the test classpath.
     * @property kept Plugins kept on the test classpath despite being excluded, as they are declared or required.
     * @property clashes Plugins kept on the test classpath despite their obfuscated classes clashing with other classes.
     */
    internal data class TestPlugins(
        val included: List<Entry>,
        val excluded: List<Exclusion>,
        val kept: List<Exclusion>,
        val clashes: List<Clash>,
    ) {

        /**
         * @property reason The reason of the exclusion.
         * @property root The excluded plugin causing this exclusion, if excluded because of missing dependencies.
         * @property clashes The obfuscated classes clashing with other classes, if excluded because of them.
         */
        data class Exclusion(
            val entry: Entry,
            val reason: String,
            val root: Entry? = null,
            val clashes: List<String> = emptyList(),
        )

        /**
         * @property declared Whether the plugin is declared explicitly, so it is already present on the classpath
         * before the IntelliJ Platform JARs.
         */
        data class Clash(
            val entry: Entry,
            val classes: List<String>,
            val declared: Boolean,
        )
    }

    @Serializable
    internal data class Entry(
        /**
         * Exact entry identity used by [dependencies]. IDs and aliases are not unique enough here.
         */
        val key: String,
        val id: String,
        val name: String? = null,
        val isModule: Boolean = false,
        val originalFile: String? = null,
        val classpath: List<String> = emptyList(),
        /**
         * References to other entries by [key], preserving precise targets when multiple entries
         * share an ID or alias.
         */
        val dependencies: List<String> = emptyList(),
        /**
         * All module aliases exported by this entry, including its primary [id].
         */
        val definedModules: List<String> = emptyList(),
        /**
         * For a bundled module, references by [key] to the plugins declaring it in their `<content>`.
         * A private module may be declared by several plugins, e.g., `intellij.platform.commercial.verifier`.
         */
        val owners: List<String> = emptyList(),
    )

    companion object {
        const val SCHEMA_VERSION = 5
    }
}

/**
 * Serialized obfuscated classes of the bundled plugins clashing with classes of the core or other bundled plugins.
 *
 * @property clashes The clashing class names by plugin ID and by the ID of the clashing counterpart:
 * the core, or a bundled plugin coming earlier in `product-info.json`.
 */
@Serializable
internal data class IdeClassClashes(
    val schemaVersion: Int = SCHEMA_VERSION,
    val fullVersion: String,
    val clashes: Map<String, Map<String, List<String>>>,
) {

    companion object {
        const val SCHEMA_VERSION = 2
    }
}
