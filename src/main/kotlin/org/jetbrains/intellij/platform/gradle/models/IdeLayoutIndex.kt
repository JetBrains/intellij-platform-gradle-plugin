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
     * Returns plugins missing on [classpath] that declare the bundled modules present on it.
     * The IDE loads a content module only with the plugin declaring it in `<content>`, e.g., only the plugin JAR has `META-INF/plugin.xml`.
     *
     * Modules owned by the core or by a plugin already present on [classpath] are skipped.
     * When several plugins declare the same module, the one declaring the modules requiring it is picked, see [ownerOf].
     *
     * @param classpath Paths relative to the IntelliJ Platform root, like [Entry.classpath].
     */
    fun findMissingContentModuleOwners(classpath: Set<String>): List<Entry> {
        // The owner's classpath contains its content modules,
        // which may be present without it, so they don't prove the owner is present.
        val contentModulesClasspath = bundledModules
            .flatMap { module -> module.owners.map { it to module.classpath } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, classpaths) -> classpaths.flatten().toSet() }
        val isPresent = { owner: Entry ->
            owner.id == IDEA_CORE ||
                // any non-module JAR means the whole plugin was added to the classpath:
                owner.classpath.any { it in classpath && it !in contentModulesClasspath[owner.key].orEmpty() }
        }

        val presentModules = bundledModules
            // a module may consist of many JARs, and it is on the classpath only if its all JARs are on the classpath:
            .filter { it.classpath.isNotEmpty() && classpath.containsAll(it.classpath) }
            .toList()

        return presentModules
            .mapNotNull { module ->
                val owners = module.owners.mapNotNull(::findByKey)
                owners.takeUnless { it.any(isPresent) }?.let { ownerOf(module, it, presentModules) }
            }
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
