// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.utils.*
import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.newInstance
import org.gradle.kotlin.dsl.the
import org.gradle.process.ExecOperations
import org.gradle.process.internal.ExecException
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.AndroidStudio
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Locations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.base.JetBrainsRuntimeAware
import org.jetbrains.intellij.platform.gradle.tasks.base.PluginVerifierAware
import org.jetbrains.intellij.platform.gradle.utils.ArchiveUtils
import org.jetbrains.intellij.platform.gradle.utils.DependenciesDownloader
import org.jetbrains.intellij.platform.gradle.utils.LatestVersionResolver
import org.jetbrains.intellij.platform.gradle.utils.ivyRepository
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import javax.inject.Inject
import kotlin.io.path.pathString

/**
 * Runs the [IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) tool to check the binary compatibility with specified IDE builds (see also [Verifying Plugin Compatibility](https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html)).
 *
 * Plugin Verifier DSL `runPluginVerifier { ... }` allows to define the list of IDEs used for the verification, as well as explicit tool version and any of the available [options](https://github.com/JetBrains/intellij-plugin-verifier#common-options) by proxifying them to the Verifier CLI.
 *
 * For more details, examples or issues reporting, go to the [IntelliJ Plugin Verifier](https://github.com/JetBrains/intellij-plugin-verifier) repository.
 *
 * To run Plugin Verifier in [`-offline`](https://github.com/JetBrains/intellij-plugin-verifier/pull/58) mode, set the Gradle [`offline` start parameter](https://docs.gradle.org/current/javadoc/org/gradle/StartParameter.html#setOffline-boolean-).
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html">Verifying Plugin Compatibility</a>
 * @see <a href="https://github.com/JetBrains/intellij-plugin-verifier">IntelliJ Plugin Verifier</a>
 */
@Deprecated(message = "CHECK")
@UntrackedTask(because = "Should always run Plugin Verifier")
abstract class RunPluginVerifierTask @Inject constructor(
    private val objectFactory: ObjectFactory,
    private val execOperations: ExecOperations,
) : DefaultTask(), JetBrainsRuntimeAware, PluginVerifierAware {

    /**
     * Defines the verification level at which the task should fail if any reported issue matches.
     * Can be set as [FailureLevel] enum or [EnumSet<FailureLevel>].
     *
     * Default value: [FailureLevel.COMPATIBILITY_PROBLEMS]
     */
    @get:Input
    abstract val failureLevel: ListProperty<FailureLevel>

    /**
     * A fallback file with a list of the releases generated with [ListProductsReleasesTask].
     * Used if [ideVersions] is not provided.
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val productsReleasesFile: RegularFileProperty

    /**
     * IDEs to check, in `intellij.version` format, i.e.: `["IC-2019.3.5", "PS-2019.3.2"]`.
     * Check the available build versions on [IntelliJ Platform Builds list](https://jb.gg/intellij-platform-builds-list).
     *
     * Default value: output of the [ListProductsReleasesTask] task
     */
    @get:Input
    @get:Optional
    abstract val ideVersions: ListProperty<String>

    /**
     * A list of the paths to locally installed IDE distributions that should be used for verification in addition to those specified in [ideVersions].
     */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val idePaths: ConfigurableFileCollection

//    /**
//     * Returns the version of the IntelliJ Plugin Verifier that will be used.
//     *
//     * Default value: `latest`
//     */
//    @get:Input
//    @get:Optional
//    abstract val verifierVersion: Property<String>

//    /**
//     * Local path to the IntelliJ Plugin Verifier that will be used.
//     * If provided, [verifierVersion] is ignored.
//     *
//     * Default value: path to the JAR file resolved using the [verifierVersion] property
//     */
//    @get:Input
//    @get:Optional
//    abstract val verifierPath: Property<String>

    /**
     * Free arguments passed to the IntelliJ Plugin Verifier exactly as specified.
     *
     * They can be used in addition to the arguments that are provided by dedicated options.
     */
    @get:Input
    @get:Optional
    abstract val freeArgs: ListProperty<String>

    /**
     * JAR or ZIP file of the plugin to verify.
     * If empty, the task will be skipped.
     *
     * Default value: output of the `buildPlugin` task
     */
    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val distributionFile: RegularFileProperty

    /**
     * The path to the directory where verification reports will be saved.
     *
     * Default value: `${project.buildDir}/reports/pluginVerifier`
     */
    @get:OutputDirectory
    @get:Optional
    abstract val verificationReportsDirectory: DirectoryProperty

    /**
     * The output formats of the verification reports.
     *
     * Accepted values:
     * - `plain` for console output
     * - `html`
     * ` `markdown`
     *
     * Default value: [VerificationReportsFormats.PLAIN], [VerificationReportsFormats.HTML]
     */
    @get:Input
    @get:Optional
    abstract val verificationReportsFormats: ListProperty<VerificationReportsFormats>

    /**
     * The path to the directory where IDEs used for the verification will be downloaded.
     *
     * Default value: `System.getProperty("plugin.verifier.home.dir")/ides`, `System.getenv("XDG_CACHE_HOME")/pluginVerifier/ides`,
     * `System.getProperty("user.home")/.cache/pluginVerifier/ides` or system temporary directory.
     */
    @get:InputDirectory
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val downloadDirectory: DirectoryProperty

    @get:Internal
    @Deprecated("Rely only on downloadDirectory")
    abstract val downloadPath: Property<Path>

    /**
     * The list of classes prefixes from the external libraries.
     * The Plugin Verifier will not report `No such class` for classes of these packages.
     */
    @get:Input
    @get:Optional
    abstract val externalPrefixes: ListProperty<String>

    /**
     * A flag that controls the output format - if set to `true`, the TeamCity compatible output will be returned to stdout.
     *
     * Default value: `false`
     */
    @get:Input
    @get:Optional
    abstract val teamCityOutputFormat: Property<Boolean>

    /**
     * Specifies which subsystems of IDE should be checked.
     *
     * Default value: `all`
     *
     * Acceptable values:**
     * - `all`
     * - `android-only`
     * - `without-android`
     */
    @get:Input
    @get:Optional
    abstract val subsystemsToCheck: Property<String>

    /**
     * A file that contains a list of problems that will be ignored in a report.
     */
    @get:InputFiles
    @get:Optional
    abstract val ignoredProblemsFile: RegularFileProperty

    @get:Internal
    abstract val offline: Property<Boolean>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IDE builds."
    }

    /**
     * Runs the IntelliJ Plugin Verifier against the plugin artifact.
     */
    @TaskAction
    fun runPluginVerifier() {
        val file = distributionFile.orNull?.asPath
        if (file == null || !file.exists()) {
            throw IllegalStateException("Plugin file does not exist: $file")
        }

        val paths = getPaths()
        if (paths.isEmpty()) {
            throw GradleException("'ideVersions' and 'localPaths' properties should not be empty")
        }

        val verifierArgs = listOf("check-plugin") + getOptions() + file.toString() + paths

        debug(context, "Distribution file: $file")
        debug(context, "Verifier path: $pluginVerifierExecutable")

        ByteArrayOutputStream().use { os ->
            try {
                execOperations.javaexec {
                    classpath = objectFactory.fileCollection().from(pluginVerifierExecutable)
                    mainClass.set("com.jetbrains.pluginverifier.PluginVerifierMain")
                    args = verifierArgs
                    standardOutput = TeeOutputStream(System.out, os)
                }
            } catch (e: ExecException) {
                error(context, "Error during Plugin Verifier CLI execution:\n$os")
                throw e
            }

            debug(context, "Current failure levels: ${FailureLevel.values().joinToString(", ")}")
            FailureLevel.values().forEach { level ->
                if (failureLevel.get().contains(level) && os.toString().contains(level.sectionHeading)) {
                    debug(context, "Failing task on '$failureLevel' failure level")
                    throw GradleException(
                        "$level: ${level.message} Check Plugin Verifier report for more details.\n" +
                                "Incompatible API Changes: https://jb.gg/intellij-api-changes"
                    )
                }
            }
        }
    }

    private fun getPaths(): List<String> {
        val dependenciesDownloader = objectFactory.newInstance<DependenciesDownloader>(offline.get())
        val archiveUtils = objectFactory.newInstance<ArchiveUtils>()

        val localPaths = idePaths.map { it.toPath() }
        val resolvedPaths = ideVersions.map { ideVersions ->
            ideVersions
                .ifEmpty {
                    when {
                        idePaths.isEmpty -> productsReleasesFile.asPath.readLines()
                        else -> null
                    }
                }
                .orEmpty()
                .map { ideVersion ->
                    resolveIdePath(ideVersion, downloadPath.get(), context) { type, version, buildType ->
                        val name = "$type-$version"
                        val ideDir = downloadPath.get().resolve(name)
                        info(context, "Downloading IDE '$name' to: $ideDir")

                        val url = resolveIdeUrl(type, version, buildType, context)
                        val dependencyVersion = listOf(type.toString(), version, buildType).filterNot(String::isNullOrEmpty).joinToString("-")
                        val group = when (type) {
                            AndroidStudio -> "com.android"
                            else -> "com.jetbrains"
                        }
                        debug(context, "Downloading IDE from $url")

                        try {
                            val ideArchive = dependenciesDownloader.downloadFromRepository(context, {
                                create(
                                    group = group,
                                    name = "ides",
                                    version = dependencyVersion,
                                    ext = "tar.gz",
                                )
                            }, {
                                ivyRepository(url)
                            }).first()

                            debug(context, "IDE downloaded, extracting...")
                            archiveUtils.extract(ideArchive.toPath(), ideDir, context) // FIXME ideArchive.toPath()
                            ideDir.listFiles().let { files ->
                                files.filter { it.isDirectory }.forEach { container ->
                                    container.listFiles().forEach { file ->
                                        Files.move(file, ideDir.resolve(file.simpleName))
                                    }
                                    container.forceRemoveDirectory()
                                }
                            }
                        } catch (e: Exception) {
                            warn(context, "Cannot download '$type-$version' from '$buildType' channel: $url", e)
                        }

                        debug(context, "IDE extracted to: $ideDir")
                        ideDir
                    }
                }
        }.get()

        return (resolvedPaths + localPaths).map { it.pathString }
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with all available CLI options
     */
    private fun getOptions(): List<String> {
        val args = mutableListOf(
            "-verification-reports-dir", verificationReportsDirectory.asPath.pathString,
            "-runtime-dir", jetbrainsRuntimeDirectory.asPath.pathString,
        )

        externalPrefixes.get().takeIf { it.isNotEmpty() }?.let {
            args.add("-external-prefixes")
            args.add(it.joinToString(":"))
        }
        if (teamCityOutputFormat.get()) {
            args.add("-team-city")
        }
        if (subsystemsToCheck.orNull != null) {
            args.add("-subsystems-to-check")
            args.add(subsystemsToCheck.get())
        }
        if (offline.get()) {
            args.add("-offline")
        }

        args.add("-verification-reports-formats")
        args.add(verificationReportsFormats.get().joinToString(","))

        if (ignoredProblemsFile.orNull != null) {
            args.add("-ignored-problems")
            args.add(ignoredProblemsFile.asPath.pathString)
        }

        freeArgs.orNull?.let {
            args.addAll(it)
        }

        return args
    }

    /**
     * Resolves the IDE type and version. If only `version` is provided, `type` is set to "IC".
     *
     * @param ideVersion IDE version. Can be "2020.2", "IC-2020.2", "202.1234.56"
     * @return path to the resolved IDE
     */
    private fun resolveIdePath(
        ideVersion: String,
        downloadPath: Path,
        context: String?,
        block: (type: IntelliJPlatformType, version: String, buildType: String) -> Path,
    ): Path {
        debug(context, "Resolving IDE path for: $ideVersion")
        val (version, code) = ideVersion.trim().split('-', limit = 2).reversed() + null
        val type = when (code) {
            null -> run {
                debug(context, "IDE type not specified, setting type to $IntellijIdeaCommunity")
                IntellijIdeaCommunity
            }

            else -> IntelliJPlatformType.fromCode(code)
        }

        val name = "$type-$version"
        val ideDirPath = downloadPath.resolve(name)

        if (ideDirPath.exists()) {
            debug(context, "IDE already available in: $ideDirPath")
            return ideDirPath
        }

        val buildTypes = when (type) {
            AndroidStudio -> listOf("")
            else -> listOf("release", "rc", "eap", "beta")
        }

        buildTypes.forEach { buildType ->
            debug(context, "Downloading IDE '$type-$version' from '$buildType' channel to: $downloadPath")
            try {
                return block(type, version!!, buildType).also {
                    debug(context, "Resolved IDE '$type-$version' path: $it")
                }
            } catch (e: IOException) {
                debug(context, "Cannot download IDE '$type-$version' from '$buildType' channel. Trying another channel...", e)
            }
        }

        throw GradleException("IDE '$ideVersion' cannot be downloaded. Please verify the specified IDE version against the products available for testing: https://jb.gg/intellij-platform-builds-list")
    }

    /**
     * Resolves direct IDE download URL provided by the JetBrains Data Services.
     * The URL created with [IDEA_DOWNLOAD_URL] contains HTTP redirection, which is supposed to be resolved.
     * Direct download URL is prepended with [CACHE_REDIRECTOR] host for providing a caching mechanism.
     *
     * @param type IDE type, i.e. IC, PS
     * @param version IDE version, i.e. 2020.2 or 203.1234.56
     * @param buildType release, rc, eap, beta
     * @return direct download URL prepended with [CACHE_REDIRECTOR] host
     */
    private fun resolveIdeUrl(type: IntelliJPlatformType, version: String, buildType: String, context: String?): String {
        val isAndroidStudio = type == AndroidStudio
        val url = when {
            isAndroidStudio -> "$ANDROID_STUDIO_DOWNLOAD_URL/$version/android-studio-$version-linux.tar.gz"
            else -> "$IDEA_DOWNLOAD_URL?code=$type&platform=linux&type=$buildType&${versionParameterName(version)}=$version"
        }

        debug(context, "Resolving direct IDE download URL for: $url")

        var connection: HttpURLConnection? = null

        try {
            with(URL(url).openConnection() as HttpURLConnection) {
                connection = this
                instanceFollowRedirects = false
                inputStream.use {
                    if ((responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP) && !isAndroidStudio) {
                        val redirectUrl = URL(getHeaderField("Location"))
                        disconnect()
                        debug(context, "Resolved IDE download URL: $url")
                        return "${Locations.CACHE_REDIRECTOR}/${redirectUrl.host}${redirectUrl.file}"
                    } else {
                        debug(context, "IDE download URL has no redirection provided. Skipping")
                    }
                }
            }
        } catch (e: Exception) {
            info(context, "Cannot resolve direct download URL for: $url")
            debug(context, "Download exception stacktrace:", e)
            throw e
        } finally {
            connection?.disconnect()
        }

        return url
    }

    /**
     * Obtains the version parameter name used for downloading IDE artifact.
     *
     * Examples:
     * - 202.7660.26 -> build
     * - 2020.1, 2020.2.3 -> version
     *
     * @param version current version
     * @return version parameter name
     */
    private fun versionParameterName(version: String) = when {
        version.matches("\\d{3}(\\.\\d+)+".toRegex()) -> "build"
        else -> "version"
    }

    companion object {
        private const val METADATA_URL = "${Locations.PLUGIN_VERIFIER_REPOSITORY}/org/jetbrains/intellij/plugins/verifier-cli/maven-metadata.xml"
        private const val IDEA_DOWNLOAD_URL = "https://data.services.jetbrains.com/products/download"
        private const val ANDROID_STUDIO_DOWNLOAD_URL = "https://redirector.gvt1.com/edgedl/android/studio/ide-zips"

        fun resolveLatestVersion() = LatestVersionResolver.fromMaven("Plugin Verifier", METADATA_URL)

        fun register(project: Project) =
            project.registerTask<RunPluginVerifierTask>(Tasks.RUN_PLUGIN_VERIFIER) {
                val listProductsReleasesTaskProvider = project.tasks.named<ListProductsReleasesTask>(Tasks.LIST_PRODUCTS_RELEASES)
                val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)
                val userHomeProvider = project.providers.systemProperty("user.home")
                val extension = project.the<IntelliJPlatformExtension>()

                downloadDirectory.convention(extension.pluginVerifier.downloadDirectory)
                downloadPath.convention(userHomeProvider.map {
                    val userHomePath = Path.of(it)
                    with(downloadDirectory.asPath.pathString) {
                        when {
                            startsWith("~/") -> userHomePath.resolve(removePrefix("~/"))
                            equals("~") -> userHomePath
                            else -> Path.of(this)
                        }
                    }
                })
                failureLevel.convention(extension.pluginVerifier.failureLevel)
                distributionFile.convention(buildPluginTaskProvider.flatMap { it.archiveFile })
                verificationReportsDirectory.convention(extension.pluginVerifier.verificationReportsDirectory)
                verificationReportsFormats.convention(extension.pluginVerifier.verificationReportsFormats)
                externalPrefixes.convention(extension.pluginVerifier.externalPrefixes)
                teamCityOutputFormat.convention(extension.pluginVerifier.teamCityOutputFormat)
                subsystemsToCheck.convention(extension.pluginVerifier.subsystemsToCheck)
                productsReleasesFile.convention(listProductsReleasesTaskProvider.flatMap {
                    it.outputFile
                })
                ignoredProblemsFile.convention(extension.pluginVerifier.ignoredProblemsFile)
                offline.convention(project.gradle.startParameter.isOffline)

                dependsOn(Tasks.BUILD_PLUGIN)
                dependsOn(Tasks.VERIFY_PLUGIN)
                dependsOn(Tasks.LIST_PRODUCTS_RELEASES)

                val isIdeVersionsEmpty = ideVersions.map {
                    it.isEmpty() && idePaths.isEmpty
                }

                listProductsReleasesTaskProvider.configure {
                    onlyIf { isIdeVersionsEmpty.get() }
                }
            }
    }

    @Suppress("unused")
    enum class FailureLevel(val sectionHeading: String, val message: String) {
        COMPATIBILITY_WARNINGS(
            "Compatibility warnings",
            "Compatibility warnings detected against the specified IDE version."
        ),
        COMPATIBILITY_PROBLEMS(
            "Compatibility problems",
            "Compatibility problems detected against the specified IDE version."
        ),
        DEPRECATED_API_USAGES(
            "Deprecated API usages",
            "Plugin uses API marked as deprecated (@Deprecated)."
        ),
        SCHEDULED_FOR_REMOVAL_API_USAGES(
            /* # usage(s) of */"scheduled for removal API",
            "Plugin uses API marked as scheduled for removal (ApiStatus.@ScheduledForRemoval)."
        ),
        EXPERIMENTAL_API_USAGES(
            "Experimental API usages",
            "Plugin uses API marked as experimental (ApiStatus.@Experimental)."
        ),
        INTERNAL_API_USAGES(
            "Internal API usages",
            "Plugin uses API marked as internal (ApiStatus.@get:Internal)."
        ),
        OVERRIDE_ONLY_API_USAGES(
            "Override-only API usages",
            "Override-only API is used incorrectly (ApiStatus.@OverrideOnly)."
        ),
        NON_EXTENDABLE_API_USAGES(
            "Non-extendable API usages",
            "Non-extendable API is used incorrectly (ApiStatus.@NonExtendable)."
        ),
        PLUGIN_STRUCTURE_WARNINGS(
            "Plugin structure warnings",
            "The structure of the plugin is not valid."
        ),
        MISSING_DEPENDENCIES(
            "Missing dependencies",
            "Plugin has some dependencies missing."
        ),
        INVALID_PLUGIN(
            "The following files specified for the verification are not valid plugins",
            "Provided plugin artifact is not valid."
        ),
        NOT_DYNAMIC(
            "Plugin cannot be loaded/unloaded without IDE restart",
            "Plugin cannot be loaded/unloaded without IDE restart."
        );

        companion object {
            @JvmField
            val ALL: EnumSet<FailureLevel> = EnumSet.allOf(FailureLevel::class.java)

            @JvmField
            val NONE: EnumSet<FailureLevel> = EnumSet.noneOf(FailureLevel::class.java)
        }
    }

    @Suppress("unused")
    enum class VerificationReportsFormats {
        PLAIN,
        HTML,
        MARKDOWN;

        companion object {
            @JvmField
            val ALL: EnumSet<VerificationReportsFormats> = EnumSet.allOf(VerificationReportsFormats::class.java)

            @JvmField
            val NONE: EnumSet<VerificationReportsFormats> = EnumSet.noneOf(VerificationReportsFormats::class.java)
        }

        override fun toString() = name.lowercase()
    }
}
