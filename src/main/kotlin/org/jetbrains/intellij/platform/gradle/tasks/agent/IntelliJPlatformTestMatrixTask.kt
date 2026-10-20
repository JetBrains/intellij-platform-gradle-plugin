// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.agent

import kotlinx.serialization.Serializable
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.junit.JUnitOptions
import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions
import org.gradle.api.tasks.testing.testng.TestNGOptions
import org.gradle.kotlin.dsl.findByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.withType
import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.TestIdePerformanceTask
import org.jetbrains.intellij.platform.gradle.tasks.TestIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.TestIdeUiTask
import org.jetbrains.intellij.platform.gradle.utils.safePathString

@Serializable
internal data class MatrixTests(val module: Boolean, val sourceSets: List<MatrixSourceSet>, val tests: List<MatrixTest>)

@Serializable
internal data class MatrixTest(
    val taskPath: String,
    val taskType: String,
    val kind: String,
    val enabled: Boolean,
    val framework: String,
    val frameworkDependencies: List<MatrixDependency>,
    val frameworkDependencyState: String,
    val sourceSets: List<String>,
    val sourceSetAssociation: String,
    val command: String,
    val supportsTestFilter: Boolean,
    val filteredCommand: String?,
    val costs: MatrixTestCosts,
    val ide: MatrixIde?,
    val runtime: Map<String, MatrixValue>,
    val plugins: List<MatrixDependency>,
    val pluginState: String,
    val sandbox: MatrixValue,
    val outputs: Map<String, MatrixValue>,
    val prerequisites: List<MatrixPrerequisite>,
    val executionStatus: String = "untested",
)

@Serializable
internal data class MatrixTestCosts(
    val mayCompile: Boolean = true,
    val startsIde: Boolean,
    val requiresUi: Boolean,
    val mayDownloadDependencies: Boolean = true,
)

@Serializable
internal data class MatrixPrerequisite(val id: String, val state: String, val reason: String)

/**
 * Writes a configuration-only inventory of JVM, IDE, UI, and performance tests.
 * Commands describe potentially expensive future executions, not dependencies of this report.
 * Source-set associations are conventions, not an inspection of resolved test classpaths.
 */
@UntrackedTask(because = "The matrix observes configuration without executing tests")
abstract class IntelliJPlatformTestMatrixTask : AgentReportTask() {

    /** Serialized configuration-time metadata; the task action does not retain or access the project. */
    @get:Internal
    abstract val snapshot: Property<String>

    /** Writes the inventory using the shared versioned JSON envelope. */
    @TaskAction
    fun report() {
        val metadata = json.decodeFromString<MatrixSnapshot<MatrixTests>>(snapshot.get())
        write("testMatrix", if (metadata.unavailable.isEmpty()) ReportStatus.complete else ReportStatus.partial, metadata.data, metadata.issues, metadata.unavailable)
    }

    init {
        description = "Writes a JSON test matrix without compiling, resolving dependencies, or starting an IDE."
    }

    /** Registers the report without adding test, sandbox, or build dependencies. */
    companion object : Registrable {
        override fun register(project: Project) {
            project.registerAgentReportTask<IntelliJPlatformTestMatrixTask>(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX) {}
            project.gradle.taskGraph.whenReady {
                if (hasTask(project.matrixTaskPath(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX))) {
                    val metadata = json.encodeToString(project.matrixTests())
                    project.tasks.named<IntelliJPlatformTestMatrixTask>(Tasks.INTELLIJ_PLATFORM_TEST_MATRIX).get().snapshot.set(metadata)
                }
            }
        }
    }
}

private fun Project.matrixTests(): MatrixSnapshot<MatrixTests> {
    val metadata = MatrixMetadata(this)
    val unavailable = mutableListOf(
        Unavailable("testClasspaths", "notResolved", "Test classes, runtime classpaths, dependency providers, and toolchain launchers are not resolved."),
        Unavailable("testResults", "notRequested", "This report does not execute tests or infer results from report directories."),
        Unavailable("plugins", "notResolved", "Only already materialized dependency declarations are listed; deferred and bundled plugin declarations are not evaluated."),
    )
    // UI task configuration creates source-set tasks, which cannot be registered from a lazy task action.
    val sourceSets = extensions.findByType<SourceSetContainer>()
    tasks.withType<TestIdeUiTask>().names.sorted().forEach { sourceSets?.maybeCreate(it) }
    val testTasks = tasks.withType<Test>().toList()
    val performanceTasks = tasks.withType<TestIdePerformanceTask>().toList()
    val declarations = metadata.testing.associateBy { it.taskPath }
    val sources = metadata.sourceSets
    val runtime = metadata.runtime()
    val rows = (testTasks + performanceTasks).sortedBy { it.path }.map { task ->
        val test = task as? Test
        val declaration = declarations[task.path]
        val kind = when (task) {
            is TestIdeUiTask -> "ui"
            is TestIdePerformanceTask -> "performance"
            is TestIdeTask -> "ide"
            else -> if (task.name == Tasks.External.TEST) "ide" else "jvm"
        }
        val needsIde = kind != "jvm"
        val ide = if (needsIde) declaration?.ide ?: metadata.baseIde else null
        val sourceNames = when {
            kind == "performance" -> emptyList()
            sources.any { it.name == task.name } -> listOf(task.name)
            task is TestIdeTask -> listOf("test").filter { name -> sources.any { it.name == name } }
            else -> emptyList()
        }
        val outputs = mutableMapOf<String, MatrixValue>()
        test?.let {
            outputs["junitXml"] = when {
                !it.reports.junitXml.required.get() -> MatrixValue("disabled")
                else -> it.reports.junitXml.outputLocation.orNull?.let { directory -> MatrixValue("configured", directory.asFile.toPath().safePathString) }
                    ?: MatrixValue("unavailable", reason = "The XML report location is not configured.")
            }
            outputs["html"] = when {
                !it.reports.html.required.get() -> MatrixValue("disabled")
                else -> it.reports.html.outputLocation.orNull?.let { directory -> MatrixValue("configured", directory.asFile.toPath().safePathString) }
                    ?: MatrixValue("unavailable", reason = "The HTML report location is not configured.")
            }
        }
        outputs["log"] = matrixTestOutput(test, "idea.log.path", needsIde)
        outputs["screenshots"] = matrixTestOutput(test, "ide.performance.screenshot.before.kill", false)
        if (task is TestIdePerformanceTask) {
            val artifacts = task.artifactsDirectory.orNull
            outputs["artifacts"] = artifacts?.let { MatrixValue("configured", it.asFile.toPath().safePathString) }
                ?: MatrixValue("unavailable", reason = "artifactsDirectory must be configured before running performance tests.")
            listOf("log", "screenshots").forEach {
                outputs[it] = artifacts?.let { directory -> MatrixValue("configured", "${directory.asFile.toPath().safePathString}/{testName}", "Path template: one directory per .ijperf script.") }
                    ?: MatrixValue("unavailable", reason = "artifactsDirectory is not configured.")
            }
        }
        val sandbox = when {
            !needsIde -> MatrixValue("notApplicable")
            declaration != null -> declaration.sandbox
            else -> MatrixValue("notResolved", reason = "Sandbox paths depend on the resolved IDE; sandbox providers are not evaluated.")
        }
        if (ide?.state == "unavailable") {
            unavailable += Unavailable("${task.path}.ide", "unavailable", "No inspectable IntelliJ Platform target is declared. Configure an IDE dependency or a custom testing target.")
        }
        val prerequisites = buildList {
            add(MatrixPrerequisite("compiledTests", "notChecked", "Running this task may compile and instrument plugin and test sources."))
            add(MatrixPrerequisite("dependencies", "notResolved", "Test dependencies and the selected runtime must be available or downloadable when the task runs."))
            if (needsIde) {
                add(MatrixPrerequisite("ide", ide?.state ?: "unavailable", "An IntelliJ Platform dependency or local IDE is required."))
                add(MatrixPrerequisite("sandbox", sandbox.state, "The plugin and its dependencies must be prepared in an isolated sandbox."))
                add(MatrixPrerequisite("testFramework", "notResolved", "IDE test framework and engine dependencies are not resolved by the matrix."))
            }
            if (kind == "ui") {
                add(MatrixPrerequisite("pluginArchive", "notChecked", "UI testing requires the configured plugin archive; running the task may build it."))
                add(MatrixPrerequisite("uiEnvironment", "notChecked", "Starter/Driver tests launch an IDE and may require a graphical session or display server."))
            }
            if (task is TestIdePerformanceTask) {
                add(MatrixPrerequisite("testDataDirectory", if (task.testDataDirectory.isPresent) "configured" else "unavailable", "Configure testDataDirectory with projects and .ijperf scripts."))
                add(MatrixPrerequisite("artifactsDirectory", if (task.artifactsDirectory.isPresent) "configured" else "unavailable", "Configure the performance artifacts destination."))
                add(MatrixPrerequisite("profilerName", if (task.profilerName.isPresent) "configured" else "unavailable", "Configure the performance profiler before execution."))
            }
            if (!task.enabled) add(MatrixPrerequisite("enabled", "disabled", "This task is disabled; its command will not execute tests."))
        }
        MatrixTest(
            task.path,
            when (task) {
                is TestIdeUiTask -> TestIdeUiTask::class.java.name
                is TestIdePerformanceTask -> TestIdePerformanceTask::class.java.name
                is TestIdeTask -> TestIdeTask::class.java.name
                else -> Test::class.java.name
            },
            kind,
            task.enabled,
            when (test?.options) {
                is JUnitPlatformOptions -> "junitPlatform"
                is TestNGOptions -> "testng"
                is JUnitOptions -> "junit4"
                else -> if (kind == "performance") "ijperf" else "unavailable"
            },
            metadata.dependencies(sourceNames.map { if (it == "main") "runtimeClasspath" else "${it}RuntimeClasspath" } + listOfNotNull(
                Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES.takeIf { needsIde },
                declaration?.platformConfiguration?.removePrefix(Configurations.INTELLIJ_PLATFORM_DEPENDENCY)?.let { Configurations.INTELLIJ_PLATFORM_TEST_DEPENDENCIES + it },
            )),
            "notResolved",
            sourceNames,
            if (sourceNames.isEmpty()) "unavailable" else "convention",
            matrixCommand(task.path),
            test != null,
            test?.let { "${matrixCommand(task.path)} --tests '<class-or-method-pattern>'" },
            MatrixTestCosts(startsIde = kind in listOf("ui", "performance"), requiresUi = kind in listOf("ui", "performance")),
            ide,
            runtime,
            if (needsIde) metadata.dependencies(metadata.pluginConfigurations(declaration)) else emptyList(),
            if (needsIde) "notResolved" else "notApplicable",
            sandbox,
            outputs.toSortedMap(),
            prerequisites,
        )
    }
    return MatrixSnapshot(MatrixTests(metadata.module, sources, rows), unavailable = unavailable.sortedBy { it.observation })
}

private fun Project.matrixTestOutput(test: Test?, property: String, ide: Boolean): MatrixValue {
    val raw = test?.systemProperties?.get(property)
    val path = matrixLiteralPath(raw)
    return when {
        path != null -> MatrixValue("configured", path)
        raw != null || ide -> MatrixValue("notResolved", reason = "No literal path is available without evaluating runtime or sandbox providers.")
        else -> MatrixValue("notConfigured", reason = "No output path is declared by this task.")
    }
}