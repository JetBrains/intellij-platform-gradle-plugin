package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.jetbrains.intellij.Utils

@SuppressWarnings("GroovyUnusedDeclaration")
class VerifyPluginTask extends ConventionTask implements VerificationTask {
    private Object pluginDirectory
    private Object ignoreFailures = false
    private Object ignoreWarnings = true

    @Input
    boolean getIgnoreFailures() {
        return this.ignoreFailures
    }

    void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures
    }

    @Input
    boolean getIgnoreWarnings() {
        return this.ignoreWarnings
    }

    void setIgnoreWarnings(boolean ignoreWarnings) {
        this.ignoreWarnings = ignoreWarnings
    }

    @SkipWhenEmpty
    @InputDirectory
    File getPluginDirectory() {
        this.pluginDirectory != null ? project.file(this.pluginDirectory) : null
    }

    void setPluginDirectory(Object pluginDirectory) {
        this.pluginDirectory = pluginDirectory
    }

    void pluginDirectory(Object pluginDirectory) {
        this.pluginDirectory = pluginDirectory
    }

    @TaskAction
    void verifyPlugin() {
        def creationResult = IdePluginManager.createManager().createPlugin(getPluginDirectory().toPath())
        if (creationResult instanceof PluginCreationSuccess) {
            creationResult.warnings.each {
                Utils.warn(this, it.message)
            }
        } else if (creationResult instanceof PluginCreationFail) {
            creationResult.errorsAndWarnings.each {
                if (it.level == PluginProblem.Level.ERROR) {
                    Utils.error(this, it.message)
                } else {
                    Utils.warn(this, it.message)
                }
            }
        } else {
            Utils.error(this, creationResult.toString())
        }
        boolean failBuild = !(creationResult instanceof PluginCreationSuccess) ||
                !getIgnoreWarnings() && !creationResult.warnings.empty
        if (failBuild && !getIgnoreFailures()) {
            throw new GradleException("Plugin verification failed.")
        }
    }
}