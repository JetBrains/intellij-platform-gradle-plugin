// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.GradleException
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildResultException
import java.lang.management.ManagementFactory
import java.nio.file.Path
import kotlin.io.path.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

abstract class IntelliJPlatformTestBase {

    val isCI = (System.getenv("CI") ?: "false").toBoolean()
    // Keep TestKit debugging opt-in. Enabling it for every local run adds significant overhead.
    private val testKitDebugEnabled = System.getProperty("test.gradle.debug")
        ?.toBoolean()
        ?: false
    private val testKitOutputForwardingEnabled = System.getProperty("test.gradle.forwardOutput")
        ?.toBoolean()
        ?: false
    private val printBuildDirectory = System.getProperty("test.gradle.printBuildDirectory")
        ?.toBoolean()
        ?: false
    val isDebugged by lazy {
        ManagementFactory.getRuntimeMXBean().inputArguments.toString().indexOf("-agentlib:jdwp") > 0
    }
    var debugEnabled = testKitDebugEnabled
    val gradleDefault = System.getProperty("test.gradle.default")
    val gradleScan = System.getProperty("test.gradle.scan").toBoolean()
    val gradleArguments =
        System.getProperty("test.gradle.arguments", "").split(' ').filter(String::isNotEmpty).toMutableList()
    val kotlinPluginVersion = System.getProperty("test.kotlin.version")
    val gradleVersion = System.getProperty("test.gradle.version").takeUnless { it.isNullOrEmpty() } ?: gradleDefault
    val gradleHome = Path(System.getProperty("test.gradle.home")).createDirectories()
    val testKitDir = gradleHome.resolve(".testKit").createDirectories()
    val intellijPlatformCacheDir = gradleHome.resolve(".intellijPlatform").createDirectories()
    val idesCacheDir = intellijPlatformCacheDir.resolve("ides").createDirectories()

    val intellijPlatformType = System.getProperty("test.intellijPlatform.type").takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.intellijPlatform.type' isn't provided")
    val intellijPlatformVersion = System.getProperty("test.intellijPlatform.version").takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.intellijPlatform.version' isn't provided")
    val intellijPlatformBuildNumber = System.getProperty("test.intellijPlatform.buildNumber").takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.intellijPlatform.buildNumber' isn't provided")
    val markdownPluginVersion = System.getProperty("test.markdownPlugin.version")
        .takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.markdownPlugin.version' isn't provided")

    lateinit var dir: Path

    @BeforeTest
    open fun setup() {
        dir = createTempDirectory("tmp")
        if (printBuildDirectory) {
            println("Build directory: ${dir.toUri()}")
        }
    }

    @OptIn(ExperimentalPathApi::class)
    @AfterTest
    open fun tearDown() {
        dir.deleteRecursively()
    }

    protected fun build(
        vararg tasksList: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        environment: Map<String, String?> = emptyMap(),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ) = build(
        tasks = tasksList,
        projectProperties = projectProperties,
        systemProperties = systemProperties,
        environment = environment,
        args = args,
        block = block,
    )

    protected fun buildAndFail(
        vararg tasksList: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        environment: Map<String, String?> = emptyMap(),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ) = build(
        fail = true,
        tasks = tasksList,
        projectProperties = projectProperties,
        systemProperties = systemProperties,
        environment = environment,
        args = args,
        block = block,
    )

    protected fun buildWithConfigurationCache(
        vararg tasksList: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        environment: Map<String, String?> = emptyMap(),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ) = build(
        tasks = tasksList,
        projectProperties = projectProperties,
        systemProperties = systemProperties,
        environment = environment,
        args = withConfigurationCache(args),
        block = block,
    )

    protected fun buildAndFailWithConfigurationCache(
        vararg tasksList: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        environment: Map<String, String?> = emptyMap(),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ) = build(
        fail = true,
        tasks = tasksList,
        projectProperties = projectProperties,
        systemProperties = systemProperties,
        environment = environment,
        args = withConfigurationCache(args),
        block = block,
    )

    protected fun build(
        gradleVersion: String = this.gradleVersion,
        fail: Boolean = false,
        assertValidConfigurationCache: Boolean = true,
        vararg tasks: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        environment: Map<String, String?> = emptyMap(),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ): BuildResult {
        val configurationCacheEnabled = (gradleArguments + args).contains(CONFIGURATION_CACHE_ARGUMENT)
        var buildResult: BuildResult? = null

        return runCatching {
            buildResult = builder(
                gradleVersion = gradleVersion,
                tasks = tasks,
                projectProperties = projectProperties,
                systemProperties = systemProperties,
                environment = environment,
                args = args,
            )
                .run {
                    when (fail) {
                        true -> buildAndFail()
                        false -> build()
                    }
                }

            buildResult!!
                .also {
                    if (configurationCacheEnabled && assertValidConfigurationCache) {
                        assertNotContains("Configuration cache problems found in this build.", it.output)
                    }
                }
                .also(block)
        }.getOrElse { throwable ->
            val diagnosticBuildResult = buildResult ?: (throwable as? UnexpectedBuildResultException)?.buildResult
            printBuildFailureDiagnostics(
                throwable = throwable,
                gradleVersion = gradleVersion,
                tasks = tasks.toList(),
                projectProperties = projectProperties,
                systemProperties = systemProperties,
                args = args,
                buildResult = diagnosticBuildResult,
            )
            throw throwable
        }
    }

    protected fun builder(
        gradleVersion: String,
        vararg tasks: String,
        projectProperties: Map<String, Any> = emptyMap(),
        systemProperties: Map<String, Any> = emptyMap(),
        environment: Map<String, String?> = emptyMap(),
        args: List<String> = emptyList(),
    ) =
        GradleRunner.create()
            .withProjectDir(dir.toFile())
            .withGradleVersion(gradleVersion)
            .withPluginClasspath()
            // Gradle TestKit forks the build process when the environment is customized.
            .withDebug(debugEnabled && environment.isEmpty())
            .withTestKitDir(testKitDir.toFile())
            .run {
                when (testKitOutputForwardingEnabled) {
                    true -> forwardOutput()
                    false -> this
                }
            }
            .run {
                when (environment.isNotEmpty()) {
                    true -> withEnvironment(
                        System.getenv().toMutableMap().apply {
                            environment.forEach { (key, value) ->
                                when (value) {
                                    null -> remove(key)
                                    else -> put(key, value)
                                }
                            }
                        },
                    )

                    false -> this
                }
            }
            .withArguments(
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
                    "--scan".takeIf { gradleScan },
                ).toTypedArray(),
                *gradleArguments.toTypedArray(),
                *args.toTypedArray(),
            )

    protected fun BuildResult.assertConfigurationCacheReused() {
        assertContains("Reusing configuration cache.", output)
    }

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

    private fun withConfigurationCache(args: List<String>) =
        when {
            args.contains(CONFIGURATION_CACHE_ARGUMENT) -> args
            else -> listOf(CONFIGURATION_CACHE_ARGUMENT) + args
        }

    private companion object {
        const val CONFIGURATION_CACHE_ARGUMENT = "--configuration-cache"
    }

    @PublishedApi
    internal fun printBuildFailureDiagnostics(
        throwable: Throwable,
        gradleVersion: String,
        tasks: List<String>,
        projectProperties: Map<String, Any>,
        systemProperties: Map<String, Any>,
        args: List<String>,
        buildResult: BuildResult?,
    ) {
        val diagnostics = buildString {
            appendLine()
            appendLine("=== TestKit Failure Diagnostics Start ===")
            appendLine("Directory: $dir")
            appendLine("Gradle version: $gradleVersion")
            appendLine("Tasks: ${tasks.joinToString(separator = " ")}")
            if (projectProperties.isNotEmpty()) {
                appendLine("Project properties: $projectProperties")
            }
            if (systemProperties.isNotEmpty()) {
                appendLine("System properties: $systemProperties")
            }
            if (args.isNotEmpty()) {
                appendLine("Additional arguments: ${args.joinToString(separator = " ")}")
            }
            appendLine("Failure: ${throwable::class.qualifiedName}: ${throwable.message}")

            collectDiagnosticFiles().takeIf { it.isNotEmpty() }?.let { files ->
                appendLine("Relevant files:")
                files.forEach { (path, content) ->
                    appendLine("  - $path")
                    appendLine(content.prependIndent("      "))
                }
            }

            buildResult?.output?.takeIf { it.isNotBlank() }?.let { output ->
                appendLine("--- Nested Gradle Output Start ---")
                appendLine(output)
                appendLine("--- Nested Gradle Output End ---")
            }
            appendLine("=== TestKit Failure Diagnostics End ===")
        }

        System.err.println(diagnostics)
    }

    @PublishedApi
    internal fun collectDiagnosticFiles(): List<Pair<Path, String>> {
        val interestingNames = setOf("split-mode-frontend.join.link", "frontend.properties", "java", "java.bat")

        return dir.toFile()
            .walkTopDown()
            .filter { it.isFile && it.name in interestingNames }
            .map { file ->
                val content = runCatching { file.readText() }
                    .getOrElse { "<unreadable: ${it.message}>" }
                    .ifBlank { "<empty>" }
                file.toPath() to content
            }
            .toList()
    }
}
