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
     * Returns plugins and modules missing on [classpath] that the IDE needs to load the core and the bundled modules
     * present on it.
     *
     * - The IDE loads a content module only with the plugin declaring it in `<content>`,
     *   e.g., only the plugin JAR has `META-INF/plugin.xml`, so the plugins declaring the bundled modules present
     *   on [classpath] are returned.
     * - The IDE excludes a content module of the core when its dependencies are missing, and external plugins
     *   depend on many of them implicitly, e.g., `intellij.spellchecker` requiring `intellij.libraries.lucene.common`,
     *   so dependencies of the core and its content modules present on [classpath] are returned,
     *   transitively, if [includeCoreDependencies] is set.
     *
     * The returned entries are dependencies of the returned plugins, too.
     * A missing module is returned on its own when its owner is present, otherwise its owner is returned.
     * When several plugins declare the same module, the one declaring the modules requiring it is picked, see [ownerOf].
     *
     * @param classpath Paths relative to the IntelliJ Platform root, like [Entry.classpath].
     * @param includeCoreDependencies Whether to return dependencies of the core and its content modules.
     */
    fun findMissingEntries(classpath: Set<String>, includeCoreDependencies: Boolean = true): List<Entry> {
        val currentClasspath = classpath.toMutableSet()
        val missingEntries = linkedMapOf<String, Entry>()

        // Each found entry adds its classpath and dependencies, so repeat until nothing more is found.
        do {
            val missingEntriesCount = missingEntries.size
            val presentModules = bundledModules.filter { isPresent(it, currentClasspath) }.toList()

            requiredEntries(presentModules, missingEntries.values, includeCoreDependencies).forEach { entry ->
                val missingEntry = findMissingEntry(entry, currentClasspath, presentModules) ?: return@forEach
                if (missingEntries.putIfAbsent(missingEntry.key, missingEntry) == null) {
                    currentClasspath += missingEntry.classpath
                }
            }
        } while (missingEntries.size > missingEntriesCount)

        return missingEntries.values.toList()
    }

    /**
     * Returns the entries the IDE needs to load the [presentModules] and the [missingEntries] found so far:
     * the [presentModules] themselves, and the dependencies of the [missingEntries], transitively.
     * If [includeCoreDependencies] is set, the dependencies of the core and its [presentModules] are returned, too.
     */
    private fun requiredEntries(
        presentModules: List<Entry>,
        missingEntries: Collection<Entry>,
        includeCoreDependencies: Boolean,
    ): List<Entry> {
        val roots = when {
            includeCoreDependencies -> coreWithModules(presentModules) + missingEntries
            else -> missingEntries
        }
        return presentModules + dependencyClosure(roots)
    }

    /**
     * Returns the core with its content modules found in [presentModules].
     */
    private fun coreWithModules(presentModules: List<Entry>): List<Entry> {
        val core = bundledPlugins.firstOrNull { it.id == IDEA_CORE } ?: return emptyList()
        return listOf(core) + presentModules.filter { core.key in it.owners }
    }

    /**
     * Returns the entry to add to [classpath], so the IDE can load [entry], or `null` if [entry] can be loaded already.
     * For a module, its owner is returned when none of its owners is present, otherwise the module itself when missing.
     */
    private fun findMissingEntry(entry: Entry, classpath: Set<String>, presentModules: List<Entry>): Entry? {
        if (!entry.isModule) {
            return entry.takeUnless { isPresent(it, classpath) }
        }

        val owners = entry.owners.mapNotNull(::findByKey)
        return when {
            owners.none { isPresent(it, classpath) } -> ownerOf(entry, owners, presentModules)
            entry.classpath.isNotEmpty() && !isPresent(entry, classpath) -> entry
            else -> null
        }
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
     */
    private fun dependencyClosure(entries: Collection<Entry>): Collection<Entry> {
        val visited = linkedMapOf<String, Entry>()
        val queue = ArrayDeque(entries)

        while (queue.isNotEmpty()) {
            val entry = queue.removeFirst()
            if (visited.putIfAbsent(entry.key, entry) == null) {
                queue += entry.dependencies.mapNotNull(::findByKey)
            }
        }
        return visited.values
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
