// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Incubating
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.argumentProviders.PerformanceTestArgumentProvider
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.error
import org.jetbrains.intellij.platform.gradle.info
import org.jetbrains.intellij.platform.gradle.logCategory
import org.jetbrains.intellij.platform.gradle.model.PerformanceTestResult
import org.jetbrains.intellij.platform.gradle.performanceTest.ProfilerName
import org.jetbrains.intellij.platform.gradle.performanceTest.TestExecutionFailException
import org.jetbrains.intellij.platform.gradle.performanceTest.parsers.IdeaLogParser
import org.jetbrains.intellij.platform.gradle.performanceTest.parsers.SimpleIJPerformanceParser
import org.jetbrains.intellij.platform.gradle.tasks.base.RunIdeBase
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/**
 * Runs performance tests on the IDE with the developed plugin installed.
 *
 * The [RunIdePerformanceTestTask] task extends the [RunIdeBase] task, so all configuration attributes of [JavaExec] and [RunIdeTask] tasks can be used in the [RunIdePerformanceTestTask] as well.
 * See [RunIdeTask] task for more details.
 *
 * Currently, the task is under adaptation; more documentation will be added in the future.
 *
 * @see [RunIdeTask]
 * @see [RunIdeBase]
 * @see [JavaExec]
 */
@Deprecated(message = "CHECK")
@Incubating
@UntrackedTask(because = "Should always run IDE for performance tests")
abstract class RunIdePerformanceTestTask : RunIdeBase() {

    /**
     * Path to directory with test projects and '.ijperf' files.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val testDataDirectory: DirectoryProperty

    /**
     * Path to the directory where performance test artifacts (IDE logs, snapshots, screenshots, etc.) will be stored.
     * If the directory doesn't exist, it will be created.
     */
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val artifactsDirectory: DirectoryProperty

    /**
     * Name of the profiler which will be used during execution.
     *
     * Default value: [ProfilerName.ASYNC]
     *
     * Acceptable values:
     * - [ProfilerName.ASYNC]
     * - [ProfilerName.YOURKIT]
     */
    @get:Input
    abstract val profilerName: Property<ProfilerName>

    private val context = logCategory()

    init {
        group = PLUGIN_GROUP_NAME
        description = "Runs performance tests on the IDE with the developed plugin installed."
    }

    @TaskAction
    override fun exec() {
        val dir = artifactsDirectory.asPath
        val testData = testDataDirectory.asPath
        val testExecutionResults = mutableListOf<PerformanceTestResult>()

        Files.walk(testData, 1)
            .filter { it.extension == "ijperf" }
            .forEach { scriptPath ->
                val testName = scriptPath.nameWithoutExtension
                val testScript = SimpleIJPerformanceParser(scriptPath).parse()
                val testArtifactsDirPath = dir.resolve(testName).createDirectories()

                // Passing to the IDE project to open
                args = listOf("${testDataDirectory.get()}/${testScript.projectName}")

                jvmArgumentProviders.add(
                    PerformanceTestArgumentProvider(
                        scriptPath,
                        testArtifactsDirPath,
                        profilerName.get().name.lowercase(),
                    )
                )
                super.exec()

                IdeaLogParser(testArtifactsDirPath.resolve("idea.log").toAbsolutePath().toString())
                    .getTestStatistic()
                    .let { testResults ->
                        info(context, "Total time ${testResults.totalTime}ms, expected time ms ${testScript.assertionTimeout}ms")

                        if (testScript.assertionTimeout != null && testResults.totalTime!! > testScript.assertionTimeout) {
                            testExecutionResults.add(PerformanceTestResult(testName, testResults, testScript))
                        }
                    }
            }

        if (testExecutionResults.isNotEmpty()) {
            testExecutionResults.forEach {
                error(context, "TEST `${it.testName}` FAILED")
                error(context, "Expected time of execution `${it.script.assertionTimeout}ms`, but was ${it.statistic.totalTime}ms")
            }
            throw TestExecutionFailException("${testExecutionResults.size} test(s) failed")
        }
    }
}
