// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

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

    /**
     * Returns the exact serialized entry referenced by [Entry.dependencies].
     */
    fun findByKey(key: String) = entriesByKey[key]

    fun findByModuleId(moduleId: String) = entriesByModuleId[moduleId]

    val bundledPlugins
        get() = entries.asSequence().filterNot { it.isModule }

    val bundledModules
        get() = entries.asSequence().filter { it.isModule }

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
    )

    companion object {
        const val SCHEMA_VERSION = 3
    }
}
