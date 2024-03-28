// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.apache.tools.ant.util.TeeOutputStream
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.aware.PluginVerifierAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RuntimeAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

/**
 * Runs the IntelliJ Plugin Verifier CLI tool to check the binary compatibility with specified IDE builds.
 *
 * @see IntelliJPlatformExtension.VerifyPlugin
 * @see <a href="https://github.com/JetBrains/intellij-plugin-verifier">IntelliJ Plugin Verifier</a>
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/verifying-plugin-compatibility.html">Verifying Plugin Compatibility</a>
 *
 * TODO: Use Reporting for handling verification report output? See: https://docs.gradle.org/current/dsl/org.gradle.api.reporting.Reporting.html
 */
@UntrackedTask(because = "Should always run")
abstract class VerifyPluginTask : JavaExec(), RuntimeAware, PluginVerifierAware {

    /**
     * Holds a reference to IntelliJ Platform IDEs which will be used by the IntelliJ Plugin Verifier CLI tool for verification.
     *
     * The list of IDEs is controlled with the [IntelliJPlatformExtension.VerifyPlugin.Ides] extension.
     *
     * @see [IntelliJPlatformExtension.VerifyPlugin.Ides]
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
     * Default value: [IntelliJPlatformExtension.VerifyPlugin.externalPrefixes]
     *
     * @see IntelliJPlatformExtension.VerifyPlugin.externalPrefixes
     */
    @get:Input
    @get:Optional
    abstract val externalPrefixes: ListProperty<String>

    /**
     * Defines the verification level at which the task should fail if any reported issue matches.
     *
     * Default value: [IntelliJPlatformExtension.VerifyPlugin.failureLevel]
     *
     * @see FailureLevel
     * @see IntelliJPlatformExtension.VerifyPlugin.failureLevel
     */
    @get:Input
    abstract val failureLevel: ListProperty<FailureLevel>

    /**
     * The list of free arguments is passed directly to the IntelliJ Plugin Verifier CLI tool.
     *
     * They can be used in addition to the arguments that are provided by dedicated options.
     *
     * Default value: [IntelliJPlatformExtension.VerifyPlugin.freeArgs]
     *
     * @see IntelliJPlatformExtension.VerifyPlugin.freeArgs
     */
    @get:Input
    @get:Optional
    abstract val freeArgs: ListProperty<String>

    /**
     * A file that contains a list of problems that will be ignored in a report.
     *
     * Default value: [IntelliJPlatformExtension.VerifyPlugin.ignoredProblemsFile]
     *
     * @see IntelliJPlatformExtension.VerifyPlugin.ignoredProblemsFile
     */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val ignoredProblemsFile: RegularFileProperty

    /**
     * Determines if the operation is running in offline mode.
     *
     * Default value: [StartParameter.offline]
     *
     * @see StartParameter
     * @see <a href="https://docs.gradle.org/current/userguide/command_line_interface.html#sec:command_line_execution_options">Command Line Execution options</a>
     */
    @get:Internal
    abstract val offline: Property<Boolean>

    /**
     * Specifies which subsystems of IDE should be checked.
     *
     * Default value: [IntelliJPlatformExtension.VerifyPlugin.subsystemsToCheck]
     *
     * @see Subsystems
     * @see IntelliJPlatformExtension.VerifyPlugin.subsystemsToCheck
     */
    @get:Input
    @get:Optional
    abstract val subsystemsToCheck: Property<Subsystems>

    /**
     * A flag that controls the output format - if set to `true`, the TeamCity compatible output will be returned to stdout.
     *
     * Default value: [IntelliJPlatformExtension.VerifyPlugin.teamCityOutputFormat]
     *
     * @see IntelliJPlatformExtension.VerifyPlugin.teamCityOutputFormat
     */
    @get:Input
    @get:Optional
    abstract val teamCityOutputFormat: Property<Boolean>

    /**
     * The path to the directory where verification reports will be saved.
     *
     * Default value: [IntelliJPlatformExtension.VerifyPlugin.verificationReportsDirectory]
     *
     * @see IntelliJPlatformExtension.VerifyPlugin.verificationReportsDirectory
     */
    @get:OutputDirectory
    @get:Optional
    abstract val verificationReportsDirectory: DirectoryProperty

    /**
     * The output formats of the verification reports.
     *
     * Default value: [IntelliJPlatformExtension.VerifyPlugin.verificationReportsFormats]
     *
     * @see VerificationReportsFormats
     * @see IntelliJPlatformExtension.VerifyPlugin.verificationReportsFormats
     */
    @get:Input
    @get:Optional
    abstract val verificationReportsFormats: ListProperty<VerificationReportsFormats>

    private val log = Logger(javaClass)

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IDE builds."

        mainClass.set("com.jetbrains.pluginverifier.PluginVerifierMain")
    }

    /**
     * Runs the IntelliJ Plugin Verifier against the plugin artifact.
     */
    @TaskAction
    override fun exec() {
        val file = archiveFile.orNull?.asPath
        if (file == null || !file.exists()) {
            throw IllegalStateException("Plugin file does not exist: $file")
        }

        with(ides.files) {
            if (isEmpty()) {
                throw GradleException("No IDE selected for verification with the IntelliJ Plugin Verifier")
            }
            args(listOf("check-plugin") + getOptions() + file.absolutePathString() + map {
                when {
                    it.isDirectory -> it.absolutePath
                    else -> it.readText()
                }
            })
        }

        log.debug("Distribution file: $file")
        log.debug("Verifier path: $pluginVerifierExecutable")

        classpath = objectFactory.fileCollection().from(pluginVerifierExecutable)

        ByteArrayOutputStream().use { os ->
            standardOutput = TeeOutputStream(System.out, os)
            super.exec()
            verifyOutput(os.toString())
        }
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with all available CLI options
     */
    private fun getOptions(): List<String> {
        val args = mutableListOf(
            "-verification-reports-dir", verificationReportsDirectory.asPath.absolutePathString(),
            "-runtime-dir", runtimeDirectory.asPath.absolutePathString(),
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
            args.add(ignoredProblemsFile.asPath.absolutePathString())
        }

        freeArgs.orNull?.let {
            args.addAll(it)
        }

        return args
    }

    private fun verifyOutput(output: String) {
        log.debug("Current failure levels: ${FailureLevel.values().joinToString(", ")}")

        val invalidFilesMessage = "The following files specified for the verification are not valid plugins:"
        if (output.contains(invalidFilesMessage)) {
            val errorMessage = output.lines()
                .dropWhile { it != invalidFilesMessage }
                .dropLastWhile { !it.startsWith(" ") }
                .joinToString(System.lineSeparator())

            throw GradleException(errorMessage)
        }

        FailureLevel.values().forEach { level ->
            if (failureLevel.get().contains(level) && output.contains(level.sectionHeading)) {
                log.debug("Failing task on '$failureLevel' failure level")
                throw GradleException(
                    "$level: ${level.message} Check Plugin Verifier report for more details.\n" +
                            "Incompatible API Changes: https://jb.gg/intellij-api-changes"
                )
            }
        }
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<VerifyPluginTask>(Tasks.VERIFY_PLUGIN) {
                val intellijPluginVerifierIdesConfiguration = project.configurations[Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES]
                val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)
                val extension = project.the<IntelliJPlatformExtension>()

                extension.verifyPlugin.let {
                    freeArgs.convention(it.freeArgs)
                    failureLevel.convention(it.failureLevel)
                    verificationReportsDirectory.convention(it.verificationReportsDirectory)
                    verificationReportsFormats.convention(it.verificationReportsFormats)
                    externalPrefixes.convention(it.externalPrefixes)
                    teamCityOutputFormat.convention(it.teamCityOutputFormat)
                    subsystemsToCheck.convention(it.subsystemsToCheck)
                    ignoredProblemsFile.convention(it.ignoredProblemsFile)
                }

                ides.from(intellijPluginVerifierIdesConfiguration)
                archiveFile.convention(buildPluginTaskProvider.flatMap { it.archiveFile })
                offline.convention(project.gradle.startParameter.isOffline)
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
            "Plugin uses API marked as internal (ApiStatus.@Internal)."
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
            "Plugin probably cannot be enabled or disabled without IDE restart",
            "Plugin probably cannot be enabled or disabled without IDE restart."
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
