package org.jetbrains.intellij.tasks

import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.VersionNumber
import org.jetbrains.intellij.Utils

class RunIdeTask extends RunIdeBase {
    RunIdeTask() {
        super(true)
    }
}

class RunIdeForUiTestTask extends RunIdeBase {
    RunIdeForUiTestTask() {
        super(true)
    }
}

@CacheableTask
class BuildSearchableOptionsTask extends RunIdeBase {
    private static final List<String> TRAVERSE_UI_ARG = ["traverseUI"]

    BuildSearchableOptionsTask() {
        super(false)
        super.setArgs(TRAVERSE_UI_ARG)
    }

    @Override
    JavaExec setArgs(List<String> applicationArgs) {
        super.setArgs(TRAVERSE_UI_ARG + applicationArgs)
    }

    @Override
    JavaExec setArgs(Iterable<?> applicationArgs) {
        super.setArgs(TRAVERSE_UI_ARG + applicationArgs)
    }
}

abstract class RunIdeBase extends JavaExec {
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
                                         DB: 'DataGrip',
                                         AI: 'AndroidStudio',
                                         GO: 'GoLand',
                                         RD: 'Rider',
                                         RDCPPP: 'Rider']

    private List<Object> requiredPluginIds = []
    private Object ideDirectory
    private Object configDirectory
    private Object systemDirectory
    private Object pluginsDirectory
    private Object jbrVersion
    private Boolean myAutoReloadPlugins = false

    @Internal
    List<String> getRequiredPluginIds() {
        Utils.stringListInput(requiredPluginIds)
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
    @Deprecated
    String getJbreVersion() {
        Utils.stringInput(jbrVersion)
    }

    @Deprecated
    void setJbreVersion(Object jbreVersion) {
        Utils.warn(this, "jbreVersion is deprecated, use jbrVersion instead")
        this.jbrVersion = jbreVersion
    }

    @Deprecated
    void jbreVersion(Object jbreVersion) {
        Utils.warn(this, "jbreVersion is deprecated, use jbrVersion instead")
        this.jbrVersion = jbreVersion
    }

    @Input
    @Optional
    String getJbrVersion() {
        Utils.stringInput(jbrVersion)
    }

    void setJbrVersion(Object jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    void jbrVersion(Object jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    @Deprecated
    @InputDirectory
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    File getIdeaDirectory() {
        ideDirectory != null ? project.file(ideDirectory) : null
    }

    @Deprecated
    void setIdeaDirectory(Object ideDirectory) {
        Utils.warn(this, "ideaDirectory is deprecated, use ideDirectory instead")
        this.ideDirectory = ideDirectory
    }

    @Deprecated
    void ideaDirectory(Object ideDirectory) {
        Utils.warn(this, "ideaDirectory is deprecated, use ideDirectory instead")
        this.ideDirectory = ideDirectory
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.NONE)
    File getIdeDirectory() {
        ideDirectory != null ? project.file(ideDirectory) : null
    }

    void setIdeDirectory(Object ideDirectory) {
        this.ideDirectory = ideDirectory
    }

    void ideDirectory(Object ideDirectory) {
        this.ideDirectory = ideDirectory
    }

    @Internal
    File getConfigDirectory() {
        configDirectory != null ? project.file(configDirectory) : null
    }

    void setConfigDirectory(Object configDirectory) {
        this.configDirectory = configDirectory
    }

    void configDirectory(Object configDirectory) {
        this.configDirectory = configDirectory
    }

    @Internal
    File getSystemDirectory() {
        systemDirectory != null ? project.file(systemDirectory) : null
    }

    void setSystemDirectory(Object systemDirectory) {
        this.systemDirectory = systemDirectory
    }

    void systemDirectory(Object systemDirectory) {
        this.systemDirectory = systemDirectory
    }

    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    File getPluginsDirectory() {
        pluginsDirectory != null ? project.file(pluginsDirectory) : null
    }

    void setPluginsDirectory(Object pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory
    }

    void pluginsDirectory(Object pluginsDirectory) {
        this.pluginsDirectory = pluginsDirectory
    }

    @Input
    @Optional
    Boolean getAutoReloadPlugins() {
        return this.myAutoReloadPlugins
    }

    /**
     * Enables auto-reload of dynamic plugins. Dynamic plugins will be reloaded automatically when their JARs are
     * modified. This allows a much faster development cycle by avoiding a full restart of the development instance
     * after code changes. Enabled by default in 2020.2 and higher.
     */
    void setAutoReloadPlugins(Boolean value) {
        this.myAutoReloadPlugins = value
    }

    RunIdeBase(boolean runAlways) {
        setMain("com.intellij.idea.Main")
        enableAssertions = true
        if (runAlways) {
            outputs.upToDateWhen { false }
        }
    }

    @Override
    void exec() {
        workingDir = project.file("${getIdeDirectory()}/bin/")
        configureClasspath()
        configureSystemProperties()
        configureJvmArgs()
        executable(getExecutable())
        super.exec()
    }

    private void configureClasspath() {
        File ideDirectory = getIdeDirectory()
        def executable = getExecutable()
        def toolsJar = executable ? project.file(Utils.resolveToolsJar(executable)) : null
        toolsJar = toolsJar?.exists() ? toolsJar : Jvm.current().toolsJar
        if (toolsJar != null) {
            classpath += project.files(toolsJar)
        }

        def buildNumber = Utils.ideBuildNumber(getIdeDirectory())
        def version = VersionNumber.parse(buildNumber[buildNumber.indexOf('-') + 1..-1])
        if (version > VersionNumber.parse("203.0")) {
            classpath += project.files(
                    "$ideDirectory/lib/bootstrap.jar",
                    "$ideDirectory/lib/util.jar",
                    "$ideDirectory/lib/jdom.jar",
                    "$ideDirectory/lib/log4j.jar",
                    "$ideDirectory/lib/jna.jar",
            )
        }
        else {
            classpath += project.files(
                    "$ideDirectory/lib/bootstrap.jar",
                    "$ideDirectory/lib/extensions.jar",
                    "$ideDirectory/lib/util.jar",
                    "$ideDirectory/lib/jdom.jar",
                    "$ideDirectory/lib/log4j.jar",
                    "$ideDirectory/lib/jna.jar",
                    "$ideDirectory/lib/trove4j.jar",
            )
        }
    }

    def configureSystemProperties() {
        systemProperties(getSystemProperties())
        systemProperties(Utils.getIdeaSystemProperties(getConfigDirectory(), getSystemDirectory(), getPluginsDirectory(), getRequiredPluginIds()))
        def operatingSystem = OperatingSystem.current()
        def userDefinedSystemProperties = getSystemProperties()
        if (operatingSystem.isMacOsX()) {
            systemPropertyIfNotDefined("idea.smooth.progress", false, userDefinedSystemProperties)
            systemPropertyIfNotDefined("apple.laf.useScreenMenuBar", true, userDefinedSystemProperties)
            systemPropertyIfNotDefined("apple.awt.fileDialogForDirectories", true, userDefinedSystemProperties)
        } else if (operatingSystem.isUnix()) {
            systemPropertyIfNotDefined("sun.awt.disablegrab", true, userDefinedSystemProperties)
        }
        systemPropertyIfNotDefined("idea.classpath.index.enabled", false, userDefinedSystemProperties)
        systemPropertyIfNotDefined("idea.is.internal", true, userDefinedSystemProperties)

        if (!userDefinedSystemProperties.containsKey("idea.auto.reload.plugins") && getAutoReloadPlugins()) {
            systemProperty("idea.auto.reload.plugins", "true")
        }

        if (!getSystemProperties().containsKey('idea.platform.prefix')) {
            def matcher = Utils.VERSION_PATTERN.matcher(Utils.ideBuildNumber(getIdeDirectory()))
            if (matcher.find()) {
                def abbreviation = matcher.group(1)
                def prefix = PREFIXES.get(abbreviation)
                if (prefix) {
                    systemProperty('idea.platform.prefix', prefix)
                }
            }
        }
    }

    private void systemPropertyIfNotDefined(String name, Object value, Map<String, Object> userDefinedSystemProperties) {
        if (!userDefinedSystemProperties.containsKey(name)) {
            systemProperty(name, value)
        }
    }

    def configureJvmArgs() {
        jvmArgs = Utils.getIdeJvmArgs(this, getJvmArgs(), getIdeDirectory())
    }
}
