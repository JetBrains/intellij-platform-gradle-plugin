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

    private Project project
    private IdeaDependency ideaDependency
    private final Set<PluginDependency> pluginDependencies = new HashSet<>()
    private boolean pluginDependenciesConfigured = false

    def setExtensionProject(@NotNull Project project) {
        this.project = project
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
