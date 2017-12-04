package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.CollectionUtils
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.jbre.JbreResolver

class RunIdeTask extends JavaExec {
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
                                         AI: 'AndroidStudio',
                                         GO: 'GoLand',
                                         RD: 'Rider',
                                         RS: 'Rider']

    private List<Object> requiredPluginIds = []
    private Object ideaDirectory
    private Object configDirectory
    private Object systemDirectory
    private Object pluginsDirectory
    private Object jbreVersion

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

    @Input
    @Optional
    String getJbreVersion() {
        Utils.stringInput(jbreVersion)
    }

    void setJbreVersion(Object jbreVersion) {
        this.jbreVersion = jbreVersion
    }

    void jbreVersion(Object jbreVersion) {
        this.jbreVersion = jbreVersion
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

    RunIdeTask() {
        setMain("com.intellij.idea.Main")
        enableAssertions = true
        outputs.upToDateWhen { false }
    }

    @Override
    void exec() {
        workingDir = project.file("${getIdeaDirectory()}/bin/")
        configureJbre()
        configureClasspath()
        configureSystemProperties()
        configureJvmArgs()
        super.exec()
    }

    private void configureJbre() {
        def jvm = new JbreResolver(project).resolve(getJbreVersion())
        if (jvm != null) {
            executable(jvm)
        }
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
        systemProperties(getSystemProperties())
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
                def abbreviation = matcher.group(1)
                def prefix = PREFIXES.get(abbreviation)
                if (prefix) {
                    systemProperty('idea.platform.prefix', prefix)

                    if (abbreviation == "RD" || abbreviation == "RS") {
                        // Allow debugging Rider's out of process ReSharper host
                        systemProperty('rider.debug.mono.debug', true)
                        systemProperty('rider.debug.mono.allowConnect', true)
                    }
                }
            }
        }
    }

    def configureJvmArgs() {
        jvmArgs = Utils.getIdeaJvmArgs(this, getJvmArgs(), getIdeaDirectory())
    }
}
