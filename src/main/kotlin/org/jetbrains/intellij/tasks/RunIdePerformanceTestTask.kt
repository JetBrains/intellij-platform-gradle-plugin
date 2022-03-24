package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.createDir
import org.gradle.api.Incubating
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.error
import org.jetbrains.intellij.getIdeJvmArgs
import org.jetbrains.intellij.info
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.model.PerformanceTestResult
import org.jetbrains.intellij.performanceTest.ProfilerName
import org.jetbrains.intellij.performanceTest.TestExecutionFailException
import org.jetbrains.intellij.performanceTest.parsers.IdeaLogParser
import org.jetbrains.intellij.performanceTest.parsers.SimpleIJPerformanceParser
import java.nio.file.Path
import java.nio.file.Paths

@Incubating
open class RunIdePerformanceTestTask : RunIdeBase(true) {

    private val context = logCategory()

    /**
     * Path to directory with test projects and '.ijperf' files.
     */
    @get:Input
    val testDataDir = objectFactory.property<String>()

    /**
     * Path to directory where performance test artifacts (IDE logs, snapshots, screenshots, etc.) will be stored.
     * If directory doesn't exist, it will be created.
     */
    @get:Input
    val artifactsDir = objectFactory.property<String>()

    /**
     * Name of the profiler which will be used while execution.
     * Enum ProfilerName, possible values ASYNC, YOURKIT
     * ASYNC profiler is by default.
     */
    @get:Input
    val profilerName = objectFactory.property<ProfilerName>()

    private lateinit var scriptPath: String

    private lateinit var testArtifactsDirPath: Path

    @TaskAction
    @Override
    override fun exec() {
        val dir = Paths.get(artifactsDir.get()).createDir()
        val testData = Path.of(testDataDir.get()).toFile()
        val testExecutionResults: MutableList<PerformanceTestResult> = mutableListOf()

        testData
            .walk()
            .maxDepth(1)
            .filter { it.extension == "ijperf" }
            .forEach {
                val testName = it.nameWithoutExtension
                val testScript = SimpleIJPerformanceParser(it.path).parse()

                scriptPath = it.toPath().toAbsolutePath().toString()
                testArtifactsDirPath = dir.resolve(testName).createDir().toAbsolutePath()

                // Passing to IDE project to open
                args = listOf("${testDataDir.get()}/${testScript.projectName}")

                super.exec()

                val testResults = IdeaLogParser(testArtifactsDirPath.resolve("idea.log").toString()).getTestStatistic()

                info(context, "Total time ${testResults.totalTime}ms, expected time ms ${testScript.assertionTimeout}ms")

                if (testScript.assertionTimeout != null && testResults.totalTime!! > testScript.assertionTimeout) {
                    testExecutionResults.add(PerformanceTestResult(testName, testResults, testScript))
                }
            }

        if (testExecutionResults.size > 0) {
            testExecutionResults.forEach {
                error(context, "TEST `${it.testName}` FAILED")
                error(context, "Expected time of execution `${it.script.assertionTimeout}ms`, but was ${it.statistic.totalTime}ms")
            }
            throw TestExecutionFailException("${testExecutionResults.size} test(s) failed")
        }
    }

    override fun configureJvmArgs() {
        jvmArgs = getIdeJvmArgs(this, jvmArgs, ideDir.get())
        jvmArgs(
            mutableListOf(
                "-Djdk.attach.allowAttachSelf=true",
                "-Didea.is.integration.test=true",
                "-Djb.privacy.policy.text=<!--999.999-->",
                "-Djb.consents.confirmation.enabled=false",
                "-Didea.local.statistics.without.report=true",
                "-Dlinux.native.menu.force.disable=true",
                "-Didea.fatal.error.notification=true",
                "-Dtestscript.filename=${scriptPath}",
                "-DintegrationTests.profiler=${profilerName.get().name.toLowerCase()}",
                "-Dide.performance.screenshot.before.kill=$testArtifactsDirPath",
                "-Didea.log.path=$testArtifactsDirPath",
                "-Dsnapshots.path=$testArtifactsDirPath",
                "-Dmemory.snapshots.path=$testArtifactsDirPath"
            )
        )
    }
}
