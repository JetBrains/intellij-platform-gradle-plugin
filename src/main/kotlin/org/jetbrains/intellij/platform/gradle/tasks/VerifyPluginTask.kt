// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

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
import org.gradle.api.tasks.options.Option
import org.gradle.internal.logging.ConsoleRenderer
import org.gradle.kotlin.dsl.assign
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Configurations.Attributes
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.models.ProductInfo
import org.jetbrains.intellij.platform.gradle.models.productInfo
import org.jetbrains.intellij.platform.gradle.models.type
import org.jetbrains.intellij.platform.gradle.problems.Problems
import org.jetbrains.intellij.platform.gradle.problems.reportError
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginVerifierAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.ProblemsAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.ProductReleasesServiceAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RuntimeAware
import org.jetbrains.intellij.platform.gradle.utils.*
import java.nio.file.Path
import java.util.*
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

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
abstract class VerifyPluginTask : JavaExec(), RuntimeAware, PluginVerifierAware, ProblemsAware, ProductReleasesServiceAware {

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

    /**
     * A flag to list IDEs without performing verification.
     * When enabled, only prints the list of IDEs that will be used for verification without performing actual verification.
     *
     * Default value: `false`
     */
    @get:Input
    @get:Optional
    @get:Option(option = "list-ides", description = "List IDEs that would be used for verification without performing it")
    abstract val listIdes: Property<Boolean>

    /**
     * Determines whether the IntelliJ Plugin Verifier resolves plugin classes against the JetBrains Runtime (JBR)
     * bundled with each verified IDE, instead of a single runtime shared across all verified IDEs.
     *
     * When enabled (default), the `-runtime-dir` option is not passed to the Plugin Verifier, so it resolves classes
     * against the JBR bundled within each target IDE. The [runtimeDirectory] is still exposed through the `JAVA_HOME`
     * environment variable and used only as a fallback for IDEs that don't ship a bundled JBR.
     *
     * When disabled, the resolved [runtimeDirectory] (the JetBrains Runtime associated with the IntelliJ Platform used
     * to build the plugin) is forced for all verified IDEs via the `-runtime-dir` option — the behavior from before
     * this option was introduced.
     *
     * Default value: [IntelliJPlatformExtension.PluginVerification.useBundledRuntime], `true`
     *
     * @see IntelliJPlatformExtension.PluginVerification.useBundledRuntime
     * @see <a href="https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1611">#1611</a>
     */
    @get:Input
    @get:Optional
    abstract val useBundledRuntime: Property<Boolean>

    private val problemsReportUrl get() = ConsoleRenderer().asClickableFileUrl(problemsReportFile.get().asFile)

    private val log = Logger(javaClass)

    /**
     * Runs the IntelliJ Plugin Verifier against the plugin artifact.
     */
    @TaskAction
    override fun exec() {
        with(ides) {
            if (isEmpty) {
                val label = "No IDE versions configured for verification"
                val details = "The IntelliJ Plugin Verifier requires at least one IDE version to verify the plugin against, but none were configured. IDE versions are specified through the intellijPlatform.pluginVerification.ides block."
                val solution = "Configure IDE versions in the intellijPlatform.pluginVerification.ides block (e.g., ides { recommended() }) or enable the default recommended IDEs fallback with '${GradleProperties.VerifyPluginDefaultRecommendedIdes}=true'. Ensure defaultRepositories() or at least localPlatformArtifacts() is present in the repositories section to resolve IDE artifacts."

                throw problems.reporter.reportError(
                    GradleException("$label $details $solution"),
                    Problems.VerifyPlugin.InvalidIDEs,
                    problemsReportUrl,
                ) {
                    contextualLabel(label)
                    details(details)
                    solution(solution)
                    documentedAt("https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification-ides")
                }
            }

            if (listIdes.getOrElse(false)) {
                return map { it.toPath().resolvePlatformPath() }.joinToString(
                    separator = "\n",
                    prefix = "IDEs that will be used for verification:\n",
                ) { platformPath ->
                    val productInfo = platformPath.productInfo()
                    "${productInfo.listIdeNotation()} - ${platformPath.safePathString}"
                }.let(::println)
            }
        }

        val file = archiveFile.orNull?.asPath
        if (file == null || !file.exists()) {
            val label = "Plugin archive file not found"
            val details = "The plugin archive file ${file ?: "is not specified"} does not exist or could not be found. This typically happens when the BuildPluginTask has not been executed or its output location was changed."
            val solution = "Ensure the BuildPluginTask has been executed successfully and verify the archiveFile property points to a valid plugin artifact."

            throw problems.reporter.reportError(
                IllegalStateException("$label $details $solution"),
                Problems.VerifyPlugin.InvalidPlugin,
                problemsReportUrl,
            ) {
                contextualLabel(label)
                details(details)
                solution(solution)
                fileLocation(file?.toString() ?: "")
            }
        }

        log.debug("Distribution file: $file")

        val executable = pluginVerifierExecutable.orNull?.asPath
            ?: run {
                val label = "IntelliJ Plugin Verifier executable not found"
                val details = "The IntelliJ Plugin Verifier CLI tool executable could not be located. This dependency is required to perform plugin verification against target IDE versions."
                val solution = "Add the pluginVerifier() dependency in the project dependencies section: dependencies { intellijPlatform { pluginVerifier() } }, or configure the intellijPlatform.pluginVerification.cliPath extension property to point to a local Plugin Verifier installation."

                throw problems.reporter.reportError(
                    GradleException("$label $details $solution"),
                    Problems.VerifyPlugin.InvalidPluginVerifier,
                    problemsReportUrl,
                ) {
                    contextualLabel(label)
                    details(details)
                    solution(solution)
                    documentedAt("https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-extension.html#intellijPlatform-pluginVerification")
                }
            }

        log.debug("Verifier path: $executable")

        classpath = objectFactory.fileCollection().from(executable)

        // When verifying against each IDE's bundled JBR, don't force a single runtime with `-runtime-dir`; instead
        // expose the resolved runtime via `JAVA_HOME` so the Plugin Verifier uses it only as a fallback for IDEs
        // that don't ship a bundled JBR. See: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1611
        if (useBundledRuntime.getOrElse(true)) {
            environment("JAVA_HOME", runtimeDirectory.asPath.safePathString)
        }

        args(
            listOf("check-plugin") + getOptions() + file.safePathString + ides.map {
                when {
                    it.isDirectory -> it.toPath().safePathString
                    else -> it.readText()
                }
            },
        )

        execWithTeeOutput(teeErrorOutput = false, throwOutputOnFailure = false) {
            super.exec()
        }.let(::verifyResult)
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return An array with all available CLI options
     */
    private fun getOptions(): List<String> {
        val args = mutableListOf(
            "-verification-reports-dir", verificationReportsDirectory.asPath.safePathString,
        )

        // Force the configured runtime for all verified IDEs only when bundled-JBR verification is disabled.
        // Otherwise the option is omitted so the Plugin Verifier picks each IDE's bundled JBR. See: #1611
        if (!useBundledRuntime.getOrElse(true)) {
            args.add("-runtime-dir")
            args.add(runtimeDirectory.asPath.safePathString)
        }

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
     * Determines the verification outcome by reading the reports produced by the IntelliJ Plugin Verifier under
     * [verificationReportsDirectory] and fails the task when any reported [FailureLevel] matches the configured
     * [failureLevel].
     *
     * Unlike parsing the console output, the report files are always written by the Plugin Verifier regardless of
     * the console output format, so the pass/fail decision no longer depends on the [teamCityOutputFormat] flag.
     *
     * @see <a href="https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1739">#1739</a>
     * @throws GradleException when the verification reports contain problems matching the [failureLevel]
     */
    @Throws(GradleException::class)
    private fun verifyResult(output: String) {
        val failureLevels = failureLevel.get()
        log.debug("Current failure levels: ${failureLevels.joinToString(", ")}")

        // A plugin archive that cannot be parsed at all is reported by the Plugin Verifier as an "invalid plugin
        // file" for which no per-IDE verification report is produced, so this structural failure is detected from
        // the tool output directly.
        val invalidFilesMessage = "The following files specified for the verification are not valid plugins:"
        if (output.contains(invalidFilesMessage)) {
            val errorMessage = output.lines()
                .dropWhile { it != invalidFilesMessage }
                .dropLastWhile { !it.startsWith(" ") }
                .joinToString("\n")

            val label = "Invalid plugin structure detected"
            val details = "The Plugin Verifier determined that the provided plugin artifact does not have a valid plugin structure. This may indicate missing plugin.xml, incorrect JAR structure, or other structural issues.\n$errorMessage"
            val solution = "Verify the plugin build process is completing successfully and the generated archive contains a valid plugin structure with META-INF/plugin.xml and required classes."

            throw problems.reporter.reportError(
                GradleException("$label $details $solution"),
                Problems.VerifyPlugin.InvalidPlugin,
                problemsReportUrl,
            ) {
                contextualLabel(label)
                details(details)
                solution(solution)
            }
        }

        val collectedProblems = collectProblems(output)

        collectedProblems.forEach { (ideVersion, ideProblems) ->
            ideProblems.forEach { (failureLevel, description) ->
                val label = failureLevel.sectionHeading
                val details = buildString {
                    append("IDE Version: $ideVersion")
                    append("\n")
                    append(failureLevel.message)
                    if (description.isNotBlank()) {
                        append("\n\n")
                        append(description)
                    }
                }
                val solution = when (failureLevel) {
                    FailureLevel.COMPATIBILITY_PROBLEMS, FailureLevel.COMPATIBILITY_WARNINGS ->
                        "Review the compatibility issues and update your plugin code to use compatible APIs for the target IDE version. Consider updating dependency versions or adjusting the since-build/until-build range."
                    FailureLevel.DEPRECATED_API_USAGES ->
                        "Replace deprecated API usage with recommended alternatives. Check the IDE's API documentation for migration paths."
                    FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES ->
                        "Remove usage of APIs scheduled for removal and migrate to replacement APIs immediately to ensure future compatibility."
                    FailureLevel.EXPERIMENTAL_API_USAGES ->
                        "Be aware that experimental APIs may change without notice. Consider using stable alternatives or accept the risk of future API changes."
                    FailureLevel.INTERNAL_API_USAGES ->
                        "Replace internal API usage with public APIs. Internal APIs are not intended for plugin use and may break compatibility."
                    FailureLevel.OVERRIDE_ONLY_API_USAGES ->
                        "Override-only APIs should only be overridden in subclasses, not called directly. Review your usage and follow the API contract."
                    FailureLevel.NON_EXTENDABLE_API_USAGES ->
                        "Do not extend classes or interfaces marked as non-extendable. Use composition or find alternative extension points."
                    FailureLevel.PLUGIN_STRUCTURE_WARNINGS ->
                        "Fix the plugin structure issues identified. Ensure plugin.xml is valid and all required files are present."
                    FailureLevel.MISSING_DEPENDENCIES ->
                        "Add the missing plugin dependencies to your plugin.xml <depends> section or include them in your plugin distribution."
                    FailureLevel.INVALID_PLUGIN ->
                        "Fix the plugin structure to create a valid plugin artifact. Ensure META-INF/plugin.xml exists and is properly formatted."
                    FailureLevel.NOT_DYNAMIC ->
                        "If dynamic loading is required, review the plugin structure and ensure all components support dynamic loading. Otherwise, accept that IDE restart is needed."
                }

                problems.reporter.report(
                    Problems.VerifyPlugin.VerificationFailure(failureLevel),
                ) {
                    contextualLabel(label)
                    details(details)
                    solution(solution)
                    severity(
                        when {
                            failureLevel in failureLevels -> Severity.ERROR
                            else -> Severity.WARNING
                        },
                    )
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

    /**
     * Reads the verification reports written by the Plugin Verifier into [verificationReportsDirectory] and maps
     * them onto [FailureLevel] categories, grouped by the verified IDE version.
     *
     * The verifier recreates the reports directory on each run and stores the outcome of every verified
     * `(IDE, plugin)` pair using the following layout:
     * ```
     * <reports>/<IDE version>/plugins/<plugin ID>/<plugin version>/verification-verdict.txt (+ detail files)
     * ```
     *
     * The always-present `verification-verdict.txt` encodes every reported category, so its content is the source
     * of truth for the pass/fail decision; the optional per-category detail files (when present) are used to enrich
     * the reported problem descriptions. The only exception is [FailureLevel.NOT_DYNAMIC], which the verifier does
     * not persist into the reports and is therefore detected on a best-effort basis from the (plain) tool output.
     */
    private fun collectProblems(output: String): Map<String, Map<FailureLevel, String>> {
        val reportsDirectory = verificationReportsDirectory.asPath.takeIf { it.exists() }
            ?: return emptyMap()

        val notDynamic = output.contains(FailureLevel.NOT_DYNAMIC.sectionHeading)

        return reportsDirectory.listDirectoryEntries()
            .filter { it.isDirectory() }
            .associate { ideDirectory ->
                val ideProblems = linkedMapOf<FailureLevel, String>()

                ideDirectory.resolve("plugins")
                    .takeIf { it.exists() }
                    ?.listDirectoryEntries()?.filter { it.isDirectory() } // <plugin ID> directories
                    ?.flatMap { it.listDirectoryEntries().filter { path -> path.isDirectory() } } // <plugin version> directories
                    ?.forEach { pluginDirectory ->
                        val verdict = pluginDirectory.resolve(VERIFICATION_VERDICT_FILE_NAME)
                            .takeIf { it.exists() }
                            ?.readText()
                            .orEmpty()

                        verdictMarkers.forEach { (marker, level) ->
                            if (verdict.contains(marker)) {
                                ideProblems.putIfAbsent(level, detailsOf(level, pluginDirectory, verdict))
                            }
                        }
                    }

                if (notDynamic) {
                    ideProblems.putIfAbsent(FailureLevel.NOT_DYNAMIC, FailureLevel.NOT_DYNAMIC.message)
                }

                ideDirectory.name to ideProblems
            }
            .filterValues { it.isNotEmpty() }
    }

    /**
     * Provides a human-readable description for the given [level] occurred in [pluginDirectory], preferring the
     * content of the corresponding detail file (when present) and falling back to the plugin's verdict otherwise.
     */
    private fun detailsOf(level: FailureLevel, pluginDirectory: Path, verdict: String): String {
        val detailFileName = detailFileNames[level] ?: return verdict.trim()
        return pluginDirectory.resolve(detailFileName)
            .takeIf { it.exists() }
            ?.readText()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: verdict.trim()
    }

    private fun ProductInfo.listIdeNotation() =
        runCatching {
            productReleasesService.get()
                .resolve(type, buildNumber.toVersion())
                .orNull
        }.getOrNull()?.notation ?: "$type-$version"

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IntelliJ Plugin Verifier CLI tool to check the binary compatibility with specified IDE builds."

        mainClass.set("com.jetbrains.pluginverifier.PluginVerifierMain")
    }

    companion object : Registrable {
        /**
         * The name of the always-present per-plugin verdict file written by the Plugin Verifier for every verified
         * `(IDE, plugin)` pair. Its content encodes all reported problem categories.
         */
        private const val VERIFICATION_VERDICT_FILE_NAME = "verification-verdict.txt"

        /**
         * Maps a stable phrase from the Plugin Verifier verdict ([VERIFICATION_VERDICT_FILE_NAME]) onto the matching
         * [FailureLevel]. The verdict is used as the source of truth because it precisely distinguishes categories
         * that share a report file (e.g. scheduled-for-removal vs. deprecated API usages) and encodes the ones that
         * have no dedicated file at all (e.g. missing mandatory dependencies).
         */
        private val verdictMarkers = linkedMapOf(
            "missing mandatory" to FailureLevel.MISSING_DEPENDENCIES,
            "compatibility problem" to FailureLevel.COMPATIBILITY_PROBLEMS,
            "compatibility warning" to FailureLevel.COMPATIBILITY_WARNINGS,
            "of scheduled for removal API" to FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
            "of deprecated API" to FailureLevel.DEPRECATED_API_USAGES,
            "of experimental API" to FailureLevel.EXPERIMENTAL_API_USAGES,
            "of internal API" to FailureLevel.INTERNAL_API_USAGES,
            "non-extendable API usage" to FailureLevel.NON_EXTENDABLE_API_USAGES,
            "override-only API usage" to FailureLevel.OVERRIDE_ONLY_API_USAGES,
            "plugin configuration defect" to FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
            "Plugin is invalid:" to FailureLevel.INVALID_PLUGIN,
        )

        /**
         * Optional per-[FailureLevel] detail files providing richer descriptions than the verdict summary. They are
         * written only when the respective category has at least one entry, so their content — when present — is used
         * purely to enrich the reported problem descriptions and never to drive the pass/fail decision.
         */
        private val detailFileNames = mapOf(
            FailureLevel.COMPATIBILITY_WARNINGS to "compatibility-warnings.txt",
            FailureLevel.COMPATIBILITY_PROBLEMS to "compatibility-problems.txt",
            FailureLevel.DEPRECATED_API_USAGES to "deprecated-usages.txt",
            FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES to "deprecated-usages.txt",
            FailureLevel.EXPERIMENTAL_API_USAGES to "experimental-api-usages.txt",
            FailureLevel.INTERNAL_API_USAGES to "internal-api-usages.txt",
            FailureLevel.OVERRIDE_ONLY_API_USAGES to "override-only-usages.txt",
            FailureLevel.NON_EXTENDABLE_API_USAGES to "non-extendable-api-usages.txt",
            FailureLevel.PLUGIN_STRUCTURE_WARNINGS to "plugin-structure-warnings.txt",
            FailureLevel.INVALID_PLUGIN to "invalid-plugin.txt",
        )

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
                listIdes.convention(false)
                useBundledRuntime.convention(pluginVerificationProvider.flatMap { it.useBundledRuntime })

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
