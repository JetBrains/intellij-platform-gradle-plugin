// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.options.Option
import org.gradle.kotlin.dsl.of
import org.gradle.work.DisableCachingByDefault
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.providers.CurrentPluginVersionValueSource
import org.jetbrains.intellij.platform.gradle.tasks.registerTask
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.writeTextAtomicallyIfChanged

/**
 * Common envelope of the JSON reports written by the agent-oriented tasks.
 * The JSON file, not the console output, is the machine-readable contract.
 */
@Serializable
internal data class Report<T>(
    val schemaVersion: Int = 1,
    val reportType: String,
    val gradlePluginVersion: String,
    val projectPath: String,
    val status: ReportStatus,
    val data: T?,
    val issues: List<Issue> = emptyList(),
    val unavailable: List<Unavailable> = emptyList(),
)

@Serializable
internal enum class ReportStatus { complete, partial, failed }

@Serializable
internal data class Issue(
    val id: String,
    val severity: String = "warning",
    val message: String,
    val setting: String? = null,
    val suggestedFix: String? = null,
    val documentation: String? = null,
    val suppressed: Boolean = false,
)

/** An observation that is deliberately or involuntarily missing from the report: notRequested, notResolved, or unavailable. */
@Serializable
internal data class Unavailable(val observation: String, val state: String, val reason: String)

@Serializable
internal data class TargetIde(
    val productCode: String,
    val version: String,
    val buildNumber: String? = null,
    val root: String? = null,
    val provenance: String,
)

/**
 * Base class of the tasks that expose the project, SDK, and platform facts as versioned JSON reports consumable by
 * coding agents and CI. Reports never compile, launch an IDE, publish, sign, or edit the project.
 */
@DisableCachingByDefault(because = "Reports describe the current environment")
abstract class AgentReportTask : DefaultTask() {

    /**
     * Destination of the JSON report, relative CLI paths are resolved against the current project.
     */
    @get:OutputFile
    @get:Option(option = "output", description = "Write the JSON report to this path (relative to this project).")
    abstract val outputFile: RegularFileProperty

    @get:Internal
    abstract val projectPath: Property<String>

    @get:Internal
    abstract val pluginVersion: Property<String>

    internal inline fun <reified T> write(
        type: String,
        status: ReportStatus,
        data: T?,
        issues: List<Issue> = emptyList(),
        unavailable: List<Unavailable> = emptyList(),
    ) {
        val report = Report(reportType = type, gradlePluginVersion = pluginVersion.get(), projectPath = projectPath.get(), status = status, data = data, issues = issues, unavailable = unavailable)
        outputFile.asPath.writeTextAtomicallyIfChanged(json.encodeToString(report) + "\n")
        logger.lifecycle("IntelliJ Platform report: ${outputFile.asPath}")
    }

    init {
        group = Plugin.Groups.INFO
    }

    companion object {
        internal val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }

        internal inline fun <reified T : AgentReportTask> Project.registerAgentReportTask(name: String, noinline configuration: T.() -> Unit) =
            registerTask<T>(name) {
                outputFile.convention(layout.buildDirectory.file("reports/intellij-platform/${name.removePrefix("intellijPlatform").lowercase()}.json"))
                projectPath.convention(path)
                pluginVersion.convention(providers.of(CurrentPluginVersionValueSource::class) {})
                configuration()
            }
    }
}
