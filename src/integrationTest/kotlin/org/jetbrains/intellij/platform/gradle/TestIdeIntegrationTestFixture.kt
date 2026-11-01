// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

abstract class TestIdeIntegrationTestFixture : IntelliJPlatformIntegrationTestBase(
    resourceName = "test-ide-regressions",
) {

    protected val excludesProperty = GradleProperties.TestIdeBundledPluginsClasspathExcludes.toString()
    protected val defaultExcludes = GradleProperties.TestIdeBundledPluginsClasspathExcludes.defaultValue.split(',')
    private val smokeTests =
        listOf("SmokeTest", "HeavySmokeTest", "FileComparisonTest", "FileTemplateTest", "OptionalDependencyTest")

    protected fun properties(
        type: String = "IU",
        version: String,
        useInstaller: Boolean = true,
        javaRelease: Int = 21,
        testClasses: List<String> = smokeTests,
        expectedBuild: String = "",
        expectedPlugins: List<String> = emptyList(),
        unexpectedPlugins: List<String> = defaultExcludes,
        expectedFileTypes: Map<String, String> = emptyMap(),
        expectedServices: List<String> = emptyList(),
        yamlExpected: Boolean = true,
        bundledPlugins: List<String> = emptyList(),
        testBundledPlugins: List<String> = emptyList(),
        fileComparisonDataExpected: Boolean = false,
    ) = defaultProjectProperties + mapOf(
        "intellijPlatform.type" to type,
        "intellijPlatform.version" to version,
        "useInstaller" to useInstaller,
        "javaRelease" to javaRelease,
        "testClasses" to testClasses.joinToString(","),
        "expectedBuild" to expectedBuild,
        "expectedPlugins" to expectedPlugins.joinToString(","),
        "unexpectedPlugins" to unexpectedPlugins.joinToString(","),
        "expectedFileTypes" to expectedFileTypes.entries.joinToString(",") { "${it.key}:${it.value}" },
        "expectedServices" to expectedServices.joinToString(","),
        "yamlExpected" to yamlExpected,
        "bundledPlugins" to bundledPlugins.joinToString(","),
        "testBundledPlugins" to testBundledPlugins.joinToString(","),
        "fileComparisonDataExpected" to fileComparisonDataExpected,
    )

    protected fun BuildResult.classpathEntries(taskName: String = Tasks.External.TEST) = output.lines()
        .filter { it.startsWith("test-ide-regressions: Test classpath entry [$taskName]: ") }
        .map { it.substringAfter("]: ") }

    protected fun smoke(
        properties: Map<String, Any>,
        vararg tasks: String = arrayOf(Tasks.External.TEST),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ) = build(*tasks, projectProperties = properties, args = args) {
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2119
        assertNotContains("it = com.jetbrains", output)

        // Make sure the expected tests were executed
        val testClasses = properties["testClasses"].toString().split(',').filter(String::isNotEmpty)
        tasks.filterNot { it.startsWith("clean") }.forEach { taskName ->
            assertEquals(TaskOutcome.SUCCESS, task(":$taskName")?.outcome, "expect task '$taskName' to succeed")
            testClasses.forEach {
                dir containsFile "build/test-results/$taskName/TEST-$it.xml"
            }

            // The same JAR added twice may break package-private access or resources lookup:
            // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1640
            // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2137
            val entries = classpathEntries(taskName)
            assert(entries.isNotEmpty()) { "expect classpath entries of task '$taskName' printed" }
            val duplicates = entries.groupBy { it }.filterValues { it.size > 1 }.keys
            assert(duplicates.isEmpty()) { "expect no duplicated classpath entries of task '$taskName': $duplicates" }
        }

        block()
    }

    protected fun BuildResult.assertBundledPluginsSummary(block: (included: Int, all: Int) -> Unit) {
        val match = assertNotNull(
            Regex("Test classpath includes (\\d+) of (\\d+) bundled plugins").find(output),
            "expect the build output contains the bundled plugins summary",
        )
        block(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }
}
