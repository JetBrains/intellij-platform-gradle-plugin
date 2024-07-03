// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.Incubating
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.argumentProviders.PerformanceTestArgumentProvider
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.models.PerformanceTestResult
import org.jetbrains.intellij.platform.gradle.performanceTest.ProfilerName
import org.jetbrains.intellij.platform.gradle.performanceTest.TestExecutionFailException
import org.jetbrains.intellij.platform.gradle.performanceTest.parsers.IdeaLogParser
import org.jetbrains.intellij.platform.gradle.performanceTest.parsers.SimpleIJPerformanceParser
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.RunnableIdeAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.TestableAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.nio.file.Files
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension

/**
 * Runs performance tests on the IDE with the developed plugin installed.
 *
 * This task runs against the IntelliJ Platform and plugins specified in project dependencies.
 * To register a customized task, use [IntelliJPlatformTestingExtension.testIdePerformance] instead.
 *
 * The [TestIdePerformanceTask] task extends the [RunIdeBase] task, so all configuration attributes of [JavaExec] and [RunIdeTask] tasks can be used in the [TestIdePerformanceTask] as well.
 * See [RunIdeTask] task for more details.
 *
 * Currently, the task is under adaptation; more documentation will be added in the future.
 *
 * @see RunIdeTask
 * @see JavaExec
 */
@Deprecated(message = "CHECK")
@Incubating
@UntrackedTask(because = "Should always run")
abstract class TestIdePerformanceTask : JavaExec(), RunnableIdeAware, TestableAware, IntelliJPlatformVersionAware {

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

    private val log = Logger(javaClass)

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
                        log.info("Total time ${testResults.totalTime}ms, expected time ms ${testScript.assertionTimeout}ms")

                        if (testScript.assertionTimeout != null && testResults.totalTime!! > testScript.assertionTimeout) {
                            testExecutionResults.add(PerformanceTestResult(testName, testResults, testScript))
                        }
                    }
            }

        if (testExecutionResults.isNotEmpty()) {
            testExecutionResults.forEach {
                log.error("TEST `${it.testName}` FAILED")
                log.error("Expected time of execution `${it.script.assertionTimeout}ms`, but was ${it.statistic.totalTime}ms")
            }
            throw TestExecutionFailException("${testExecutionResults.size} test(s) failed")
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Runs performance tests on the IDE with the developed plugin installed."
    }

    companion object : Registrable {

        internal val configuration: TestIdePerformanceTask.() -> Unit = {
            val prepareTestIdePerformanceSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_TEST_IDE_PERFORMANCE_SANDBOX)
            applySandboxFrom(prepareTestIdePerformanceSandboxTaskProvider)

//                artifactsDirectory.convention(extension.type.flatMap { type ->
//                    extension.version.flatMap { version ->
//                        project.layout.buildDirectory.dir(
//                            "reports/performance-test/$type$version-${project.version}-${
//                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
//                            }"
//                        ).map { it.toString() }
//                    }
//                })
//                profilerName.convention(ProfilerName.ASYNC)

            // Check that the `runIdePerformanceTest` task was launched
            // Check that `performanceTesting.jar` is absent (that means it's a community version)
            // Check that user didn't pass a custom version of the performance plugin
//                if (
//                    RUN_IDE_PERFORMANCE_TEST_TASK_NAME in project.gradle.startParameter.taskNames
//                    && extension.plugins.get().none { it is String && it.startsWith(PERFORMANCE_PLUGIN_ID) }
//                ) {
//                    val bundledPlugins = BuiltinPluginsRegistry.resolveBundledPlugins(ideaDependencyProvider.get().classes.toPath(), context)
//                    if (!bundledPlugins.contains(PERFORMANCE_PLUGIN_ID)) {
//                        val buildNumber = ideaDependencyProvider.get().buildNumber
//                        val resolvedPlugin = resolveLatestPluginUpdate(PERFORMANCE_PLUGIN_ID, buildNumber)
//                            ?: throw BuildException("No suitable plugin update found for $PERFORMANCE_PLUGIN_ID:$buildNumber")
//
//                        val plugin = resolver.resolve(project, resolvedPlugin)
//                            ?: throw BuildException(with(resolvedPlugin) { "Failed to resolve plugin $id:$version@$channel" })
//
//                        configurePluginDependency(project, plugin, extension, this, resolver)
//                    }
//                }
        }

        override fun register(project: Project) =
            project.registerTask<TestIdePerformanceTask>(Tasks.TEST_IDE_PERFORMANCE, configureWithType = false, configuration = configuration)
    }

//    private fun resolveLatestPluginUpdate(pluginId: String, buildNumber: String, channel: String = "") =
//        PluginRepositoryFactory.create(IntelliJPluginConstants.Locations.MARKETPLACE)
//            .pluginManager
//            .searchCompatibleUpdates(listOf(pluginId), buildNumber, channel)
//            .firstOrNull()
//            ?.let { PluginDependencyNotation(it.pluginXmlId, it.version, it.channel) }

    //    private fun configureDownloadRobotServerPluginTask(project: Project) {
//        info("Configuring robot-server download Task")
//
//        project.tasks.register<DownloadRobotServerPluginTask>(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
//        project.tasks.withType<DownloadRobotServerPluginTask> {
//            val taskContext = logCategory()
//
//            version.convention(VERSION_LATEST)
//            outputDir.convention(project.layout.buildDirectory.dir("robotServerPlugin"))
//            pluginArchive.convention(project.provider {
//                val resolvedVersion = resolveRobotServerPluginVersion(version.orNull)
//                val (group, name) = getDependency(resolvedVersion).split(':')
//                dependenciesDownloader.downloadFromRepository(taskContext, {
//                    create(
//                        group = group,
//                        name = name,
//                        version = resolvedVersion,
//                    )
//                }, {
//                    mavenRepository(INTELLIJ_DEPENDENCIES) {
//                        content { includeGroup(group) }
//                    }
//                }).first()
//            })
//        }
//    }
}
