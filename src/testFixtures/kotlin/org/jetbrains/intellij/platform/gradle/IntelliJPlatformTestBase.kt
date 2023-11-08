// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.internal.DefaultGradleRunner
import org.intellij.lang.annotations.Language
import java.io.File
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertFalse

abstract class IntelliJPlatformTestBase {

    var debugEnabled = true
    val gradleDefault = System.getProperty("test.gradle.default")
    val gradleScan = System.getProperty("test.gradle.scan").toBoolean()
    val gradleArguments = System.getProperty("test.gradle.arguments", "").split(' ').filter(String::isNotEmpty).toMutableList()
    val kotlinPluginVersion: String = System.getProperty("test.kotlin.version")
    val gradleVersion: String = System.getProperty("test.gradle.version").takeUnless { it.isNullOrEmpty() } ?: gradleDefault
    val isCI get() = System.getProperty("test.ci").toBoolean()

    val gradleHome: String = System.getProperty("test.gradle.home")
    var dir = createTempDirectory("tmp")

    @BeforeTest
    open fun setup() {
        dir = createTempDirectory("tmp")
    }

    protected fun build(
        vararg tasksList: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ) = build(
        tasks = tasksList,
        projectProperties = projectProperties,
        systemProperties = systemProperties,
        args = args,
        block = block,
    )

    protected fun buildAndFail(
        vararg tasksList: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ) = build(
        fail = true,
        tasks = tasksList,
        projectProperties = projectProperties,
        systemProperties = systemProperties,
        args = args,
        block = block,
    )

    protected fun build(
        gradleVersion: String = this.gradleVersion,
        fail: Boolean = false,
        assertValidConfigurationCache: Boolean = true,
        vararg tasks: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ): BuildResult = builder(
        gradleVersion = gradleVersion,
        tasks = tasks,
        projectProperties = projectProperties,
        systemProperties = systemProperties,
        args = args,
    )
        .run {
            when (fail) {
                true -> buildAndFail()
                false -> build()
            }
        }
        .also {
            if (assertValidConfigurationCache) {
                assertNotContains("Configuration cache problems found in this build.", it.output)
            }
        }
        .also(block)

    private fun builder(
        gradleVersion: String,
        vararg tasks: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        args: List<String> = emptyList(),
    ) =
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withGradleVersion(gradleVersion)
            .forwardOutput()
            .withPluginClasspath()
//            .withDebug(debugEnabled)
            .withTestKitDir(File(gradleHome))
            .withArguments(
                "-Dorg.gradle.kotlin.dsl.scriptCompilationAvoidance=false", // workaround for https://github.com/gradle/gradle/issues/25412
                *projectProperties
                    .run { this + mapOf("platformVersion" to System.getenv("PLATFORM_VERSION")).filterNot { it.value == null } }
                    .map { "-P${it.key}=${it.value}" }
                    .toTypedArray(),
                *systemProperties
                    .map { "-D${it.key}=${it.value}" }
                    .toTypedArray(),
                *tasks,
                *listOfNotNull(
                    "--stacktrace",
                    "--configuration-cache",
                    "--scan".takeIf { gradleScan },
                ).toTypedArray(),
                *gradleArguments.toTypedArray(),
                *args.toTypedArray(),
            )//, "-Dorg.gradle.debug=true")

    private fun getPluginClasspath(): List<File> {
        //Get the default classpath
        val defaultClasspath = DefaultGradleRunner()
            .withProjectDir(dir.toFile())
            .withPluginClasspath()
            .pluginClasspath

        //Replace the Gradle classpath with the IntelliJ one
        if (System.getProperty("IntelliJClasspath") != null) {
            return defaultClasspath
                .filterNot { it.absolutePath.contains("classes") }
                .plus(Paths.get("./out/production/classes").toFile())
        }
        return defaultClasspath
    }

    protected fun assertNotContains(expected: String, actual: String) {
        // https://stackoverflow.com/questions/10934743/formatting-output-so-that-intellij-idea-shows-diffs-for-two-texts
        assertFalse(
            actual.contains(expected),
            """
            expected:<$expected> but was:<$actual>
            """.trimIndent()
        )
    }

    protected fun assertFileContent(file: Path?, @Language("xml") expectedContent: String) =
        assertEquals(expectedContent.trim(), file?.readText()?.replace("\r", "")?.trim())

    protected fun assertFileContent(file: File?, @Language("xml") expectedContent: String) =
        assertEquals(expectedContent.trim(), file?.readText()?.replace("\r", "")?.trim())

    protected val BuildResult.safeOutput: String
        get() = output.replace("\r", "")

    protected val BuildResult.safeLogs: String
        get() = safeOutput.lineSequence().filterNot { it.startsWith("[gradle-intellij-plugin") }.joinToString("\n")
}
