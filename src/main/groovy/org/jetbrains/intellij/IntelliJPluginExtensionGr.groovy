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
     * The type of IDE distribution (IC, IU, CL, PY, PC, RD or JPS).
     * <p/>
     * The type might be included as a prefix in {@link #version} value.
     */
    String type = 'IC'

    private Project project
    private IdeaDependency ideaDependency
    private final Set<PluginDependency> pluginDependencies = new HashSet<>()
    private boolean pluginDependenciesConfigured = false

    def setExtensionProject(@NotNull Project project) {
        this.project = project
    }

    String getType() {
        def v = version.orNull
        if (v == null) {
            return 'IC'
        }
        if (v.startsWith('IU-') || 'IU' == type) {
            return 'IU'
        } else if (v.startsWith('JPS-') || 'JPS' == type) {
            return "JPS"
        } else if (v.startsWith('CL-') || 'CL' == type) {
            return 'CL'
        } else if (v.startsWith('PY-') || 'PY' == type) {
            return 'PY'
        } else if (v.startsWith('PC-') || 'PC' == type) {
            return 'PC'
        } else if (v.startsWith('RD-') || 'RD' == type) {
            return 'RD'
        } else if (v.startsWith('GO-') || 'GO' == type) {
            return 'GO'
        } else {
            return 'IC'
        }
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
