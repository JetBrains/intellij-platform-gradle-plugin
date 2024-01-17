// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.debug
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.logCategory
import org.jetbrains.intellij.platform.gradle.tasks.base.PluginVerifierAware
import org.jetbrains.intellij.platform.gradle.tasks.base.RuntimeAware
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.io.ByteArrayOutputStream
import java.util.*
import kotlin.io.path.exists
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
 * TODO: Use Reporting for handling verification report output? See: https://docs.gradle.org/current/dsl/org.gradle.api.reporting.Reporting.html
 */
@UntrackedTask(because = "Should always run Plugin Verifier")
abstract class VerifyPluginTask : JavaExec(), RuntimeAware, PluginVerifierAware {

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IDE builds."

        mainClass.set("com.jetbrains.pluginverifier.PluginVerifierMain")
    }

    @get:InputFiles
    @get:Classpath
    abstract val ides: ConfigurableFileCollection

    /**
     * Defines the verification level at which the task should fail if any reported issue matches.
     * Can be set as [FailureLevel] enum or [EnumSet<FailureLevel>].
     *
     * Default value: [FailureLevel.COMPATIBILITY_PROBLEMS]
     */
    @get:Input
    abstract val failureLevel: ListProperty<FailureLevel>

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
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    @get:Optional
    abstract val ignoredProblemsFile: RegularFileProperty

    @get:Internal
    abstract val offline: Property<Boolean>

    private val context = logCategory()

    /**
     * Runs the IntelliJ Plugin Verifier against the plugin artifact.
     */
    @TaskAction
    override fun exec() {
        val file = distributionFile.orNull?.asPath
        if (file == null || !file.exists()) {
            throw IllegalStateException("Plugin file does not exist: $file")
        }

        with(ides.files) {
            if (isEmpty()) {
                throw GradleException("No IDE selected for verification with the IntelliJ Plugin Verifier")
            }
            args(listOf("check-plugin") + getOptions() + file.toString() + map {
                when {
                    it.isDirectory -> it.path
                    else -> it.readText()
                }
            })
        }

        debug(context, "Distribution file: $file")
        debug(context, "Verifier path: $pluginVerifierExecutable")

        classpath = objectFactory.fileCollection().from(pluginVerifierExecutable)

        ByteArrayOutputStream().use { os ->
            standardOutput = TeeOutputStream(System.out, os)
            super.exec()
            verifyOutput(os)
        }
    }

    /**
     * Collects all the options for the Plugin Verifier CLI provided with the task configuration.
     *
     * @return array with all available CLI options
     */
    private fun getOptions(): List<String> {
        val args = mutableListOf(
            "-verification-reports-dir", verificationReportsDirectory.asPath.pathString,
            "-runtime-dir", runtimeDirectory.asPath.pathString,
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

        // TODO check PV version
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

    private fun verifyOutput(os: ByteArrayOutputStream) {
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

    companion object {
        fun register(project: Project) =
            project.registerTask<VerifyPluginTask>(Tasks.VERIFY_PLUGIN) {
                val intellijPluginVerifierIdesConfiguration = project.configurations.getByName(Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES)

                val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)
                val extension = project.the<IntelliJPlatformExtension>()

                extension.verifyPlugin.let {
                    ides.from(intellijPluginVerifierIdesConfiguration)
                    downloadDirectory.convention(it.downloadDirectory)
                    freeArgs.convention(it.freeArgs)
                }

                failureLevel.convention(extension.verifyPlugin.failureLevel)
                distributionFile.convention(buildPluginTaskProvider.flatMap { it.archiveFile })
                verificationReportsDirectory.convention(extension.verifyPlugin.verificationReportsDirectory)
                verificationReportsFormats.convention(extension.verifyPlugin.verificationReportsFormats)
                externalPrefixes.convention(extension.verifyPlugin.externalPrefixes)
                teamCityOutputFormat.convention(extension.verifyPlugin.teamCityOutputFormat)
                subsystemsToCheck.convention(extension.verifyPlugin.subsystemsToCheck)

                ignoredProblemsFile.convention(extension.verifyPlugin.ignoredProblemsFile)
                offline.convention(project.gradle.startParameter.isOffline)

                dependsOn(buildPluginTaskProvider)
//                dependsOn(Tasks.VERIFY_PLUGIN)
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
}
