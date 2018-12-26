package org.jetbrains.intellij

import org.gradle.api.Project
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
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
    String jreRepo
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

    Project project
    private IdeaDependency ideaDependency
    private final Set<PluginDependency> pluginDependencies = new HashSet<>()
    private boolean pluginDependenciesConfigured = false

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
        } else if (version.startsWith('PY-') || 'PY' == type) {
            return 'PY'
        } else if (version.startsWith('PC-') || 'PC' == type) {
            return 'PC'
        } else if (version.startsWith('RD-') || 'RD' == type) {
            return 'RD'
        } else {
            return 'IC'
        }
    }

    String getVersion() {
        if (version == null) {
            return null
        }
        if (version.startsWith('JPS-')) {
            return version.substring(4)
        }
        if (version.startsWith('IU-') || version.startsWith('IC-') ||
                version.startsWith('RD-') || version.startsWith('CL-')
                || version.startsWith('PY-') || version.startsWith('PC-')) {
            return version.substring(3)
        }
        return version
    }

    def addPluginDependency(@NotNull PluginDependency pluginDependency) {
        pluginDependencies.add(pluginDependency)
    }

    Set<PluginDependency> getUnresolvedPluginDependencies() {
        if (pluginDependenciesConfigured) {
            return []
        }
        return pluginDependencies
    }

    Set<PluginDependency> getPluginDependencies() {
        if (!pluginDependenciesConfigured) {
            Utils.debug(project, "Plugin dependencies are resolved", new Throwable())
            pluginDependenciesConfigured = true
            project.configurations.getByName(IntelliJPlugin.IDEA_PLUGINS_CONFIGURATION_NAME).resolve()
        }
        return pluginDependencies
    }

    def setIdeaDependency(IdeaDependency ideaDependency) {
        this.ideaDependency = ideaDependency
    }

    def getIdeaDependency() {
        if (ideaDependency == null) {
            Utils.debug(project, "IDE dependency is resolved", new Throwable())
            project.configurations.getByName(IntelliJPlugin.IDEA_CONFIGURATION_NAME).resolve()
            if (ideaDependency == null) {
                throw new BuildException("Cannot resolve ideaDependency", null)
            }
        }
        return ideaDependency
    }
}
