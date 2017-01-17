package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.OutputDirectory
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.CollectionUtils
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.Utils

class RunIdeaTask extends JavaExec {
    private static final def PREFIXES = [IU: null,
                                         IC: 'Idea',
                                         RM: 'Ruby',
                                         PY: 'Python',
                                         PC: 'PyCharmCore',
                                         PE: 'PyCharmEdu',
                                         PS: 'PhpStorm',
                                         WS: 'WebStorm',
                                         OC: 'AppCode',
                                         CL: 'CLion',
                                         DB: '0xDBE',
                                         AI: 'AndroidStudio']
    private List<Object> requiredPluginIds = []
    private Object projectDirectory
    private Object ideaDirectory
    private Object configDirectory
    private Object systemDirectory
    private Object pluginsDirectory

    List<String> getRequiredPluginIds() {
        CollectionUtils.stringize(requiredPluginIds.collect {
            it instanceof Closure ? (it as Closure).call() : it
        }.flatten())
    }

    void setRequiredPluginIds(Object... requiredPluginIds) {
        this.requiredPluginIds.clear()
        this.requiredPluginIds.addAll(requiredPluginIds as List)
    }

    void requiredPluginIds(Object... requiredPluginIds) {
        this.requiredPluginIds.addAll(requiredPluginIds as List)
    }

    @OutputDirectory
    File getProjectDirectory() {
        projectDirectory != null ? project.file(projectDirectory) : null
    }

    void setProjectDirectory(Object projectDirectory) {
        this.projectDirectory = projectDirectory
    }

    void projectDirectory(Object projectDirectory) {
        this.projectDirectory = projectDirectory
    }

    @InputDirectory
    File getIdeaDirectory() {
        ideaDirectory != null ? project.file(ideaDirectory) : null
    }

    void setIdeaDirectory(Object ideaDirectory) {
        this.ideaDirectory = ideaDirectory
    }

    void ideaDirectory(Object ideaDirectory) {
        this.ideaDirectory = ideaDirectory
    }

    @OutputDirectory
    File getConfigDirectory() {
        configDirectory != null ? project.file(configDirectory) : null
    }

    void setConfigDirectory(Object configDirectory) {
        this.configDirectory = configDirectory
    }

    void configDirectory(Object configDirectory) {
        this.configDirectory = configDirectory
    }

    @OutputDirectory
    File getSystemDirectory() {
        systemDirectory != null ? project.file(systemDirectory) : null
    }

    void setSystemDirectory(Object systemDirectory) {
        this.systemDirectory = systemDirectory
    }

    void systemDirectory(Object systemDirectory) {
        this.systemDirectory = systemDirectory
    }

    File getPluginsDirectory() {
        pluginsDirectory != null ? project.file(pluginsDirectory) : null
    }

    void setPluginsDirectory(Object pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory
    }

    void pluginsDirectory(Object pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory
    }

    RunIdeaTask() {
        setMain("com.intellij.idea.Main")
        enableAssertions = true
        outputs.upToDateWhen { false }
    }

    @Override
    void exec() {
        workingDir = project.file("${getIdeaDirectory()}/bin/")
        configureClasspath()
        configureSystemProperties()
        configureJvmArgs()
        configureArgs()
        super.exec()
    }

    private void configureClasspath() {
        File ideaDirectory = getIdeaDirectory()
        def toolsJar = Jvm.current().toolsJar
        if (toolsJar != null) classpath += project.files(toolsJar)
        classpath += project.files("$ideaDirectory/lib/idea_rt.jar",
                "$ideaDirectory/lib/idea.jar",
                "$ideaDirectory/lib/bootstrap.jar",
                "$ideaDirectory/lib/extensions.jar",
                "$ideaDirectory/lib/util.jar",
                "$ideaDirectory/lib/openapi.jar",
                "$ideaDirectory/lib/trove4j.jar",
                "$ideaDirectory/lib/jdom.jar",
                "$ideaDirectory/lib/log4j.jar")
    }

    def configureSystemProperties() {
        systemProperties(Utils.getIdeaSystemProperties(getConfigDirectory(), getSystemDirectory(), getPluginsDirectory(), getRequiredPluginIds()))
        def operatingSystem = OperatingSystem.current()
        if (operatingSystem.isMacOsX()) {
            systemProperty("idea.smooth.progress", false)
            systemProperty("apple.laf.useScreenMenuBar", true)
        } else if (operatingSystem.isUnix() && !getSystemProperties().containsKey("sun.awt.disablegrab")) {
            systemProperty("sun.awt.disablegrab", true)
        }
        systemProperty("idea.classpath.index.enabled", false)
        systemProperty("idea.is.internal", true)

        if (!getSystemProperties().containsKey('idea.platform.prefix')) {
            def matcher = Utils.VERSION_PATTERN.matcher(Utils.ideaBuildNumber(getIdeaDirectory()))
            if (matcher.find()) {
                def prefix = PREFIXES.get(matcher.group(1))
                if (prefix) {
                    systemProperty('idea.platform.prefix', prefix)
                }
            }
        }
    }

    def configureJvmArgs() {
        jvmArgs = Utils.getIdeaJvmArgs(this, getJvmArgs(), getIdeaDirectory())
    }

    def configureArgs() {
        def projectDirectory = getProjectDirectory()
        if (projectDirectory) {
            args = ["$projectDirectory"]
        }
    }
}
