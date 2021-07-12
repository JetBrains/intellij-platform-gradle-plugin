package org.jetbrains.intellij.dependency

import org.jetbrains.intellij.createPlugin
import org.jetbrains.intellij.debug
import org.jetbrains.intellij.model.XmlExtractor
import org.jetbrains.intellij.model.PluginsCache
import org.jetbrains.intellij.model.PluginsCachePlugin
import org.jetbrains.intellij.warn
import java.io.File
import java.io.Serializable

class BuiltinPluginsRegistry(private val pluginsDirectory: File, private val context: String?) : Serializable {
    private val plugins = mutableMapOf<String, PluginsCachePlugin>()
    private val directoryNameMapping = mutableMapOf<String, String>()

    @Transient
    private val extractor = XmlExtractor<PluginsCache>()

    companion object {
        const val version = 1

        fun fromDirectory(pluginsDirectory: File, context: String?) =
            BuiltinPluginsRegistry(pluginsDirectory, context).apply {
                if (!fillFromCache()) {
                    debug(context, "Builtin registry cache is missing")
                    fillFromDirectory()
                    dumpToCache()
                }
            }
    }

    private fun fillFromCache(): Boolean {
        val cache = cacheFile().takeIf { it.exists() } ?: return false

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
        pluginsDirectory.listFiles()?.apply {
            asSequence()
                .filter { it.isDirectory }
                .forEach { add(it) }
        }
        debug(context, "Builtin registry populated with ${plugins.size} plugins")
    }

    private fun dumpToCache() {
        debug(context, "Dumping cache for builtin plugin")
        try {
            extractor.marshal(PluginsCache(plugins.values.toList()), cacheFile())
        } catch (t: Throwable) {
            warn(context, "Failed to dump cache for builtin plugin", t)
        }
    }

    private fun cacheFile() = File(pluginsDirectory, "builtinRegistry-$version.xml")

    fun findPlugin(name: String): File? {
        val plugin = plugins[name] ?: plugins[directoryNameMapping[name]] ?: return null
        val result = File(pluginsDirectory, plugin.directoryName)

        return when {
            result.exists() && result.isDirectory -> result
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

    fun add(artifact: File) {
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
}
