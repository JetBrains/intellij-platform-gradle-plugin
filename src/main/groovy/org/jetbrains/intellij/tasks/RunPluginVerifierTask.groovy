package org.jetbrains.intellij.tasks

import de.undercouch.gradle.tasks.download.DownloadAction
import de.undercouch.gradle.tasks.download.org.apache.http.client.utils.URIBuilder
import groovy.json.JsonSlurper
import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.*
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.IntelliJPlugin
import org.jetbrains.intellij.IntelliJPluginExtension
import org.jetbrains.intellij.Utils
import org.jetbrains.intellij.jbr.JbrResolver

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class RunPluginVerifierTask extends ConventionTask {
    private static final String BINTRAY_API_VERIFIER_VERSION_LATEST = "https://api.bintray.com/packages/jetbrains/intellij-plugin-service/intellij-plugin-verifier/versions/_latest"
    private static final String IDE_DOWNLOAD_URL = "https://data.services.jetbrains.com/products/download"
    public static final String CACHE_REDIRECTOR = 'https://cache-redirector.jetbrains.com'

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
    private List<Object> ideVersions = []
    private List<Object> localPaths = []
    private Object verifierVersion
    private Object distributionFile
    private Object verificationReportsDirectory
    private Object downloadDirectory
    private Object jbrVersion
    private Object runtimeDir
    private List<Object> externalPrefixes = []
    private Boolean teamCityOutputFormat = false
    private Object subsystemsToCheck

    /**
     * Returns a list of the {@link FailureLevel} values used for failing the task if any reported issue will match.
     *
     * @return verification failure level
     */
    @Input
    EnumSet<FailureLevel> getFailureLevel() {
        return failureLevel
    }

    /**
     * Sets a list of the {@link FailureLevel} values that will make the task if any reported issue will match.
     *
     * @param failureLevel EnumSet of {@link FailureLevel} values
     */
    void setFailureLevel(EnumSet<FailureLevel> failureLevel) {
        this.failureLevel = failureLevel
    }

    /**
     * Sets a list of the {@link FailureLevel} values that will make the task if any reported issue will match.
     *
     * @param failureLevel EnumSet of {@link FailureLevel} values
     */
    void failureLevel(EnumSet<FailureLevel> failureLevel) {
        this.failureLevel = failureLevel
    }

    /**
     * Sets the {@link FailureLevel} value that will make the task if any reported issue will match.
     *
     * @param failureLevel {@link FailureLevel} value
     */
    void setFailureLevel(FailureLevel failureLevel) {
        this.failureLevel = EnumSet.of(failureLevel)
    }

    /**
     * Returns a list of the specified IDE versions used for the verification.
     * By default, uses the plugin target IDE version.
     *
     * @return IDE versions list
     */
    @Input
    List<String> getIdeVersions() {
        return Utils.stringListInput(ideVersions)
    }

    /**
     * Sets a list of the IDE versions used for the verification.
     * Accepts list of {@link String} or {@link Closure}.
     *
     * @param ideVersions list of IDE versions
     */
    void setIdeVersions(List<Object> ideVersions) {
        this.ideVersions = ideVersions
    }

    /**
     * Sets a list of the IDE versions used for the verification.
     * Accepts list of {@link String} or {@link Closure}.
     *
     * @param ideVersions list of IDE versions
     */
    void ideVersions(List<Object> ideVersions) {
        this.ideVersions = ideVersions
    }

    /**
     * Returns a list of the paths to locally installed IDE distributions that should be used for verification.
     *
     * @return locally installed IDEs list
     */
    @InputFiles
    List<String> getLocalPaths() {
        return Utils.stringListInput(localPaths)
    }

    /**
     * Sets a list of the paths to locally installed IDE distributions that should be used for verification
     * in addition to those specified in {@link #ideVersions}.
     * Accepts list of {@link String} or {@link Closure}.
     *
     * @param localPaths list of paths
     */
    void setLocalPaths(List<Object> localPaths) {
        this.localPaths = localPaths
    }

    /**
     * Sets a list of the paths to locally installed IDE distributions that should be used for verification
     * in addition to those specified in {@link #ideVersions}.
     * Accepts list of {@link String} or {@link Closure}.
     *
     * @param localPaths list of paths
     */
    void localPaths(List<Object> localPaths) {
        this.localPaths = localPaths
    }

    /**
     * Returns the version of the IntelliJ Plugin Verifier that will be used.
     * By default, set to "latest".
     *
     * @return verifierVersion IntelliJ Plugin Verifier version
     */
    @Input
    @Optional
    String getVerifierVersion() {
        return Utils.stringInput(verifierVersion)
    }

    /**
     * Sets the version of the IntelliJ Plugin Verifier that will be used.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param verifierVersion IntelliJ Plugin Verifier version
     */
    void setVerifierVersion(Object verifierVersion) {
        this.verifierVersion = verifierVersion
    }

    /**
     * Sets the version of the IntelliJ Plugin Verifier that will be used.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param verifierVersion IntelliJ Plugin Verifier version
     */
    void verifierVersion(Object verifierVersion) {
        this.verifierVersion = verifierVersion
    }

    /**
     * Returns an instance of the distribution file generated with the build task.
     * If empty, task will be skipped.
     *
     * @return generated plugin artifact
     */
    @InputFile
    @SkipWhenEmpty
    File getDistributionFile() {
        def input = distributionFile instanceof Closure ? (distributionFile as Closure).call() : distributionFile
        return input != null ? project.file(input) : null
    }

    /**
     * Sets an instance of the distribution file generated with the build task.
     * Accepts {@link File} or {@link Closure}.
     *
     * @param distributionFile generated plugin artifact
     */
    void setDistributionFile(Object distributionFile) {
        this.distributionFile = distributionFile
    }

    /**
     * Sets an instance of the distribution file generated with the build task.
     * Accepts {@link File} or {@link Closure}.
     *
     * @param distributionFile generated plugin artifact
     */
    void distributionFile(Object distributionFile) {
        this.distributionFile = distributionFile
    }

    /**
     * Returns the path to directory where verification reports will be saved.
     * By default, set to ${project.buildDir}/reports/pluginVerifier.
     *
     * @return path to verification reports directory
     */
    @OutputDirectory
    @Optional
    String getVerificationReportsDirectory() {
        return Utils.stringInput(verificationReportsDirectory)
    }

    /**
     * Sets the path to directory where verification reports will be saved.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param verificationReportsDirectory path to verification reports directory
     */
    void setVerificationReportsDirectory(Object verificationReportsDirectory) {
        this.verificationReportsDirectory = verificationReportsDirectory
    }

    /**
     * Sets the path to directory where verification reports will be saved.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param verificationReportsDirectory path to verification reports directory
     */
    void verificationReportsDirectory(Object verificationReportsDirectory) {
        this.verificationReportsDirectory = verificationReportsDirectory
    }

    /**
     * Returns the path to directory where IDEs used for the verification will be downloaded.
     * By default, set to ${project.buildDir}/pluginVerifier.
     *
     * @return path to IDEs download directory
     */
    @Input
    @Optional
    String getDownloadDirectory() {
        return Utils.stringInput(downloadDirectory)
    }

    /**
     * Returns the path to directory where IDEs used for the verification will be downloaded.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param downloadDirectory path to IDEs download directory
     */
    void setDownloadDirectory(Object downloadDirectory) {
        this.downloadDirectory = downloadDirectory
    }

    /**
     * Returns the path to directory where IDEs used for the verification will be downloaded.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param downloadDirectory path to IDEs download directory
     */
    void downloadDirectory(Object downloadDirectory) {
        this.downloadDirectory = downloadDirectory
    }

    /**
     * Returns JBR version used by the IntelliJ Plugin Verifier.
     * If not passed, built-in JBR or current JVM will be used.
     *
     * @return JBR Version
     */
    @Input
    @Optional
    String getJbrVersion() {
        return Utils.stringInput(jbrVersion)
    }

    /**
     * Sets JBR version used by the IntelliJ Plugin Verifier, i.e. "11_0_2b159".
     * All JetBrains Java versions are available at BinTray: https://bintray.com/jetbrains/intellij-jbr
     * Accepts {@link String} or {@link Closure}.
     *
     * @param jbrVersion JBR version
     */
    void setJbrVersion(Object jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    /**
     * Sets JBR version used by the IntelliJ Plugin Verifier, i.e. "11_0_2b159".
     * All JetBrains Java versions are available at BinTray: https://bintray.com/jetbrains/intellij-jbr
     * Accepts {@link String} or {@link Closure}.
     *
     * @param jbrVersion JBR version
     */
    void jbrVersion(Object jbrVersion) {
        this.jbrVersion = jbrVersion
    }

    /**
     * Returns the path to directory containing JVM runtime.
     *
     * @return JVM runtime directory
     */
    @Input
    @Optional
    String getRuntimeDir() {
        return Utils.stringInput(runtimeDir)
    }

    /**
     * Sets the path to directory containing JVM runtime, overrides {@link #jbrVersion}.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param runtimeDir JVM runtime directory
     */
    void setRuntimeDir(Object runtimeDir) {
        this.runtimeDir = runtimeDir
    }

    /**
     * Sets the path to directory containing JVM runtime, overrides {@link #jbrVersion}.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param runtimeDir JVM runtime directory
     */
    void runtimeDir(Object runtimeDir) {
        this.runtimeDir = runtimeDir
    }

    /**
     * Returns the prefixes of classes from the external libraries.
     * The Plugin Verifier will not report 'No such class' for classes of these packages.
     *
     * @return list with external prefixes to ignore
     */
    @Input
    @Optional
    List<String> getExternalPrefixes() {
        return Utils.stringListInput(externalPrefixes)
    }

    /**
     * Sets the list of classes prefixes from the external libraries.
     * The Plugin Verifier will not report 'No such class' for classes of these packages.
     * Accepts list of {@link String} or {@link Closure}.
     *
     * @param externalPrefixes list of classes prefixes to ignore
     */
    void setExternalPrefixes(List<Object> externalPrefixes) {
        this.externalPrefixes = externalPrefixes
    }

    /**
     * Sets the list of classes prefixes from the external libraries.
     * The Plugin Verifier will not report 'No such class' for classes of these packages.
     * Accepts list of {@link String} or {@link Closure}.
     *
     * @param externalPrefixes list of classes prefixes to ignore
     */
    void externalPrefixes(List<Object> externalPrefixes) {
        this.externalPrefixes = externalPrefixes
    }

    /**
     * Returns a flag that controls the output format - if set to <code>true</code>, the TeamCity compatible output
     * will be returned to stdout.
     *
     * @return prints TeamCity compatible output
     */
    @Input
    @Optional
    Boolean getTeamCityOutputFormat() {
        return Utils.stringInput(teamCityOutputFormat)
    }

    /**
     * Sets a flag that controls the output format - if set to <code>true</code>, the TeamCity compatible output
     * will be returned to stdout.
     *
     * @param teamCityOutputFormat prints TeamCity compatible output
     */
    void setTeamCityOutputFormat(Boolean teamCityOutputFormat) {
        this.teamCityOutputFormat = teamCityOutputFormat
    }

    /**
     * Sets a flag that controls the output format - if set to <code>true</code>, the TeamCity compatible output
     * will be returned to stdout.
     *
     * @param teamCityOutputFormat prints TeamCity compatible output
     */
    void teamCityOutputFormat(Boolean teamCityOutputFormat) {
        this.teamCityOutputFormat = teamCityOutputFormat
    }

    /**
     * Returns which subsystems of IDE should be checked.
     *
     * @return subsystems to check
     */
    @Input
    @Optional
    String getSubsystemsToCheck() {
        return Utils.stringInput(subsystemsToCheck)
    }

    /**
     * Specifies which subsystems of IDE should be checked.
     * Available options: `all` (default), `android-only`, `without-android`.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param subsystemsToCheck subsystems of IDE should to check
     */
    void setSubsystemsToCheck(Object subsystemsToCheck) {
        this.subsystemsToCheck = subsystemsToCheck
    }

    /**
     * Specifies which subsystems of IDE should be checked.
     * Available options: `all` (default), `android-only`, `without-android`.
     * Accepts {@link String} or {@link Closure}.
     *
     * @param subsystemsToCheck subsystems of IDE should to check
     */
    void subsystemsToCheck(Object subsystemsToCheck) {
        this.subsystemsToCheck = subsystemsToCheck
    }

    /**
     * Runs the IntelliJ Plugin Verifier against the plugin artifact.
     * {@link String}
     */
    @TaskAction
    void runPluginVerifier() {
        def file = getDistributionFile()
        if (file == null || !file.exists()) {
            throw new IllegalStateException("Plugin file does not exist: $file")
        }

        def verifierPath = getVerifierPath()
        def verifierArgs = ["check-plugin"]
        verifierArgs += getOptions()
        verifierArgs += [file.canonicalPath]
        verifierArgs += getIdeVersions().collect { resolveIdePath(it) }
        verifierArgs += getLocalPaths()

        Utils.debug(this, "Distribution file: $file.canonicalPath")
        Utils.debug(this, "Verifier path: $verifierPath")

        new ByteArrayOutputStream().withStream { os ->
            project.javaexec {
                classpath = project.files(verifierPath)
                main = "com.jetbrains.pluginverifier.PluginVerifierMain"
                args = verifierArgs
                standardOutput = os
            }

            def output = os.toString()
            println output

            Utils.debug(this, "Current failure levels: ${FailureLevel.values().join(", ")}")
            for (FailureLevel level : FailureLevel.values()) {
                if (failureLevel.contains(level) && output.contains(level.testValue)) {
                    Utils.debug(this, "Failing task on $failureLevel failure level")
                    throw new GradleException(level.toString())
                }
            }
        }
    }

    /**
     * Fetches IntelliJ Plugin Verifier artifact from the {@link IntelliJPlugin#DEFAULT_INTELLIJ_PLUGIN_SERVICE}
     * repository and resolves the path to verifier-cli jar file.
     *
     * @return path to verifier-cli jar
     */
    @InputFile
    String getVerifierPath() {
        def repository = project.repositories.maven { it.url = IntelliJPlugin.DEFAULT_INTELLIJ_PLUGIN_SERVICE }
        try {
            def resolvedVerifierVersion = resolveVerifierVersion()
            Utils.debug(this, "Using Verifier in $resolvedVerifierVersion version")
            def dependency = project.dependencies.create("org.jetbrains.intellij.plugins:verifier-cli:$resolvedVerifierVersion:all@jar")
            def configuration = project.configurations.detachedConfiguration(dependency)
            return configuration.singleFile.absolutePath
        } catch (Exception e) {
            println e
            Utils.error(this, "Error when resolving Plugin Verifier path", e)
        }
        return project.repositories.remove(repository)
    }

    /**
     * Resolves the IDE type and version. If just version is provided, type is set to "IC".
     *
     * @param ideVersion IDE version. Can be "2020.2", "IC-2020.2", "202.1234.56"
     * @return path to the resolved IDE
     */
    @Nullable
    String resolveIdePath(String ideVersion) {
        Utils.debug(this, "Resolving IDE path for $ideVersion")
        def (String type, String version) = ideVersion.split("-", 2)

        if (!version) {
            Utils.debug(this, "IDE type not specified, setting type to IC")
            version = type
            type = "IC"
        }

        for (String buildType in ["release", "rc", "eap", "beta"]) {
            Utils.debug(project, "Downloading IDE '$type-$version' from $buildType channel to ${getDownloadDirectory()}")
            try {
                def dir = downloadIde(type, version, buildType)
                Utils.debug(project, "Resolved IDE '$type-$version' path: ${dir.absolutePath}")
                return dir.absolutePath
            } catch (IOException e) {
                Utils.debug(project, "Cannot download IDE '$type-$version' from $buildType channel. Trying another channel...", e)
            }
        }

        Utils.error(project, "Cannot download IDE '$type-$version'. Please verify provided version with the available versions: https://data.services.jetbrains.com/products/releases?code=$type&type=[release|rc|eap]")
        return null
    }

    /**
     * Downloads IDE from the {@link #IDE_DOWNLOAD_URL} service by the given parameters.
     *
     * @param type      IDE type, i.e. IC, PS
     * @param version   IDE version, i.e. 2020.2 or 203.1234.56
     * @param buildType release, rc, eap, beta
     * @return {@link File} instance pointing to the IDE directory
     */
    private File downloadIde(String type, String version, String buildType) {
        def name = "$type-$version"
        def ideDir = new File(getDownloadDirectory(), name)
        Utils.debug(this, "Downloaing IDE: $name")

        if (!ideDir.exists()) {
            def ideArchive = new File(getDownloadDirectory(), "${name}.tar.gz")
            def url = resolveIdeUrl(type, version, buildType)

            Utils.debug(this, "Downloaing IDE from $url")

            new DownloadAction(project).with {
                src(url)
                dest(ideArchive.absolutePath)
                tempAndMove(true)
                execute()
            }

            try {
                Utils.debug(this, "IDE downloaded, extracting...")
                Utils.untar(project, ideArchive, ideDir)
                def container = ideDir.listFiles().first()
                container.listFiles().each { it.renameTo("$ideDir/$it.name") }
                container.deleteDir()
            } finally {
                ideArchive.delete()
            }
            Utils.debug(this, "IDE extracted to $ideDir, archive removed")
        } else {
            Utils.debug(this, "IDE already available in $ideDir")
        }

        return ideDir
    }

    /**
     * Resolves direct IDE download URL provided by the JetBrains Data Services.
     * The URL created with {@link #IDE_DOWNLOAD_URL} contains HTTP redirection, which is supposed to be resolved.
     * Direct download URL is prepended with {@link #CACHE_REDIRECTOR} host for providing caching mechanism.
     *
     * @param type      IDE type, i.e. IC, PS
     * @param version   IDE version, i.e. 2020.2 or 203.1234.56
     * @param buildType release, rc, eap, beta
     * @return direct download URL prepended with {@link #CACHE_REDIRECTOR} host
     */
    private String resolveIdeUrl(String type, String version, String buildType) {
        def url = new URIBuilder(IDE_DOWNLOAD_URL)
                .addParameter("code", type)
                .addParameter("platform", "linux")
                .addParameter("type", buildType)
                .addParameter(versionParameterName(version), version)
                .toString()
        Utils.debug(this, "Resolving direct IDE download URL for: $url")

        HttpURLConnection connection = null

        try {
            connection = new URL(url).openConnection() as HttpURLConnection
            connection.setInstanceFollowRedirects(false)
            connection.getInputStream()

            if (connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_PERM || connection.getResponseCode() == HttpURLConnection.HTTP_MOVED_TEMP) {
                def redirectUrl = new URL(connection.getHeaderField("Location"))
                url = "$CACHE_REDIRECTOR/${redirectUrl.host}${redirectUrl.file}"
                Utils.debug(this, "Resolved IDE download URL: $url")
            } else {
                Utils.debug(this, "IDE download URL has no redirection provided, skipping.")
            }
        } catch (Exception e) {
            Utils.error(this, "Cannot resolve direct download URL for: $url", e)
        } finally {
            if (connection != null) {
                connection.disconnect()
            }
        }

        return url
    }

    /**
     * Resolves Plugin Verifier version.
     * If set to {@link #VERIFIER_VERSION_LATEST}, there's request to {@link #BINTRAY_API_VERIFIER_VERSION_LATEST}
     * performed for the latest available verifier version.
     *
     * @return Plugin Verifier version
     */
    private String resolveVerifierVersion() {
        if (getVerifierVersion() != VERIFIER_VERSION_LATEST) {
            return getVerifierVersion()
        }

        Utils.debug(this, "Resolving Verifier version with BinTray")
        def url = new URL(BINTRAY_API_VERIFIER_VERSION_LATEST)
        return new JsonSlurper().parse(url)["name"]
    }

    /**
     * Resolves the Java Runtime directory. `runtimeDir` property is used if provided with the task configuration.
     * Otherwise, `jbrVersion` is used for resolving the JBR. If it's not set, or it's impossible to resolve valid
     * version, built-in JBR will be used.
     * As a last fallback, current JVM will be used.
     *
     * @return path to the Java Runtime directory
     */
    private String resolveRuntimeDir() {
        if (runtimeDir != null) {
            Utils.debug(this, "Runtime specified with propeties: ${getRuntimeDir()}")
            return getRuntimeDir()
        }

        def jbrResolver = new JbrResolver(project, this)
        if (jbrVersion != null) {
            def jbr = jbrResolver.resolve(getJbrVersion())
            if (jbr != null) {
                Utils.debug(this, "Runtime specified with JBR Version property: ${getJbrVersion()}")
                return jbr.javaHome
            }
            Utils.warn(this, "Cannot resolve JBR ${getJbrVersion()}. Falling back to built-in JBR.")
        }

        def extension = project.extensions.findByType(IntelliJPluginExtension)
        def jbrPath = OperatingSystem.current().isMacOsX() ? "jbr/Contents/Home" : "jbr"

        def builtinJbrVersion = Utils.getBuiltinJbrVersion(Utils.ideSdkDirectory(project, extension))
        if (builtinJbrVersion != null) {
            def builtinJbr = jbrResolver.resolve(builtinJbrVersion)
            if (builtinJbr != null) {
                def javaHome = new File(builtinJbr.javaHome, jbrPath)
                if (javaHome.exists()) {
                    Utils.debug(this, "Using built-in JBR: $javaHome")
                    return javaHome
                }
            }
            Utils.warn(this, "Cannot resolve builtin JBR $builtinJbrVersion. Falling local Java.")
        }

        if (extension.alternativeIdePath) {
            def javaHome = new File(Utils.ideaDir(extension.alternativeIdePath), jbrPath)
            if (javaHome.exists()) {
                Utils.debug(this, "Using built-in JBR from alternativeIdePath: $extension.alternativeIdePath")
                return javaHome
            }
            Utils.warn(this, "Cannot resolve JBR at $javaHome. Falling back to current JVM.")
        }

        Utils.debug(this, "Using current JVM: ${Jvm.current().getJavaHome()}")
        return Jvm.current().getJavaHome()
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with available CLI options
     */
    private List<String> getOptions() {
        def args = [
                "-verification-reports-dir", getVerificationReportsDirectory(),
                "-runtime-dir", resolveRuntimeDir()
        ]

        if (!externalPrefixes.empty) {
            args += ["-external-prefixes", getExternalPrefixes().join(":")]
        }
        if (teamCityOutputFormat) {
            args += ["-team-city"]
        }
        if (subsystemsToCheck != null) {
            args += ["-subsystems-to-check", getSubsystemsToCheck()]
        }

        return args
    }

    /**
     * Retrieve the Plugin Verifier home directory used for storing downloaded IDEs.
     * Following home directory resolving method is taken directly from the Plugin Verifier to keep the compatibility.
     *
     * @return Plugin Verifier home directory
     */
    static Path verifierHomeDirectory() {
        def verifierHomeDir = System.getProperty("plugin.verifier.home.dir")
        if (verifierHomeDir != null) {
            Paths.get(verifierHomeDir)
        } else {
            def userHome = System.getProperty("user.home")
            if (userHome != null) {
                Paths.get(userHome, ".pluginVerifier")
            } else {
                FileUtils.getTempDirectory().toPath().resolve(".pluginVerifier")
            }
        }
    }

    /**
     * Provides target directory used for storing downloaded IDEs.
     * Path is compatible with the Plugin Verifier approach.
     *
     * @return directory for downloaded IDEs
     */
    static Path ideDownloadDirectory() {
        def path = verifierHomeDirectory().resolve("ides")
        Files.createDirectories(path)
        return path
    }

    /**
     * Obtains version parameter name used for downloading IDE artifact.
     * Examples:
     * - 202.7660.26 -> build
     * - 2020.2, 16.1 -> majorVersion
     * - 2020.2.3 -> version
     *
     * @param version current version
     * @return version parameter name
     */
    static String versionParameterName(String version) {
        if (version.matches("\\d{3}\\.")) {
            return "build"
        }
        if (version.matches("(\\d{2}){1,2}.\\d")) {
            return "majorVersion"
        }
        return "version"
    }
}
