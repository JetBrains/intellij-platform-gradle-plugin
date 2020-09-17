package org.jetbrains.intellij.tasks

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.util.CollectionUtils
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.jbr.JbrResolver

class RunPluginVerifierTask extends ConventionTask {
    private static final String BINTRAY_API_VERIFIER_VERSION_LATEST = "https://api.bintray.com/packages/jetbrains/intellij-plugin-service/intellij-plugin-verifier/versions/_latest"
    public static final String VERIFIER_VERSION_LATEST = "latest"

    static enum FailureLevel {
        COMPATIBILITY_WARNINGS("Compatibility warnings"),
        COMPATIBILITY_PROBLEMS("Compatibility problems"),
        DEPRECATED_API_USAGES("Deprecated API usages"),
        EXPERIMENTAL_API_USAGES("Experimental API usages"),
        INTERNAL_API_USAGES("Internal API usages"),
        OVERRIDE_ONLY_API_USAGES("Override-only API usages"),
        NON_EXTENDABLE_API_USAGES("Non-extendable API usages"),
        PLUGIN_STRUCTURE_WARNINGS("Plugin structure warnings"),
        MISSING_DEPENDENCIES("Missing dependencies"),
        INVALID_PLUGIN("The following files specified for the verification are not valid plugins"),
        NOT_DYNAMIC("Plugin cannot be loaded/unloaded without IDE restart");

        public static final EnumSet<FailureLevel> ALL = EnumSet.allOf(FailureLevel.class)
        public static final EnumSet<FailureLevel> NONE = EnumSet.noneOf(FailureLevel.class)

        public final String testValue

        FailureLevel(String testValue) {
            this.testValue = testValue
        }
    }

    private EnumSet<FailureLevel> failureLevel
    private List<Object> ides = []
    private Object verifierVersion
    private Object distributionFile
    private Object verificationReportsDir
    private Object jbrVersion
    private Object runtimeDir
    private Object externalPrefixes
    private Object pluginsToCheckAllBuilds
    private Object pluginsToCheckLastBuilds
    private Object teamCity
    private Object tcGrouping
    private Object excludedPluginsFile
    private Object dumpBrokenPluginList
    private Object pluginsToCheckFile
    private Object subsystemsToCheck

    @Input
    EnumSet<FailureLevel> getFailureLevel() {
        return failureLevel
    }

    void setFailureLevel(EnumSet<FailureLevel> failureLevel) {
        this.failureLevel = failureLevel
    }

    void setFailureLevel(FailureLevel failureLevel) {
        this.failureLevel = EnumSet.of(failureLevel)
    }

    @SkipWhenEmpty
    @Input
    List<String> getIdes() {
        return CollectionUtils.stringize(ides.collect {
            it instanceof Closure ? (it as Closure).call() : it
        }.flatten())
    }

    void setIdes(List<Object> ides) {
        this.ides = ides
    }

    void ides(List<Object> ides) {
        this.ides = ides
    }

    @Input
    @Optional
    String getVerifierVersion() {
        return Utils.stringInput(verifierVersion)
    }

    void setVerifierVersion(String verifierVersion) {
        this.verifierVersion = verifierVersion
    }

    void verifierVersion(Object verifierVersion) {
        this.verifierVersion = verifierVersion
    }

    @SkipWhenEmpty
    @InputFile
    File getDistributionFile() {
        distributionFile != null ? project.file(distributionFile) : null
    }

    void setDistributionFile(Object distributionFile) {
        this.distributionFile = distributionFile
    }

    void distributionFile(Object distributionFile) {
        this.distributionFile = distributionFile
    }

    @Input
    @Optional
    String getVerificationReportsDir() {
        return Utils.stringInput(verificationReportsDir)
    }

    void setVerificationReportsDir(Object verificationReportsDir) {
        this.verificationReportsDir = verificationReportsDir
    }

    void verificationReportsDir(Object verificationReportsDir) {
        this.verificationReportsDir = verificationReportsDir
    }

    @Input
    @Optional
    String getJbrVersion() {
        return Utils.stringInput(jbrVersion)
    }

    void setJbrVersion(Object jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    void jbrVersion(Object jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    @Input
    @Optional
    String getRuntimeDir() {
        return Utils.stringInput(runtimeDir)
    }

    void setRuntimeDir(Object runtimeDir) {
        this.runtimeDir = runtimeDir
    }

    void runtimeDir(Object runtimeDir) {
        this.runtimeDir = runtimeDir
    }

    @Input
    @Optional
    String getExternalPrefixes() {
        return Utils.stringInput(externalPrefixes)
    }

    void setExternalPrefixes(Object externalPrefixes) {
        this.externalPrefixes = externalPrefixes
    }

    void externalPrefixes(Object externalPrefixes) {
        this.externalPrefixes = externalPrefixes
    }

    @Input
    @Optional
    String getPluginsToCheckAllBuilds() {
        return Utils.stringInput(pluginsToCheckAllBuilds)
    }

    void setPluginsToCheckAllBuilds(Object pluginsToCheckAllBuilds) {
        this.pluginsToCheckAllBuilds = pluginsToCheckAllBuilds
    }

    void pluginsToCheckAllBuilds(Object pluginsToCheckAllBuilds) {
        this.pluginsToCheckAllBuilds = pluginsToCheckAllBuilds
    }

    @Input
    @Optional
    String getPluginsToCheckLastBuilds() {
        return Utils.stringInput(pluginsToCheckLastBuilds)
    }

    void setPluginsToCheckLastBuilds(Object pluginsToCheckLastBuilds) {
        this.pluginsToCheckLastBuilds = pluginsToCheckLastBuilds
    }

    void pluginsToCheckLastBuilds(Object pluginsToCheckLastBuilds) {
        this.pluginsToCheckLastBuilds = pluginsToCheckLastBuilds
    }

    @Input
    @Optional
    String getTeamCity() {
        return Utils.stringInput(teamCity)
    }

    void setTeamCity(Object teamCity) {
        this.teamCity = teamCity
    }

    void teamCity(Object teamCity) {
        this.teamCity = teamCity
    }

    @Input
    @Optional
    String getTcGrouping() {
        return Utils.stringInput(tcGrouping)
    }

    void setTcGrouping(Object tcGrouping) {
        this.tcGrouping = tcGrouping
    }

    void tcGrouping(Object tcGrouping) {
        this.tcGrouping = tcGrouping
    }

    @Input
    @Optional
    String getExcludedPluginsFile() {
        return Utils.stringInput(excludedPluginsFile)
    }

    void setExcludedPluginsFile(Object excludedPluginsFile) {
        this.excludedPluginsFile = excludedPluginsFile
    }

    void excludedPluginsFile(Object excludedPluginsFile) {
        this.excludedPluginsFile = excludedPluginsFile
    }

    @Input
    @Optional
    String getDumpBrokenPluginList() {
        return Utils.stringInput(dumpBrokenPluginList)
    }

    void setDumpBrokenPluginList(Object dumpBrokenPluginList) {
        this.dumpBrokenPluginList = dumpBrokenPluginList
    }

    void dumpBrokenPluginList(Object dumpBrokenPluginList) {
        this.dumpBrokenPluginList = dumpBrokenPluginList
    }

    @Input
    @Optional
    String getPluginsToCheckFile() {
        return Utils.stringInput(pluginsToCheckFile)
    }

    void setPluginsToCheckFile(Object pluginsToCheckFile) {
        this.pluginsToCheckFile = pluginsToCheckFile
    }

    void pluginsToCheckFile(Object pluginsToCheckFile) {
        this.pluginsToCheckFile = pluginsToCheckFile
    }

    @Input
    @Optional
    String getSubsystemsToCheck() {
        return Utils.stringInput(subsystemsToCheck)
    }

    void setSubsystemsToCheck(Object subsystemsToCheck) {
        this.subsystemsToCheck = subsystemsToCheck
    }

    void subsystemsToCheck(Object subsystemsToCheck) {
        this.subsystemsToCheck = subsystemsToCheck
    }

    @TaskAction
    void runPluginVerifier() {
        def file = getDistributionFile()
        if (file == null || !file.exists()) {
            throw new IllegalStateException("Plugin file does not exist: $file")
        }

        def extension = project.extensions.findByType(IntelliJPluginExtension)
        def resolver = new IdeaDependencyManager(extension.intellijRepo ?: IntelliJPlugin.DEFAULT_INTELLIJ_REPO)
        def verifierPath = getVerifierPath()

        def verifierArgs = ["check-plugin"]
        verifierArgs += getOptions()
        verifierArgs += [file.absolutePath]
        verifierArgs += getIdes().collect {
            def (String type, String version) = it.split("-")
            def dependency = resolver.resolveRemote(project, version, type, false)
            return dependency.classes.absolutePath
        }

        new ByteArrayOutputStream().withStream { os ->
            project.javaexec {
                classpath = project.files(verifierPath)
                main = "com.jetbrains.pluginverifier.PluginVerifierMain"
                args = verifierArgs
                standardOutput = os
            }

            def output = os.toString()
            println output
            for (FailureLevel level : FailureLevel.values()) {
                if (failureLevel.contains(level) && output.contains(level.testValue)) {
                    throw new GradleException(level.toString())
                }
            }
        }
    }

    private String getVerifierPath() {
        def repository = project.repositories.maven { it.url = IntelliJPlugin.DEFAULT_INTELLIJ_PLUGIN_SERVICE }
        try {
            def resolvedVerifierVersion = resolveVerifierVersion()
            def dependency = project.dependencies.create("org.jetbrains.intellij.plugins:verifier-cli:$resolvedVerifierVersion:all@jar")
            def configuration = project.configurations.detachedConfiguration(dependency)
            return configuration.singleFile.absolutePath
        }
        finally {
            project.repositories.remove(repository)
        }
    }

    private String resolveVerifierVersion() {
        if (getVerifierVersion() != VERIFIER_VERSION_LATEST) {
            return getVerifierVersion()
        }

        def url = new URL(BINTRAY_API_VERIFIER_VERSION_LATEST)
        return new JsonSlurper().parse(url)["name"]
    }

    private String resolveRuntimeDir() {
        if (runtimeDir != null) {
            return getRuntimeDir()
        }

        def jbrResolver = new JbrResolver(project, this)
        if (jbrVersion != null) {
            def jbr = jbrResolver.resolve(getJbrVersion())
            if (jbr != null) {
                return jbr.javaHome
            }
            Utils.warn(this, "Cannot resolve JBR ${getJbrVersion()}. Falling back to builtin JBR.")
        }

        def extension = project.extensions.findByType(IntelliJPluginExtension)
        def jbrPath = OperatingSystem.current().isMacOsX() ? "jbr/Contents/Home" : "jbr"

        def builtinJbrVersion = Utils.getBuiltinJbrVersion(Utils.ideSdkDirectory(project, extension))
        if (builtinJbrVersion != null) {
            def builtinJbr = jbrResolver.resolve(builtinJbrVersion)
            if (builtinJbr != null) {
                def javaHome = new File(builtinJbr.javaHome, jbrPath)
                if (javaHome.exists()) {
                    return javaHome
                }
            }
            Utils.warn(this, "Cannot resolve builtin JBR $builtinJbrVersion. Falling local Java.")
        }

        if (extension.alternativeIdePath) {
            def javaHome = new File(Utils.ideaDir(extension.alternativeIdePath), jbrPath)
            if (javaHome.exists()) {
                return javaHome
            }
            Utils.warn(this, "Cannot resolve JBR at $javaHome. Falling back to current JVM.")
        }

        return Jvm.current().getJavaHome()
    }

    private List<String> getOptions() {
        def args = [
                "-verification-reports-dir", getVerificationReportsDir(),
                "-runtime-dir", resolveRuntimeDir()
        ]

        if (externalPrefixes != null) {
            args += ["-external-prefixes", getExternalPrefixes()]
        }
        if (pluginsToCheckAllBuilds != null) {
            args += ["-plugins-to-check-all-builds", getPluginsToCheckAllBuilds()]
        }
        if (pluginsToCheckLastBuilds != null) {
            args += ["-plugins-to-check-last-builds", getPluginsToCheckLastBuilds()]
        }
        if (teamCity != null) {
            args += ["-team-city", getTeamCity()]
        }
        if (tcGrouping != null) {
            args += ["-tc-grouping", getTcGrouping()]
        }
        if (excludedPluginsFile != null) {
            args += ["-excluded-plugins-file", getExcludedPluginsFile()]
        }
        if (dumpBrokenPluginList != null) {
            args += ["-dump-broken-plugin-list", getDumpBrokenPluginList()]
        }
        if (pluginsToCheckFile != null) {
            args += ["-plugins-to-check-file", getPluginsToCheckFile()]
        }
        if (subsystemsToCheck != null) {
            args += ["-subsystems-to-check", getSubsystemsToCheck()]
        }

        return args
    }
}
