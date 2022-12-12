// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.createDir
import com.jetbrains.plugin.structure.base.utils.extension
import com.jetbrains.plugin.structure.base.utils.nameWithoutExtension
import org.gradle.api.Incubating
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.error
import org.jetbrains.intellij.info
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.model.PerformanceTestResult
import org.jetbrains.intellij.performanceTest.ProfilerName
import org.jetbrains.intellij.performanceTest.TestExecutionFailException
import org.jetbrains.intellij.performanceTest.parsers.IdeaLogParser
import org.jetbrains.intellij.performanceTest.parsers.SimpleIJPerformanceParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

@Incubating
abstract class RunIdePerformanceTestTask : RunIdeBase(true) {

    /**
     * Path to directory with test projects and '.ijperf' files.
     */
    @get:Input
    abstract val testDataDir: Property<String>

    /**
     * Path to directory where performance test artifacts (IDE logs, snapshots, screenshots, etc.) will be stored.
     * If the directory doesn't exist, it will be created.
     */
    @get:Input
    abstract val artifactsDir: Property<String>

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

    private lateinit var scriptPath: String
    private lateinit var testArtifactsDirPath: Path
    private val context = logCategory()

    @TaskAction
    override fun exec() {
        val dir = Paths.get(artifactsDir.get()).createDir()
        val testData = Path.of(testDataDir.get())
        val testExecutionResults = mutableListOf<PerformanceTestResult>()

        Files.walk(testData, 1)
            .filter { it.extension == "ijperf" }
            .forEach {
                val testName = it.nameWithoutExtension
                val testScript = SimpleIJPerformanceParser(it).parse()

                scriptPath = it.toAbsolutePath().toString()
                testArtifactsDirPath = dir.resolve(testName).createDir().toAbsolutePath()

                // Passing to IDE project to open
                args = listOf("${testDataDir.get()}/${testScript.projectName}")

                super.exec()

                IdeaLogParser(testArtifactsDirPath.resolve("idea.log").toString())
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

    /**
     * Configures arguments passed to JVM.
     */
    override fun collectJvmArgs() = super.collectJvmArgs() + mutableListOf(
        "-Djdk.attach.allowAttachSelf=true",
        "-Didea.is.integration.test=true",
        "-Djb.privacy.policy.text=<!--999.999-->",
        "-Djb.consents.confirmation.enabled=false",
        "-Didea.local.statistics.without.report=true",
        "-Dlinux.native.menu.force.disable=true",
        "-Didea.fatal.error.notification=true",
        "-Dtestscript.filename=$scriptPath",
        "-DintegrationTests.profiler=${profilerName.get().name.toLowerCase()}",
        "-Dide.performance.screenshot.before.kill=$testArtifactsDirPath",
        "-Didea.log.path=$testArtifactsDirPath",
        "-Dsnapshots.path=$testArtifactsDirPath",
        "-Dmemory.snapshots.path=$testArtifactsDirPath"
    )
}
