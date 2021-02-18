package org.jetbrains.intellij.dependency

import groovy.xml.MarkupBuilder
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.Utils

class BuiltinPluginsRegistry {
    private final Map<String, Plugin> plugins = new HashMap<>()

    private final Map<String, String> directoryNameMapping = new HashMap<>()
    private final File pluginsDirectory;

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
                def dependencies = node.dependencies.first().dependency*.text() as Collection<String>
                plugins.put(node.@id, new Plugin(node.@id, node.@directoryName, dependencies))
                directoryNameMapping.put(node.@directoryName, node.@id)
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
                    plugin(id: p.key, directoryName: p.value.directoryName) {
                        dependencies {
                            for (def d in p.value.dependencies) {
                                dependency(d)
                            }
                        }
                    }
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
        def plugin = plugins[name] ?: plugins[directoryNameMapping[name]]
        if (plugin != null) {
            def result = new File(pluginsDirectory, plugin.directoryName)
            return result.exists() && result.isDirectory() ? result : null
        }
        return null
    }

    @Nullable
    Collection<String> collectBuiltinDependencies(@NotNull Collection<String> pluginIds) {
        List<String> idsToProcess = new ArrayList<>(pluginIds)
        Set<String> result = new HashSet<>()
        while (!idsToProcess.isEmpty()) {
            def id = idsToProcess.remove(0)
            def plugin = plugins[id] ?: plugins[directoryNameMapping[id]]
            if (plugin) {
                if (result.add(id)) {
                    idsToProcess.addAll(plugin.dependencies - result)
                }
            }
        }
        return result
    }

    def add(@NotNull File artifact, def loggingContext) {
        Utils.debug(loggingContext, "Adding directory to plugins index: $artifact)")
        def intellijPlugin = Utils.createPlugin(artifact, false, loggingContext)
        if (intellijPlugin != null) {
            def dependencies = intellijPlugin.dependencies
                    .findAll { !it.optional }
                    .collect { it.id }
            def plugin = new Plugin(intellijPlugin.pluginId, artifact.name, dependencies)
            plugins.put(intellijPlugin.pluginId, plugin)
            if (plugin.directoryName != plugin.id) {
                directoryNameMapping.put(plugin.directoryName, plugin.id)
            }
        }
    }

    private static class Plugin {
        final String id
        final String directoryName
        final Collection<String> dependencies;

        Plugin(String id, String directoryName, Collection<String> dependencies) {
            this.id = id
            this.directoryName = directoryName
            this.dependencies = dependencies
        }
    }
}
