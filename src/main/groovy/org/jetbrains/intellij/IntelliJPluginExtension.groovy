package org.jetbrains.intellij

import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.PluginDependency

/**
 * Configuration options for the {@link org.jetbrains.intellij.IntelliJPlugin}.
 */
class IntelliJPluginExtension {
    Object[] plugins = []
    String localPath
    String localSourcesPath
    String version
    String type = 'IC'
    String pluginName
    String sandboxDirectory
    String intellijRepo = IntelliJPlugin.DEFAULT_INTELLIJ_REPO
    String pluginsRepo = IntelliJPlugin.DEFAULT_INTELLIJ_PLUGINS_REPO
    String jreRepo = IntelliJPlugin.DEFAULT_JBR_REPO
    String alternativeIdePath
    String ideaDependencyCachePath
    boolean instrumentCode = true
    boolean updateSinceUntilBuild = true
    boolean sameSinceUntilBuild = false
    boolean downloadSources = true
    // turning it off disables configuring dependencies to intellij sdk jars automatically,
    // instead the intellij, intellijPlugin and intellijPlugins functions could be used for an explicit configuration
    boolean configureDefaultDependencies = true
    // configure extra dependency artifacts from intellij repo
    // the dependencies on them could be configured only explicitly using intellijExtra function in the dependencies block
    Object[] extraDependencies = []

    IdeaDependency ideaDependency
    private final Set<PluginDependency> pluginDependencies = new HashSet<>()

    String getType() {
        if (version == null) {
            return 'IC'
        }
        if (version.startsWith('IU-') || 'IU' == type) {
            return 'IU'
        } else if (version.startsWith('JPS-') || 'JPS' == type) {
            return "JPS"
        } else if (version.startsWith('CL-') || 'CL' == type) {
            return 'CL'
        } else if (version.startsWith('RD-') || 'RD' == type) {
            return 'RD'
        } else if (version.startsWith('MPS-') || 'MPS' == type) {
            return 'MPS'
        } else {
            return 'IC'
        }
    }

    String getVersion() {
        if (version == null) {
            return null
        }
        if (version.startsWith('JPS-') || version.startsWith('MPS-')) {
            return version.substring(4)
        }
        if (version.startsWith('IU-') || version.startsWith('IC-') ||
                version.startsWith('RD-') || version.startsWith('CL-')) {
            return version.substring(3)
        }
        return version
    }

    Set<PluginDependency> getPluginDependencies() {
        return pluginDependencies
    }
}
