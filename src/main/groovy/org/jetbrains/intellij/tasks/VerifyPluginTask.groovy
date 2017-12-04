package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.jetbrains.intellij.IntelliJPlugin

@SuppressWarnings("GroovyUnusedDeclaration")
class VerifyPluginTask extends ConventionTask implements VerificationTask {
    private Object pluginDirectory
    private Object ignoreFailures = false
    private Object ignoreWarnings = true

    @Input
    boolean getIgnoreFailures() {
        return this.ignoreFailures
    }

    boolean isIgnoreFailures() {
        return this.ignoreFailures
    }

    void setIgnoreFailures(boolean ignoreFailures) {
        this.ignoreFailures = ignoreFailures
    }

    @Input
    boolean getIgnoreWarnings() {
        return this.ignoreWarnings
    }

    boolean isIgnoreWarnings() {
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
        boolean failBuild = true
        def creationResult = IdePluginManager.createManager().createPlugin(getPluginDirectory())
        if (creationResult instanceof PluginCreationSuccess) {
            failBuild = !ignoreWarnings && !creationResult.warnings.empty
        }

        if (creationResult instanceof PluginCreationSuccess) {
            creationResult.warnings.each {
                IntelliJPlugin.LOG.warn("Plugin verification: $it.message")
            }
        } else if (creationResult instanceof PluginCreationFail) {
            creationResult.errorsAndWarnings.each {
                if (it.level == PluginProblem.Level.ERROR) {
                    IntelliJPlugin.LOG.error("Plugin verification: $it.message")
                } else {
                    IntelliJPlugin.LOG.warn("Plugin verification: $it.message")
                }
            }
        } else {
            IntelliJPlugin.LOG.error(creationResult.toString())
        }
        if (failBuild && !ignoreFailures) {
            throw new GradleException("Plugin verification failed.")
        }
    }
}