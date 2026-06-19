// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.UntrackedTask
import org.gradle.process.ExecResult
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.aware.frontendProcessPluginsDirectory
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import org.jetbrains.intellij.platform.gradle.utils.writeTextIfChanged
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readText

@UntrackedTask(because = "Should always run")
abstract class RunIdeSplitModeTask : RunIdeTask() {

    private val log = Logger(javaClass)

    private val splitModeFrontendSandboxConfigDirectory: DirectoryProperty = project.objects.directoryProperty()

    private val splitModeFrontendSandboxPluginsDirectory: DirectoryProperty = project.objects.directoryProperty()

    private val splitModeFrontendSandboxSystemDirectory: DirectoryProperty = project.objects.directoryProperty()

    private val splitModeFrontendSandboxLogDirectory: DirectoryProperty = project.objects.directoryProperty()

    private val splitModeFrontendPropertiesFile: RegularFileProperty = project.objects.fileProperty()

    internal fun applySplitModeFrontendSandboxFrom(sandboxProducerTaskProvider: TaskProvider<PrepareSandboxTask>) {
        splitModeFrontendSandboxConfigDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.sandboxConfigFrontendDirectory })
            .finalizeValueOnRead()
        splitModeFrontendSandboxPluginsDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.frontendProcessPluginsDirectory() })
            .finalizeValueOnRead()
        splitModeFrontendSandboxSystemDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.sandboxSystemFrontendDirectory })
            .finalizeValueOnRead()
        splitModeFrontendSandboxLogDirectory
            .convention(sandboxProducerTaskProvider.flatMap { it.sandboxLogFrontendDirectory })
            .finalizeValueOnRead()
        splitModeFrontendPropertiesFile
            .convention(sandboxProducerTaskProvider.flatMap {
                it.sandboxDirectory.file("${Tasks.RUN_IDE_SPLIT_MODE}-frontend.properties")
            })
            .finalizeValueOnRead()

        dependsOn(sandboxProducerTaskProvider)
    }

    @TaskAction
    override fun exec() {
        val backendFailure = AtomicReference<Throwable>()
        val backendLaunched = AtomicBoolean(false)
        var frontendFinishedSuccessfully = false

        executionMode.set(ExecutionMode.SPLIT_MODE_BACKEND)
        prepareIdeExecution()

        val backendThread = thread(name = "split-mode-backend-launcher") {
            runCatching {
                runSplitModeBackend {
                    val result = execOperations.javaexec {
                        copyJavaExecSpecTo(this)
                        isIgnoreExitValue = true
                    }
                    assertExpectedSplitModeExitCode("backend", result, backendLaunched.get())
                }
            }.onFailure {
                backendFailure.set(it)
            }
        }

        try {
            val frontendJoinLink = waitForSplitModeFrontendJoinLink(backendFailure)
            backendLaunched.set(true)

            backendFailure.get()?.let { throw it }

            setArgs(emptyList<String>())
            executionMode.set(ExecutionMode.SPLIT_MODE_FRONTEND)
            val frontendSandboxPaths = SplitModeSandboxPaths(
                configDirectory = splitModeFrontendSandboxConfigDirectory,
                pluginsDirectory = splitModeFrontendSandboxPluginsDirectory,
                systemDirectory = splitModeFrontendSandboxSystemDirectory,
                logDirectory = splitModeFrontendSandboxLogDirectory,
            )
            prepareIdeExecution(frontendJoinLink, frontendSandboxPaths)
            writeSplitModeFrontendPropertiesFile()

            val result = execOperations.javaexec {
                copyJavaExecSpecTo(this)
                disableSplitModeFrontendJavaDebuggingIfNeeded()
                useSplitModeFrontendPropertiesFile()
                isIgnoreExitValue = true
            }
            assertExpectedSplitModeExitCode("frontend", result, launched = true)
            backendFailure.get()?.let { throw it }
            frontendFinishedSuccessfully = true
        } catch (error: Throwable) {
            backendFailure.get()
                ?.takeIf { it !== error }
                ?.let(error::addSuppressed)
            throw error
        } finally {
            log.info("Waiting for split-mode backend process to exit.")
            when {
                frontendFinishedSuccessfully -> backendThread.join()
                else -> backendThread.join(2_000)
            }
            backendFailure.get()?.let { throw it }
        }
    }

    private fun assertExpectedSplitModeExitCode(processName: String, result: ExecResult, launched: Boolean) {
        when (val exitValue = result.exitValue) {
            0 -> Unit
            EXPECTED_SPLIT_MODE_SHUTDOWN_EXIT_CODE -> when {
                launched -> log.lifecycle("Split-mode $processName process exited with expected shutdown code $exitValue.")
                else -> result.assertNormalExitValue()
            }

            else -> result.assertNormalExitValue()
        }
    }

    private fun org.gradle.process.JavaExecSpec.useSplitModeFrontendPropertiesFile() {
        val frontendPropertiesArgument = "-Didea.properties.file=${splitModeFrontendPropertiesFile.asPath.safePathString}"
        setJvmArgs(jvmArgs.orEmpty().filterNot { it.startsWith("-Didea.properties.file=") } + frontendPropertiesArgument)
    }

    private fun org.gradle.process.JavaExecSpec.disableSplitModeFrontendJavaDebuggingIfNeeded() {
        val jvmArgsWithoutDebugging = jvmArgs.orEmpty().filterNot { it.isJavaDebuggingArgument() }
        val hadJavaDebuggingArgs = jvmArgsWithoutDebugging.size != jvmArgs.orEmpty().size
        val hadDebugOptionsEnabled = debugOptions.enabled.orNull == true

        if (!hadJavaDebuggingArgs && !hadDebugOptionsEnabled) {
            return
        }

        debugOptions.enabled.set(false)
        setJvmArgs(jvmArgsWithoutDebugging)
        log.lifecycle(
            "Java debugging is disabled for the frontend process launched by `${Tasks.RUN_IDE_SPLIT_MODE}`. " +
                "Use `${Tasks.RUN_IDE_FRONTEND}` when frontend JVM debugging is needed."
        )
    }

    private fun String.isJavaDebuggingArgument() = startsWith("-agentlib:jdwp=") || startsWith("-Xrunjdwp:")

    private fun writeSplitModeFrontendPropertiesFile() {
        val propertiesPath = splitModeFrontendPropertiesFile.asPath
        propertiesPath.parent.createDirectories()
        propertiesPath.writeTextIfChanged(
            """
            idea.config.path=${splitModeFrontendSandboxConfigDirectory.asPath.safePathString}
            idea.system.path=${splitModeFrontendSandboxSystemDirectory.asPath.safePathString}
            idea.log.path=${splitModeFrontendSandboxLogDirectory.asPath.safePathString}
            idea.plugins.path=${splitModeFrontendSandboxPluginsDirectory.asPath.safePathString}
            """.trimIndent()
        )
    }

    private fun waitForSplitModeFrontendJoinLink(backendFailure: AtomicReference<Throwable>): String {
        val joinLinkPath = splitModeFrontendJoinLinkFile.asPath
        log.info("Waiting for split-mode frontend join link at '${joinLinkPath.safePathString}'.")

        val deadline = System.nanoTime() + JOIN_LINK_WAIT_TIMEOUT_MS * 1_000_000
        while (System.nanoTime() < deadline) {
            backendFailure.get()?.let { throw it }

            if (joinLinkPath.exists()) {
                joinLinkPath.readText()
                    .trim()
                    .takeIf { it.isNotEmpty() }
                    ?.let { return it }
            }

            try {
                Thread.sleep(JOIN_LINK_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }
        }

        backendFailure.get()?.let { throw it }
        throw org.gradle.api.InvalidUserDataException(
            "No split-mode frontend join link available after waiting ${JOIN_LINK_WAIT_TIMEOUT_MS / 1000}s. " +
                "Expected file: ${joinLinkPath.safePathString}."
        )
    }

    private companion object {
        private const val EXPECTED_SPLIT_MODE_SHUTDOWN_EXIT_CODE = 193
        private const val JOIN_LINK_WAIT_TIMEOUT_MS = 30_000L
        private const val JOIN_LINK_POLL_INTERVAL_MS = 200L
    }
}
