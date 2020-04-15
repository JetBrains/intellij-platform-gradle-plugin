package org.jetbrains.intellij.dependency

import groovy.transform.ToString
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
        //todo: save to file/load from file
        def result = new BuiltinPluginsRegistry(pluginsDirectory)
        def files = pluginsDirectory.listFiles()
        if (files != null) {
            for (file in files) {
                if (file.isDirectory()) {
                    result.add(file, loggingContext)
                }
            }
        }
        return result
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
        Utils.debug(loggingContext, "Adding  directory to plugins index: $artifact)")
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
