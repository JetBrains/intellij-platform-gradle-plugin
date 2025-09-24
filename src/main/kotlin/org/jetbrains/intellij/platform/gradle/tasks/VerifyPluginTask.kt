// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.problems.Severity
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.Problems
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.reportError
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginVerifierAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RuntimeAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.io.ByteArrayOutputStream
import java.util.*
import javax.inject.Inject
import kotlin.io.path.exists
import org.gradle.api.problems.Problems as GradleProblems

/**
 * Runs the IntelliJ Plugin Verifier CLI tool to check compatibility with specified IDE builds.
 *
 * @see IntelliJPlatformExtension.PluginVerification
 * @see <a href="https://github.com/JetBrains/intellij-plugin-verifier">IntelliJ Plugin Verifier</a>
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html">Verifying Plugin Compatibility</a>
 *
 */
// TODO: Use Reporting for handling verification report output? See: https://docs.gradle.org/current/dsl/org.gradle.api.reporting.Reporting.html
// TODO: Parallel run? https://docs.gradle.org/current/userguide/worker_api.html#converting_to_worker_api
@Suppress("UnstableApiUsage")
@UntrackedTask(because = "Should always run")
abstract class VerifyPluginTask : JavaExec(), RuntimeAware, PluginVerifierAware {
    /**
     * Provides access to Gradle's Problems API for structured issue reporting
     *
     * The Problems API enables standardized error and warning reporting with rich context information,
     * including file locations, documentation links, and suggested solutions.
     *
     * @see [org.gradle.api.problems.Problems]
     * @see [org.gradle.api.problems.ProblemReporter]
     * @since Gradle 8.6
     */
    @get:Inject
    abstract val problems: GradleProblems

    /**
     * Holds a reference to IntelliJ Platform IDEs which will be used by the IntelliJ Plugin Verifier CLI tool for verification.
     *
     * The list of IDEs is controlled with the [IntelliJPlatformExtension.PluginVerification.Ides] extension.
     *
     * @see [IntelliJPlatformExtension.PluginVerification.Ides]
     */
    @get:Classpath
    abstract val ides: ConfigurableFileCollection

    /**
     * Input ZIP archive file of the plugin to verify.
     * If empty, the task will be skipped.
     *
     * Default value: [BuildPluginTask.archiveFile]
     */
    @get:InputFile
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveFile: RegularFileProperty

    /**
     * The list of class prefixes from the external libraries.
     * The Plugin Verifier will not report `No such class` for classes of these packages.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.externalPrefixes]
     *
     * @see IntelliJPlatformExtension.PluginVerification.externalPrefixes
     */
    @get:Input
    @get:Optional
    abstract val externalPrefixes: ListProperty<String>

    /**
     * Defines the verification level at which the task should fail if any reported issue matches.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.failureLevel]
     *
     * @see FailureLevel
     * @see IntelliJPlatformExtension.PluginVerification.failureLevel
     */
    @get:Input
    abstract val failureLevel: ListProperty<FailureLevel>

    /**
     * The list of free arguments is passed directly to the IntelliJ Plugin Verifier CLI tool.
     *
     * They can be used in addition to the arguments that are provided by dedicated options.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.freeArgs]
     *
     * @see IntelliJPlatformExtension.PluginVerification.freeArgs
     */
    @get:Input
    @get:Optional
    abstract val freeArgs: ListProperty<String>

    /**
     * A file that contains a list of problems that will be ignored in a report.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.ignoredProblemsFile]
     *
     * @see IntelliJPlatformExtension.PluginVerification.ignoredProblemsFile
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val ignoredProblemsFile: RegularFileProperty

    /**
     * Determines if the operation is running in offline mode.
     *
     * Default value: [org.gradle.StartParameter.offline]
     *
     * @see org.gradle.StartParameter
     * @see <a href="https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_execution_options">Command Line Execution options</a>
     */
    @get:Internal
    abstract val offline: Property<Boolean>

    /**
     * Specifies which subsystems of IDE should be checked.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.subsystemsToCheck]
     *
     * @see Subsystems
     * @see IntelliJPlatformExtension.PluginVerification.subsystemsToCheck
     */
    @get:Input
    @get:Optional
    abstract val subsystemsToCheck: Property<Subsystems>

    /**
     * A flag that controls the output format - if set to `true`, the TeamCity compatible output will be returned to stdout.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.teamCityOutputFormat]
     *
     * @see IntelliJPlatformExtension.PluginVerification.teamCityOutputFormat
     */
    @get:Input
    @get:Optional
    abstract val teamCityOutputFormat: Property<Boolean>

    /**
     * The path to the directory where verification reports will be saved.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.verificationReportsDirectory]
     *
     * @see IntelliJPlatformExtension.PluginVerification.verificationReportsDirectory
     */
    @get:OutputDirectory
    @get:Optional
    abstract val verificationReportsDirectory: DirectoryProperty

    /**
     * The output formats of the verification reports.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.verificationReportsFormats]
     *
     * @see VerificationReportsFormats
     * @see IntelliJPlatformExtension.PluginVerification.verificationReportsFormats
     */
    @get:Input
    @get:Optional
    abstract val verificationReportsFormats: ListProperty<VerificationReportsFormats>

    /**
     * The default path where the output report of the Problems API will be.
     *
     * Default value: [org.gradle.api.file.ProjectLayout.getBuildDirectory]/reports/problems/problems-report.html
     *
     * @see Problems
     */
    @get:Internal
    abstract val problemsReportFile: RegularFileProperty

    private val problemsReportUrl get() = ConsoleRenderer().asClickableFileUrl(problemsReportFile.get().asFile)

    private val log = Logger(javaClass)

    /**
     * Runs the IntelliJ Plugin Verifier against the plugin artifact.
     */
    @TaskAction
    override fun exec() {
        val file = archiveFile.orNull?.asPath
        if (file == null || !file.exists()) {
            throw problems.reporter.reportError(
                IllegalStateException("Plugin file $file does not exist"),
                Problems.VerifyPlugin.InvalidPlugin,
                problemsReportUrl,
            ) {
                fileLocation(file?.toString() ?: "")
                details("This happened because the plugin you're verifying could not be found")
            }
        }

        log.debug("Distribution file: $file")

        val executable = pluginVerifierExecutable.orNull?.asPath
            ?: throw problems.reporter.reportError(
                GradleException("No IntelliJ Plugin Verifier executable found"),
                Problems.VerifyPlugin.InvalidPluginVerifier,
                problemsReportUrl,
            ) {
                documentedAt("https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification")
                solution("Please ensure the `pluginVerifier()` entry is present in the project dependencies section or `intellijPlatform.pluginVerification.cliPath` extension property is set")
            }

        log.debug("Verifier path: $executable")

        classpath = objectFactory.fileCollection().from(executable)

        with(ides) {
            if (isEmpty) {
                throw problems.reporter.reportError(
                    GradleException("No IDE resolved for verification with the IntelliJ Plugin Verifier"),
                    Problems.VerifyPlugin.InvalidIDEs,
                    problemsReportUrl,
                ) {
                    documentedAt("https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification-ides")
                    solution("Please ensure the `intellijPlatform.pluginVerification.ides` extension block is configured along with the `defaultRepositories()` (or at least `localPlatformArtifacts()`) entry in the repositories section")
                }
            }
            args(
                listOf("check-plugin") + getOptions() + file.safePathString + map {
                    when {
                        it.isDirectory -> it.absolutePath
                        else -> it.readText()
                    }
                },
            )
        }

        ByteArrayOutputStream().use { os ->
            standardOutput = TeeOutputStream(System.out, os)
            super.exec()
            verifyOutput(os.toString())
        }
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return An array with all available CLI options
     */
    private fun getOptions(): List<String> {
        val args = mutableListOf(
            "-verification-reports-dir", verificationReportsDirectory.asPath.safePathString,
            "-runtime-dir", runtimeDirectory.asPath.safePathString,
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
            args.add(subsystemsToCheck.get().toString())
        }
        if (offline.get()) {
            args.add("-offline")
        }

        // TODO check PV version
        args.add("-verification-reports-formats")
        args.add(verificationReportsFormats.get().joinToString(","))

        if (ignoredProblemsFile.orNull != null) {
            args.add("-ignored-problems")
            args.add(ignoredProblemsFile.asPath.safePathString)
        }

        freeArgs.orNull?.let {
            args.addAll(it)
        }

        return args
    }

    /**
     * @throws GradleException
     */
    @Throws(GradleException::class)
    private fun verifyOutput(output: String) {
        val failureLevels = failureLevel.get()
        log.debug("Current failure levels: ${failureLevels.joinToString(", ")}")

        val invalidFilesMessage = "The following files specified for the verification are not valid plugins:"
        if (output.contains(invalidFilesMessage)) {
            val errorMessage = output.lines()
                .dropWhile { it != invalidFilesMessage }
                .dropLastWhile { !it.startsWith(" ") }
                .joinToString("\n")

            throw problems.reporter.reportError(
                GradleException(errorMessage),
                Problems.VerifyPlugin.InvalidPlugin,
                problemsReportUrl,
            ) {
                details("Verification failed because the plugin(s) provided are not valid")
            }
        }

        val collectedProblems = collectProblems(output)

        collectedProblems.forEach { (ideVersion, ideProblems) ->
            ideProblems.forEach { (failureLevel, issues) ->
                issues.forEach { (title, description) ->
                    problems.reporter.report(
                        Problems.VerifyPlugin.VerificationFailure(failureLevel),
                    ) {
                        contextualLabel(title)
                        details(description)
                        severity(
                            when {
                                failureLevel in failureLevels -> Severity.ERROR
                                else -> Severity.WARNING
                            },
                        )
                    }
                }
            }
        }

        val verificationFailures = collectedProblems
            .flatMap { it.value.keys }
            .toSet()
            .intersect(failureLevels)

        if (verificationFailures.isNotEmpty()) {
            throw GradleException("Verification failed with $verificationFailures problems. See the report at: $problemsReportUrl")
        }
    }

    private fun collectProblems(output: String): Map<String, Map<FailureLevel, Map<String, String>>> {
        val headingToLevel = FailureLevel.values().associateBy { it.sectionHeading }
        val pluginLine = Regex("^Plugin .*? against (\\S+):")

        val lines = output.lineSequence().toList()
        val starts = lines.mapIndexedNotNull { i, s -> pluginLine.find(s)?.let { i to it.groupValues[1] } }

        return buildMap {
            starts.forEachIndexed { idx, (start, ide) ->
                val end = starts.getOrNull(idx + 1)?.first ?: lines.size
                val entries = lines.subList(start + 1, end)

                val headers = entries.mapIndexedNotNull { i, entry ->
                    headingToLevel.entries.firstOrNull { entry.startsWith(it.key) }?.let { i to it.value }
                }

                val map = headers.associateTo(linkedMapOf()) { (start, level) ->
                    val items = entries.drop(start + 1).takeWhile { it.startsWith(' ') }.map { it.trim() }
                    level to parseItemsToMap(items)
                }

                put(ide, map)
            }
        }
    }

    private fun parseItemsToMap(items: List<String>): Map<String, String> {
        val keys = items.mapIndexedNotNull { i, item ->
            when {
                item.startsWith("#") -> i to item.removePrefix("#")
                else -> null
            }
        }
        return buildMap {
            keys.forEachIndexed { idx, (start, key) ->
                val end = keys.getOrNull(idx + 1)?.first ?: items.size
                val value = items.subList(start + 1, end)
                    .asSequence()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .joinToString("\n")
                put(key, value)
            }
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IntelliJ Plugin Verifier CLI tool to check the binary compatibility with specified IDE builds."

        mainClass.set("com.jetbrains.pluginverifier.PluginVerifierMain")
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<VerifyPluginTask>(Tasks.VERIFY_PLUGIN) {
                val intellijPluginVerifierIdesConfiguration =
                    project.configurations[Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES]
                val pluginVerificationProvider = project.extensionProvider.map { it.pluginVerification }
                val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)

                ides = project.files(
                    project.provider {
                        with(intellijPluginVerifierIdesConfiguration.incoming.dependencies) {
                            if (size > 5) {
                                val ideList = joinToString(", ") { "${it.group}:${it.name}:${it.version}" }
                                log.warn("The ${Tasks.VERIFY_PLUGIN} task is about to resolve $size IDEs: $ideList")
                            }
                            flatMap {
                                project.configurations.detachedConfiguration(it).apply {
                                    attributes { attribute(Attributes.extracted, true) }
                                }.resolve()
                            }
                        }
                    },
                )

                freeArgs.convention(pluginVerificationProvider.flatMap { it.freeArgs })
                failureLevel.convention(pluginVerificationProvider.flatMap { it.failureLevel })
                verificationReportsDirectory.convention(pluginVerificationProvider.flatMap { it.verificationReportsDirectory })
                verificationReportsFormats.convention(pluginVerificationProvider.flatMap { it.verificationReportsFormats })
                externalPrefixes.convention(pluginVerificationProvider.flatMap { it.externalPrefixes })
                teamCityOutputFormat.convention(pluginVerificationProvider.flatMap { it.teamCityOutputFormat })
                subsystemsToCheck.convention(pluginVerificationProvider.flatMap { it.subsystemsToCheck })
                ignoredProblemsFile.convention(pluginVerificationProvider.flatMap { it.ignoredProblemsFile })

                archiveFile.convention(buildPluginTaskProvider.flatMap { it.archiveFile })
                offline.convention(project.gradle.startParameter.isOffline)

                problemsReportFile.convention(project.layout.buildDirectory.file("reports/problems/problems-report.html"))
            }
    }

    @Suppress("unused")
    enum class FailureLevel(val sectionHeading: String, val message: String) {
        COMPATIBILITY_WARNINGS(
            "Compatibility warnings",
            "Compatibility warnings detected against the specified IDE version.",
        ),
        COMPATIBILITY_PROBLEMS(
            "Compatibility problems",
            "Compatibility problems detected against the specified IDE version.",
        ),
        DEPRECATED_API_USAGES(
            "Deprecated API usages",
            "Plugin uses API marked as deprecated (@Deprecated).",
        ),
        SCHEDULED_FOR_REMOVAL_API_USAGES(
            /* # usage(s) of */"scheduled for removal API",
            "Plugin uses API marked as scheduled for removal (ApiStatus.@ScheduledForRemoval).",
        ),
        EXPERIMENTAL_API_USAGES(
            "Experimental API usages",
            "Plugin uses API marked as experimental (ApiStatus.@Experimental).",
        ),
        INTERNAL_API_USAGES(
            "Internal API usages",
            "Plugin uses API marked as internal (ApiStatus.@Internal).",
        ),
        OVERRIDE_ONLY_API_USAGES(
            "Override-only API usages",
            "Override-only API is used incorrectly (ApiStatus.@OverrideOnly).",
        ),
        NON_EXTENDABLE_API_USAGES(
            "Non-extendable API usages",
            "Non-extendable API is used incorrectly (ApiStatus.@NonExtendable).",
        ),
        PLUGIN_STRUCTURE_WARNINGS(
            "Plugin structure warnings",
            "The structure of the plugin is not valid.",
        ),
        MISSING_DEPENDENCIES(
            "Missing dependencies",
            "Plugin has some dependencies missing.",
        ),
        INVALID_PLUGIN(
            "The following files specified for the verification are not valid plugins",
            "Provided plugin artifact is not valid.",
        ),
        NOT_DYNAMIC(
            "Plugin probably cannot be enabled or disabled without IDE restart",
            "Plugin probably cannot be enabled or disabled without IDE restart.",
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

    @Suppress("unused")
    enum class Subsystems {
        ALL,
        ANDROID_ONLY,
        WITHOUT_ANDROID;

        override fun toString() = name.lowercase().replace('_', '-')
    }
}
