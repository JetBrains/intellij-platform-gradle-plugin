package org.jetbrains.intellij.dependency

import groovy.transform.ToString
import groovy.xml.MarkupBuilder
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.Utils

@ToString(includeNames = true, includeFields = true, ignoreNulls = true)
class BuiltinPluginsRegistry implements Serializable {
    private final Map<String, Plugin> plugins = new HashMap<>();

    transient private final Map<String, String> directoryNameMapping = new HashMap<>();
    transient private final File pluginsDirectory;

    BuiltinPluginsRegistry(def pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory
    }

    static BuiltinPluginsRegistry fromDirectory(File pluginsDirectory, def loggingContext) {
        def result = new BuiltinPluginsRegistry(pluginsDirectory)
        if (!result.fillFromCache(loggingContext)) {
            Utils.debug(loggingContext, "Builtin registry cache is missing")
            result.fillFromDirectory(loggingContext)
            result.dumpToCache(loggingContext)
        }
        return result
    }

    private boolean fillFromCache(def loggingContext) {
        def cache = cacheFile()
        if (cache == null) return false

        Utils.debug(loggingContext, "Builtin registry cache is found. Loading from $cache")
        try {
            Utils.parseXml(cache).children().forEach { node ->
                plugins.put(node.id, new Plugin(node.id, node.directoryName, node.implementationDetail))
            }
            return true
        }
        catch (Throwable t) {
            Utils.warn(loggingContext, "Cannot read builtin registry cache", t)
            return false
        }
    }

    private void fillFromDirectory(loggingContext) {
        def files = pluginsDirectory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory()) {
                    add(file, loggingContext)
                }
            }
        }
        Utils.debug(loggingContext, "Builtin registry populated with ${plugins.size()} plugins")
    }

    private void dumpToCache(def loggingContext) {
        Utils.debug(loggingContext, "Dumping cache for builtin plugin")
        def cacheFile = cacheFile()
        def writer = null
        try {
            writer = new FileWriter(cacheFile)
            new MarkupBuilder(writer).plugins {
                for (p in plugins) {
                    plugin (
                        id: p.key,
                        directoryName: p.value.directoryName,
                        implementationDetail: p.value.implementationDetail,
                    )
                }
            }
        } catch (Throwable t) {
            Utils.warn(loggingContext, "Failed to dump cache for builtin plugin", t)
        } finally {
            if (writer != null) {
                writer.close()
            }
        }
    }

    private File cacheFile() {
        new File(pluginsDirectory, "builtinRegistry.xml")
    }

    @Nullable
    File findPlugin(def name) {
        def plugin = plugins.get(name) ?: plugins.get(directoryNameMapping.get(name))
        if (plugin != null) {
            def result = new File(pluginsDirectory, plugin.directoryName)
            return result.exists() && result.isDirectory() ? result : null
        }
        return null
    }

    @Nullable
    Set<String> getImplementationDetailPluginIds() {
        return plugins.values().findAll { it.implementationDetail }.collect { it.id }
    }

    def add(@NotNull File artifact, def loggingContext) {
        Utils.debug(loggingContext, "Adding directory to plugins index: $artifact)")
        def intellijPlugin = Utils.createPlugin(artifact, false, loggingContext)
        if (intellijPlugin != null) {
            def plugin = new Plugin(intellijPlugin.pluginId, artifact.name, intellijPlugin.implementationDetail)
            plugins.put(intellijPlugin.pluginId, plugin)
            if (plugin.directoryName != plugin.id) {
                directoryNameMapping.put(plugin.directoryName, plugin.id)
            }
        }
    }

    private static class Plugin {
        final String id
        final String directoryName
        final boolean implementationDetail;

        Plugin(String id, String directoryName, boolean implementationDetail) {
            this.id = id
            this.directoryName = directoryName
            this.implementationDetail = implementationDetail
        }
    }
}
