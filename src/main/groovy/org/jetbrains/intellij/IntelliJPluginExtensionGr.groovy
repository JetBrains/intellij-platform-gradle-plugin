package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.Project
import org.gradle.api.model.ObjectFactory
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.PluginDependency

class IntelliJPluginExtensionGr extends IntelliJPluginExtension {

    IntelliJPluginExtensionGr(@NotNull ObjectFactory objects) {
        super(objects)
    }

    /**
     * The version of the IntelliJ Platform IDE that will be used to build the plugin.
     * <p/>
     * Please see <a href="https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html">Plugin Compatibility</a> in SDK docs for more details.
     */
    String version

    /**
     * The type of IDE distribution (IC, IU, CL, PY, PC, RD or JPS).
     * <p/>
     * The type might be included as a prefix in {@link #version} value.
     */
    String type = 'IC'

    /**
     * configure extra dependency artifacts from intellij repo
     *  the dependencies on them could be configured only explicitly using intellijExtra function in the dependencies block
     */
    Object[] extraDependencies = []

    private Project project
    private IdeaDependency ideaDependency
    private final Set<PluginDependency> pluginDependencies = new HashSet<>()
    private boolean pluginDependenciesConfigured = false

    def setExtensionProject(@NotNull Project project) {
        this.project = project
    }

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
        } else if (version.startsWith('GO-') || 'GO' == type) {
            return 'GO'
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
                || version.startsWith('PY-') || version.startsWith('PC-') || version.startsWith('GO-')) {
            return version.substring(3)
        }
        return version
    }

    String getBuildVersion() {
        return IdeVersion.createIdeVersion(getIdeaDependency().buildNumber).asStringWithoutProductCode()
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
            project.configurations.getByName(IntelliJPlugin.IDEA_PLUGINS_CONFIGURATION_NAME).resolve()
            pluginDependenciesConfigured = true
        }
        return pluginDependencies
    }

    def setIdeaDependency(IdeaDependency ideaDependency) {
        this.ideaDependency = ideaDependency
    }

    IdeaDependency getIdeaDependency() {
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
