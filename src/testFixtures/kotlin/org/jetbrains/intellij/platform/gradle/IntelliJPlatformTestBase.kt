// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.deleteRecursively
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

abstract class IntelliJPlatformTestBase {

    var debugEnabled = !(System.getenv("CI") ?: "false").toBoolean()
    val gradleDefault = System.getProperty("test.gradle.default")
    val gradleScan = System.getProperty("test.gradle.scan").toBoolean()
    val gradleArguments =
        System.getProperty("test.gradle.arguments", "").split(' ').filter(String::isNotEmpty).toMutableList()
    val kotlinPluginVersion = System.getProperty("test.kotlin.version")
    val gradleVersion = System.getProperty("test.gradle.version").takeUnless { it.isNullOrEmpty() } ?: gradleDefault
    val gradleHome = Path(System.getProperty("test.gradle.home"))
    val isCI get() = System.getProperty("test.ci").toBoolean()

    val intellijPlatformType = System.getProperty("test.intellijPlatform.type").takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.intellijPlatform.type' isn't provided")
    val intellijPlatformVersion = System.getProperty("test.intellijPlatform.version").takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.intellijPlatform.version' isn't provided")
    val markdownPluginVersion = System.getProperty("test.markdownPlugin.version")
        .takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.markdownPlugin.version' isn't provided")

    lateinit var dir: Path

    @BeforeTest
    open fun setup() {
        dir = createTempDirectory("tmp")
        println("Build directory: ${dir.toUri()}")
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    open fun tearDown() {
        dir.deleteRecursively()
    }

    protected inline fun build(
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

    protected inline fun buildAndFail(
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

    protected inline fun build(
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

    protected fun builder(
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
            .withDebug(debugEnabled)
            .withTestKitDir(gradleHome.toFile())
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
            )

    /**
     * Disables debugging by setting the [debugEnabled] to `false`.
     * Gradle runs Ant with another Java, that leads to NoSuchMethodError during the instrumentation.
     */
    protected fun disableDebug() {
        println("Debugging is disabled for test.")
        debugEnabled = false
    }

    protected inline fun BuildResult.assertLogValue(label: String, block: (String) -> Unit): String {
        assertContains(label, output)
        return output
            .lineSequence()
            .filter { it.contains(label) }
            .map { it.substringAfter(label).trim() }
            .toList()
            .let { lines ->
                assertEquals(1, lines.size, "Expected only one log line containing: $label")
                lines
                    .first()
                    .removePrefix(label)
                    .trim()
                    .also(block)
            }
    }
}
