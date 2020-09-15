package org.jetbrains.intellij.tasks

import groovy.json.JsonSlurper
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.jbr.JbrResolver

class RunPluginVerifierTask extends ConventionTask {
    private static final String BINTRAY_API_VERIFIER_VERSION_LATEST = "https://api.bintray.com/packages/jetbrains/intellij-plugin-service/intellij-plugin-verifier/versions/_latest"
    private static final String VERIFIER_VERSION_LATEST = "latest"

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

    private EnumSet<FailureLevel> failureLevel = EnumSet.of(FailureLevel.INVALID_PLUGIN)
    private List<String> ides = new ArrayList<String>()
    private String verifierVersion = VERIFIER_VERSION_LATEST
    private String verificationReportsDir = "${project.buildDir}/reports/pluginsVerifier"
    private String jbrVersion
    private String runtimeDir
    private String externalPrefixes
    private String pluginsToCheckAllBuilds
    private String pluginsToCheckLastBuilds
    private String teamCity
    private String tcGrouping
    private String excludedPluginsFile
    private String dumpBrokenPluginList
    private String pluginsToCheckFile
    private String subsystemsToCheck

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

    @Input
    @Optional
    List<String> getIdes() {
        return ides
    }

    void setIdes(List<String> ides) {
        this.ides = ides
    }

    @Input
    @Optional
    String getVerifierVersion() {
        return verifierVersion
    }

    void setVerifierVersion(String verifierVersion) {
        this.verifierVersion = verifierVersion
    }

    @Input
    @Optional
    String getVerificationReportsDir() {
        return verificationReportsDir
    }

    void setVerificationReportsDir(String verificationReportsDir) {
        this.verificationReportsDir = verificationReportsDir
    }

    @Input
    @Optional
    String getJbrVersion() {
        return jbrVersion
    }

    void setJbrVersion(String jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    @Input
    @Optional
    String getRuntimeDir() {
        return runtimeDir
    }

    void setRuntimeDir(String runtimeDir) {
        this.runtimeDir = runtimeDir
    }

    @Input
    @Optional
    String getExternalPrefixes() {
        return externalPrefixes
    }

    void setExternalPrefixes(String externalPrefixes) {
        this.externalPrefixes = externalPrefixes
    }

    @Input
    @Optional
    String getPluginsToCheckAllBuilds() {
        return pluginsToCheckAllBuilds
    }

    void setPluginsToCheckAllBuilds(String pluginsToCheckAllBuilds) {
        this.pluginsToCheckAllBuilds = pluginsToCheckAllBuilds
    }

    @Input
    @Optional
    String getPluginsToCheckLastBuilds() {
        return pluginsToCheckLastBuilds
    }

    void setPluginsToCheckLastBuilds(String pluginsToCheckLastBuilds) {
        this.pluginsToCheckLastBuilds = pluginsToCheckLastBuilds
    }

    @Input
    @Optional
    String getTeamCity() {
        return teamCity
    }

    void setTeamCity(String teamCity) {
        this.teamCity = teamCity
    }

    @Input
    @Optional
    String getTcGrouping() {
        return tcGrouping
    }

    void setTcGrouping(String tcGrouping) {
        this.tcGrouping = tcGrouping
    }

    @Input
    @Optional
    String getExcludedPluginsFile() {
        return excludedPluginsFile
    }

    void setExcludedPluginsFile(String excludedPluginsFile) {
        this.excludedPluginsFile = excludedPluginsFile
    }

    @Input
    @Optional
    String getDumpBrokenPluginList() {
        return dumpBrokenPluginList
    }

    void setDumpBrokenPluginList(String dumpBrokenPluginList) {
        this.dumpBrokenPluginList = dumpBrokenPluginList
    }

    @Input
    @Optional
    String getPluginsToCheckFile() {
        return pluginsToCheckFile
    }

    void setPluginsToCheckFile(String pluginsToCheckFile) {
        this.pluginsToCheckFile = pluginsToCheckFile
    }

    @Input
    @Optional
    String getSubsystemsToCheck() {
        return subsystemsToCheck
    }

    void setSubsystemsToCheck(String subsystemsToCheck) {
        this.subsystemsToCheck = subsystemsToCheck
    }

    @TaskAction
    void runPluginVerifier() {
        def extension = project.extensions.findByType(IntelliJPluginExtension)
        def pluginFileName = "${extension.pluginName}-${project.version}"

        if (!project.file("${project.buildDir}/distributions/${pluginFileName}.zip").exists()) {
            throw new IllegalStateException("Plugin file $pluginFileName does not exist.")
        }

        if (ides.isEmpty()) {
            ides.add("${extension.type}-${extension.version}")
        }

        def resolver = new IdeaDependencyManager(extension.intellijRepo ?: IntelliJPlugin.DEFAULT_INTELLIJ_REPO)
        def verifierPath = getVerifierPath()

        def verifierArgs = ["check-plugin"]
        verifierArgs += getOptions()
        verifierArgs += ["${project.buildDir}/distributions/${pluginFileName}.zip"]
        verifierArgs += ides.collect {
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
        if (verifierVersion != VERIFIER_VERSION_LATEST) {
            return verifierVersion
        }

        def url = new URL(BINTRAY_API_VERIFIER_VERSION_LATEST)
        return new JsonSlurper().parse(url)["name"]
    }

    private String resolveRuntimeDir() {
        if (runtimeDir != null) {
            return runtimeDir
        }

        def jbrResolver = new JbrResolver(project, this)
        if (jbrVersion != null) {
            def jbr = jbrResolver.resolve(jbrVersion)
            if (jbr != null) {
                return jbr.javaHome
            }
            Utils.warn(this, "Cannot resolve JBR $jbrVersion. Falling back to builtin JBR.")
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
                "-verification-reports-dir", verificationReportsDir,
                "-runtime-dir", resolveRuntimeDir()
        ]

        if (externalPrefixes != null) {
            args += ["-external-prefixes", externalPrefixes]
        }
        if (pluginsToCheckAllBuilds != null) {
            args += ["-plugins-to-check-all-builds", pluginsToCheckAllBuilds]
        }
        if (pluginsToCheckLastBuilds != null) {
            args += ["-plugins-to-check-last-builds", pluginsToCheckLastBuilds]
        }
        if (teamCity != null) {
            args += ["-team-city", teamCity]
        }
        if (tcGrouping != null) {
            args += ["-tc-grouping", tcGrouping]
        }
        if (excludedPluginsFile != null) {
            args += ["-excluded-plugins-file", excludedPluginsFile]
        }
        if (dumpBrokenPluginList != null) {
            args += ["-dump-broken-plugin-list", dumpBrokenPluginList]
        }
        if (pluginsToCheckFile != null) {
            args += ["-plugins-to-check-file", pluginsToCheckFile]
        }
        if (subsystemsToCheck != null) {
            args += ["-subsystems-to-check", subsystemsToCheck]
        }

        return args
    }
}
