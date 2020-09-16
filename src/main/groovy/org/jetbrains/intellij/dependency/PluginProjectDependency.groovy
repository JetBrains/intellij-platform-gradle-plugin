package org.jetbrains.intellij.dependency

import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.intellij.plugin.IdePlugin
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import groovy.transform.CompileStatic
import groovy.transform.ToString
import org.gradle.api.Project
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.tasks.PrepareSandboxTask

@CompileStatic
@ToString(includeNames = true, includeFields = true, ignoreNulls = true)
class PluginProjectDependency implements PluginDependency {
    @NotNull
    private transient Project project

    @Lazy
    private File pluginDirectory = {
        def prepareSandboxTask = project?.tasks?.findByName(IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME)
        return prepareSandboxTask instanceof PrepareSandboxTask ?
                new File(prepareSandboxTask.getDestinationDir(), prepareSandboxTask.getPluginName()) : null
    }()

    @Lazy
    private transient PluginDependencyImpl pluginDependency = {
        if (pluginDirectory.exists()) {
            def creationResult = IdePluginManager.createManager().createPlugin(pluginDirectory.toPath())
            if (creationResult instanceof PluginCreationSuccess) {
                def intellijPlugin = creationResult.getPlugin()
                if (intellijPlugin instanceof IdePlugin) {
                    def pluginDependency = new PluginDependencyImpl(intellijPlugin.pluginId, intellijPlugin.pluginVersion, pluginDirectory)
                    pluginDependency.sinceBuild = intellijPlugin.getSinceBuild()?.asStringWithoutProductCode()
                    pluginDependency.untilBuild = intellijPlugin.getUntilBuild()?.asStringWithoutProductCode()
                    return pluginDependency
                }
            }
            Utils.error(project, "Cannot use $pluginDirectory as a plugin dependency. " + creationResult)
        }
        return null
    }()

    PluginProjectDependency(@NotNull Project project) {
        this.project = project
    }

    @NotNull
    @Override
    String getId() {
        return pluginDependency ? pluginDependency.id : "<unknown plugin id>"
    }

    @NotNull
    @Override
    String getVersion() {
        return pluginDependency ? pluginDependency.version : "<unknown plugin version>"
    }

    @Nullable
    @Override
    String getChannel() {
        return pluginDependency?.channel
    }

    @NotNull
    @Override
    File getArtifact() {
        return this.pluginDirectory
    }

    @NotNull
    @Override
    Collection<File> getJarFiles() {
        return pluginDependency ? pluginDependency.jarFiles : Collections.<File>emptyList()
    }

    @Nullable
    @Override
    File getClassesDirectory() {
        return pluginDependency?.classesDirectory
    }

    @Nullable
    @Override
    File getMetaInfDirectory() {
        return pluginDependency?.metaInfDirectory
    }

    @Override
    File getSourcesDirectory() {
        return pluginDependency?.sourcesDirectory
    }

    @Override
    boolean isBuiltin() {
        return false
    }

    @Override
    boolean isMaven() {
        return false
    }

    @Override
    boolean isCompatible(@NotNull IdeVersion ideVersion) {
        return true
    }

    @Override
    PluginDependencyNotation getNotation() {
        return new PluginDependencyNotation(id, null, null)
    }
}
