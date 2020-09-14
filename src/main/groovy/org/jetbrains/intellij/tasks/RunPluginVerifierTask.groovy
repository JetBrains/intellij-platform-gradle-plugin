package org.jetbrains.intellij.tasks

import groovy.json.JsonSlurper
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.jvm.Jvm
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.dependency.IdeaDependencyManager

class RunPluginVerifierTask extends ConventionTask {
    private static final String BINTRAY_API_VERIFIER_VERSION_LATEST = "https://api.bintray.com/packages/jetbrains/intellij-plugin-service/intellij-plugin-verifier/versions/_latest"
    private static final String VERIFIER_VERSION_LATEST = "latest"

    private List<String> ides = new ArrayList<String>()
    private String verifierVersion = VERIFIER_VERSION_LATEST
    private String verificationReportsDir = "${project.buildDir}/reports/pluginsVerifier"
    private String runtimeDir = Jvm.current().getJavaHome()
    private String externalPrefixes = ""
    private String pluginsToCheckAllBuilds = ""
    private String pluginsToCheckLastBuilds = ""
    private String teamCity = ""
    private String tcGrouping = ""
    private String excludedPluginsFile = ""
    private String dumpBrokenPluginList = ""
    private String pluginsToCheckFile = ""
    private String subsystemsToCheck = ""

    @Input
    List<String> getIdes() {
        return ides
    }

    void setIdes(List<String> ides) {
        this.ides = ides
    }

    @Input
    String getVerifierVersion() {
        return verifierVersion
    }

    void setVerifierVersion(String verifierVersion) {
        this.verifierVersion = verifierVersion
    }

    @Input
    String getVerificationReportsDir() {
        return verificationReportsDir
    }

    void setVerificationReportsDir(String verificationReportsDir) {
        this.verificationReportsDir = verificationReportsDir
    }

    @Input
    String getRuntimeDir() {
        return runtimeDir
    }

    void setRuntimeDir(String runtimeDir) {
        this.runtimeDir = runtimeDir
    }

    @Input
    String getExternalPrefixes() {
        return externalPrefixes
    }

    void setExternalPrefixes(String externalPrefixes) {
        this.externalPrefixes = externalPrefixes
    }

    @Input
    String getPluginsToCheckAllBuilds() {
        return pluginsToCheckAllBuilds
    }

    void setPluginsToCheckAllBuilds(String pluginsToCheckAllBuilds) {
        this.pluginsToCheckAllBuilds = pluginsToCheckAllBuilds
    }

    @Input
    String getPluginsToCheckLastBuilds() {
        return pluginsToCheckLastBuilds
    }

    void setPluginsToCheckLastBuilds(String pluginsToCheckLastBuilds) {
        this.pluginsToCheckLastBuilds = pluginsToCheckLastBuilds
    }

    @Input
    String getTeamCity() {
        return teamCity
    }

    void setTeamCity(String teamCity) {
        this.teamCity = teamCity
    }

    @Input
    String getTcGrouping() {
        return tcGrouping
    }

    void setTcGrouping(String tcGrouping) {
        this.tcGrouping = tcGrouping
    }

    @Input
    String getExcludedPluginsFile() {
        return excludedPluginsFile
    }

    void setExcludedPluginsFile(String excludedPluginsFile) {
        this.excludedPluginsFile = excludedPluginsFile
    }

    @Input
    String getDumpBrokenPluginList() {
        return dumpBrokenPluginList
    }

    void setDumpBrokenPluginList(String dumpBrokenPluginList) {
        this.dumpBrokenPluginList = dumpBrokenPluginList
    }

    @Input
    String getPluginsToCheckFile() {
        return pluginsToCheckFile
    }

    void setPluginsToCheckFile(String pluginsToCheckFile) {
        this.pluginsToCheckFile = pluginsToCheckFile
    }

    @Input
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

        project.javaexec {
            classpath = project.files(verifierPath)
            main = "com.jetbrains.pluginverifier.PluginVerifierMain"
            args = verifierArgs
        }
    }

    private String getVerifierPath() {
        project.repositories.maven { it.url = IntelliJPlugin.DEFAULT_INTELLIJ_PLUGIN_SERVICE }
        def resolvedVerifierVersion = resolveVerifierVersion()
        def dependency = project.dependencies.create("org.jetbrains.intellij.plugins:verifier-cli:$resolvedVerifierVersion:all@jar")
        def configuration = project.configurations.detachedConfiguration(dependency)
        return configuration.singleFile.absolutePath
    }

    private String resolveVerifierVersion() {
        if (verifierVersion != VERIFIER_VERSION_LATEST) {
            return verifierVersion
        }

        def url = new URL(BINTRAY_API_VERIFIER_VERSION_LATEST)
        return new JsonSlurper().parse(url)["name"]
    }

    private List<String> getOptions() {
        def args = [
                "-verification-reports-dir", verificationReportsDir,
                "-runtime-dir", runtimeDir
        ]

        if (!externalPrefixes.isEmpty()) {
            args += ["-external-prefixes", externalPrefixes]
        }
        if (!pluginsToCheckAllBuilds.isEmpty()) {
            args += ["-plugins-to-check-all-builds", pluginsToCheckAllBuilds]
        }
        if (!pluginsToCheckLastBuilds.isEmpty()) {
            args += ["-plugins-to-check-last-builds", pluginsToCheckLastBuilds]
        }
        if (!teamCity.isEmpty()) {
            args += ["-team-city", teamCity]
        }
        if (!tcGrouping.isEmpty()) {
            args += ["-tc-grouping", tcGrouping]
        }
        if (!excludedPluginsFile.isEmpty()) {
            args += ["-excluded-plugins-file", excludedPluginsFile]
        }
        if (!dumpBrokenPluginList.isEmpty()) {
            args += ["-dump-broken-plugin-list", dumpBrokenPluginList]
        }
        if (!pluginsToCheckFile.isEmpty()) {
            args += ["-plugins-to-check-file", pluginsToCheckFile]
        }
        if (!subsystemsToCheck.isEmpty()) {
            args += ["-subsystems-to-check", subsystemsToCheck]
        }

        return args
    }
}
