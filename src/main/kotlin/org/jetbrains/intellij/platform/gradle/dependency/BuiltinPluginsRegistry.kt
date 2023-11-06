// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.dependency

import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.listFiles
import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.createPlugin
import org.jetbrains.intellij.platform.gradle.debug
import org.jetbrains.intellij.platform.gradle.model.PluginsCache
import org.jetbrains.intellij.platform.gradle.model.PluginsCachePlugin
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.warn
import java.io.File
import java.io.Serializable
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.name

class BuiltinPluginsRegistry(private val pluginsDirectory: File, private val context: String?) : Serializable {

    private val plugins = mutableMapOf<String, PluginsCachePlugin>()
    private val directoryNameMapping = mutableMapOf<String, String>()

    private fun fillFromCache(extractor: XmlExtractor<PluginsCache>): Boolean {
        val cache = cacheFile().takeIf(Path::exists) ?: return false

        debug(context, "Builtin registry cache is found. Loading from: $cache")
        return try {
            extractor.unmarshal(cache).plugins.forEach {
                plugins[it.id] = it
                directoryNameMapping[it.directoryName] = it.id
            }
            true
        } catch (t: Throwable) {
            warn(context, "Cannot read builtin registry cache", t)
            false
        }
    }

    private fun fillFromDirectory() {
        pluginsDirectory
            .toPath()
            .listFiles()
            .filter { it.isDirectory() }
            .forEach(::add)
        debug(context, "Builtin registry populated with ${plugins.size} plugins")
    }

    private fun dumpToCache(extractor: XmlExtractor<PluginsCache>) {
        debug(context, "Dumping cache for builtin plugin")
        try {
            extractor.marshal(PluginsCache(plugins.values.toList()), cacheFile())
        } catch (t: Throwable) {
            warn(context, "Failed to dump cache for builtin plugin", t)
        }
    }

    private fun cacheFile() = pluginsDirectory.resolve("builtinRegistry-$version.xml").toPath()

    fun findPlugin(name: String): Path? {
        val plugin = plugins[name] ?: plugins[directoryNameMapping[name]] ?: return null
        val result = pluginsDirectory.resolve(plugin.directoryName)

        return when {
            result.exists() && result.isDirectory -> result.toPath()
            else -> null
        }
    }

    fun collectBuiltinDependencies(pluginIds: Collection<String>): Collection<String> {
        val idsToProcess = pluginIds.toMutableList()
        val result = mutableSetOf<String>()

        while (idsToProcess.isNotEmpty()) {
            val id = idsToProcess.removeAt(0)
            val plugin = plugins[id] ?: plugins[directoryNameMapping[id]] ?: continue
            if (result.add(id)) {
                idsToProcess.addAll(plugin.dependencies - result)
            }
        }

        return result
    }

    fun add(artifact: Path) {
        debug(context, "Adding directory to plugins index: $artifact)")
        val intellijPlugin = createPlugin(artifact, false, context) ?: return
        val id = intellijPlugin.pluginId ?: return
        val dependencies = intellijPlugin.dependencies.filter { !it.isOptional }.map { it.id }
        val plugin = PluginsCachePlugin(id, artifact.name, dependencies)

        plugins[id] = plugin
        if (plugin.directoryName != id) {
            directoryNameMapping[plugin.directoryName] = id
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as BuiltinPluginsRegistry

        if (pluginsDirectory != other.pluginsDirectory) return false
        if (plugins != other.plugins) return false
        if (directoryNameMapping != other.directoryNameMapping) return false

        return true
    }

    override fun hashCode(): Int {
        var result = pluginsDirectory.hashCode()
        result = 31 * result + plugins.hashCode()
        result = 31 * result + directoryNameMapping.hashCode()
        return result
    }

    companion object {
        const val version = 1

        fun fromDirectory(pluginsDirectory: Path, context: String?) = BuiltinPluginsRegistry(pluginsDirectory.toFile(), context).apply {
            val extractor = XmlExtractor<PluginsCache>(context)
            if (!fillFromCache(extractor)) {
                debug(context, "Builtin registry cache is missing")
                fillFromDirectory()
                dumpToCache(extractor)
            }
        }

        fun resolveBundledPlugins(ideDir: Path, context: String?) = ideDir
            .productInfo()
            .bundledPlugins
            .takeIf { it.isNotEmpty() }
            ?: fromDirectory(ideDir.resolve("plugins"), context)
                .plugins
                .keys
                .takeIf { it.isNotEmpty() }
            ?: throw GradleException("Unable to resolve bundled plugins")
    }
}
