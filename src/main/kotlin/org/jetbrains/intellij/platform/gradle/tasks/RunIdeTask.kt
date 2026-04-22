// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.api.tasks.options.Option
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.named
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.argumentProviders.ComposeHotReloadArgumentProvider
import org.jetbrains.intellij.platform.gradle.argumentProviders.SplitModeArgumentProvider
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeDirectory
import org.jetbrains.intellij.platform.gradle.tasks.aware.*
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.safePathString
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.concurrent.TimeUnit.MILLISECONDS
import kotlin.io.path.*

internal const val SPLIT_MODE_FRONTEND_COMMAND = "thinClient"
private const val SPLIT_MODE_BACKEND_COMMAND = "serverMode"
private const val SPLIT_MODE_FRONTEND_ENV_VAR_BASE_NAME = "JETBRAINS_CLIENT"
internal const val SPLIT_MODE_FRONTEND_MAIN_CLASS = "com.intellij.platform.runtime.loader.IntellijLoader"
private const val DEFAULT_SPLIT_MODE_SERVER_PORT = 5990
private const val DEFAULT_JVM_DEBUG_PORT = 5005
private const val DEFAULT_SPLIT_MODE_FRONTEND_DEBUG_PORT = 5007
private const val SPLIT_MODE_JOIN_LINK_MARKER = "Join link:"
private const val SPLIT_MODE_JOIN_LINK_WAIT_TIMEOUT_MS = 30_000L
private const val SPLIT_MODE_JOIN_LINK_POLL_INTERVAL_MS = 200L
private const val SPLIT_MODE_REFRESH_TOKEN_ARGUMENT = "--refresh-split-mode-token=true"
private const val SPLIT_MODE_HOST_PASSWORD_ENV = "CWM_HOST_PASSWORD"
private const val SPLIT_MODE_CLIENT_PASSWORD_ENV = "CWM_CLIENT_PASSWORD"
private const val SPLIT_MODE_NO_TIMEOUTS_ENV = "CWM_NO_TIMEOUTS"
private const val SPLIT_MODE_SHARED_PASSWORD = "qwerty123"
internal const val PURGE_OLD_LOG_DIRECTORIES_OPTION = "purge-old-log-directories"

/**
 * Runs the IDE instance using the currently selected IntelliJ Platform with the built plugin loaded.
 * It directly extends the [JavaExec] Gradle task, which allows for an extensive configuration (system properties, memory management, etc.).
 *
 * This task runs against the IntelliJ Platform and plugins specified in project dependencies.
 * To register a customized task, use [IntelliJPlatformTestingExtension.runIde].
 */
@UntrackedTask(because = "Should always run")
abstract class RunIdeTask : JavaExec(), RunnableIdeAware, SplitModeAware, PluginInstallationTargetAware, IntelliJPlatformVersionAware,
    ComposeHotReloadAware {

    private val log = Logger(javaClass)

    @get:Internal
    abstract val executionMode: Property<ExecutionMode>

    /**
     * The backend port used by split-mode source-like tasks.
     */
    @get:Input
    abstract val splitModeServerPort: Property<Int>

    /**
     * Advanced override for the join link passed to the frontend task when launched directly.
     *
     * Usually, setting [splitModeServerPort] is enough.
     */
    @get:Optional
    @get:Input
    abstract val splitModeFrontendJoinLink: Property<String>

    @get:Internal
    abstract val splitModeFrontendBootstrapClasspath: ConfigurableFileCollection

    /**
     * Removes stale log directories before launching `runIde`, `runIdeBackend`, or `runIdeFrontend`.
     *
     * Default value: `false`
     */
    @get:Input
    abstract val purgeOldLogDirectories: Property<Boolean>

    @Option(
        option = PURGE_OLD_LOG_DIRECTORIES_OPTION,
        description = "Removes stale sandbox log directories before launching the IDE."
    )
    fun setPurgeOldLogDirectoriesOption(value: Boolean) {
        purgeOldLogDirectories.set(value)
    }

    /**
     * Executes the task, configures, and runs the IDE.
     */
    @TaskAction
    override fun exec() {
        validateIntelliJPlatformVersion()
        validateSplitModeSupport()

        workingDir = platformPath.toFile()
        purgeOldLogDirectoriesIfRequested()

        if (composeHotReload.get() && executionMode.get() != ExecutionMode.SPLIT_MODE_FRONTEND) {
            log.info("Compose Hot Reload is enabled for `runIde` task")

            systemPropertyDefault("compose.reload.devToolsEnabled", false)
            systemPropertyDefault("compose.reload.staticsReinitializeMode", "AllDirty")

            classpath(composeHotReloadAgentConfiguration)
        }

        when (executionMode.get()) {
            ExecutionMode.STANDARD -> if (splitMode.get()) {
                environment("${SPLIT_MODE_FRONTEND_ENV_VAR_BASE_NAME}_JDK", resolveSplitModeFrontendRuntimeDirectory().safePathString)
                environment("${SPLIT_MODE_FRONTEND_ENV_VAR_BASE_NAME}_PROPERTIES", splitModeFrontendProperties.asPath.safePathString)

                if (args.isNotEmpty()) {
                    throw InvalidUserDataException("Passing arguments directly is not supported in Split Mode. Use `argumentProviders` instead.")
                }
            }

            ExecutionMode.SPLIT_MODE_BACKEND -> {
                configureSplitModeBackendLaunch()
                argumentProviders.removeAll { it is SplitModeArgumentProvider }
                validateSplitModeTaskArguments()
                args(SPLIT_MODE_BACKEND_COMMAND, "-p", splitModeServerPort.get().toString())
            }

            ExecutionMode.SPLIT_MODE_FRONTEND -> {
                configureSplitModeFrontendLaunch()
                argumentProviders.removeAll { it is SplitModeArgumentProvider }
                validateSplitModeTaskArguments()
                args(
                    SPLIT_MODE_FRONTEND_COMMAND,
                    decorateSplitModeFrontendJoinLink(resolveSplitModeFrontendJoinLink()),
                    SPLIT_MODE_REFRESH_TOKEN_ARGUMENT,
                )
            }
        }

        systemPropertyDefault("idea.auto.reload.plugins", autoReload.get())

        super.exec()
    }

    private fun validateSplitModeTaskArguments() {
        if (args.isNotEmpty()) {
            throw InvalidUserDataException("Passing arguments directly is not supported for split-mode backend/frontend tasks. Use `argumentProviders` instead.")
        }
    }

    private fun configureSplitModeBackendLaunch() {
        configureSplitModeSharedEnvironment()
        val joinLinkPath = splitModeFrontendJoinLinkFile.asPath
        joinLinkPath.deleteIfExists()
        standardOutput = SplitModeJoinLinkOutputStream(
            delegate = runCatching { standardOutput }.getOrNull() ?: System.out,
            joinLinkPath = joinLinkPath,
            persistedJoinLinkOverride = when {
                isJvmDebugEnabled() -> createSplitModeFrontendDebugJoinLink()
                else -> null
            },
        )
    }

    private fun resolveSplitModeFrontendJoinLink(): String {
        splitModeFrontendJoinLink.orNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val joinLinkPath = splitModeFrontendJoinLinkFile.asPath
        readAvailableSplitModeFrontendJoinLink(joinLinkPath)?.let { return it }

        log.info("Waiting for split-mode frontend join link at '${joinLinkPath.safePathString}'.")
        val deadline = System.nanoTime() + MILLISECONDS.toNanos(SPLIT_MODE_JOIN_LINK_WAIT_TIMEOUT_MS)
        while (System.nanoTime() < deadline) {
            try {
                Thread.sleep(SPLIT_MODE_JOIN_LINK_POLL_INTERVAL_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                break
            }

            readAvailableSplitModeFrontendJoinLink(joinLinkPath)?.let { return it }
        }

        throw InvalidUserDataException(
            "No split-mode frontend join link available after waiting ${SPLIT_MODE_JOIN_LINK_WAIT_TIMEOUT_MS / 1000}s. Start `${Tasks.RUN_IDE_BACKEND}` first or set `splitModeFrontendJoinLink` explicitly. Expected file: ${joinLinkPath.safePathString}."
        )
    }

    private fun readAvailableSplitModeFrontendJoinLink(joinLinkPath: java.nio.file.Path) =
        readSplitModeFrontendJoinLink(joinLinkPath)
            ?.takeIf(::isSplitModeFrontendJoinLinkAvailable)

    private fun readSplitModeFrontendJoinLink(joinLinkPath: java.nio.file.Path) = when {
        joinLinkPath.exists() -> joinLinkPath.readText()
            .trim()
            .takeIf { it.isNotEmpty() }

        else -> null
    }

    private fun isSplitModeFrontendJoinLinkAvailable(joinLink: String) = runCatching {
        val uri = URI(joinLink)
        val host = uri.host
        val port = uri.port

        if (uri.scheme !in setOf("tcp", "debug") || host.isNullOrBlank() || port <= 0) {
            return@runCatching true
        }

        Socket().use { socket ->
            socket.connect(InetSocketAddress(host, port), 200)
        }

        true
    }.getOrDefault(false)

    private fun purgeOldLogDirectoriesIfRequested() {
        if (!purgeOldLogDirectories.get()) {
            return
        }

        when (executionMode.get()) {
            ExecutionMode.STANDARD,
            ExecutionMode.SPLIT_MODE_BACKEND, ExecutionMode.SPLIT_MODE_FRONTEND -> {
                val sandboxLogPath = sandboxLogDirectory.asPath
                if (sandboxLogPath.exists()) {
                    log.info("Removing existing sandbox log directory at '${sandboxLogPath.safePathString}'.")
                    sandboxLogPath.toFile().deleteRecursively()
                    check(sandboxLogPath.notExists()) { "Failed to remove existing sandbox log directory: ${sandboxLogPath.safePathString}" }
                }
                sandboxLogPath.createDirectories()
            }
        }
    }

    private fun configureSplitModeFrontendLaunch() {
        configureSplitModeSharedEnvironment()
        configureSplitModeFrontendDebugPort()
        systemProperties.remove("compose.reload.devToolsEnabled")
        systemProperties.remove("compose.reload.staticsReinitializeMode")
        systemProperties.remove("idea.reset.classpath.from.manifest")
        systemProperties.remove("java.nio.file.spi.DefaultFileSystemProvider")
        classpath = splitModeFrontendBootstrapClasspath
    }

    private fun configureSplitModeSharedEnvironment() {
        environment(SPLIT_MODE_NO_TIMEOUTS_ENV, "1")
        when (executionMode.get()) {
            ExecutionMode.SPLIT_MODE_BACKEND -> environment(SPLIT_MODE_HOST_PASSWORD_ENV, SPLIT_MODE_SHARED_PASSWORD)
            ExecutionMode.SPLIT_MODE_FRONTEND -> environment(SPLIT_MODE_CLIENT_PASSWORD_ENV, SPLIT_MODE_SHARED_PASSWORD)
            ExecutionMode.STANDARD -> Unit
        }
    }

    private fun configureSplitModeFrontendDebugPort() {
        if (!isJvmDebugEnabled()) {
            return
        }

        if (debugOptions.port.orNull == DEFAULT_JVM_DEBUG_PORT) {
            debugOptions.port.set(DEFAULT_SPLIT_MODE_FRONTEND_DEBUG_PORT)
        }
    }

    private fun isJvmDebugEnabled() = debug || debugOptions.enabled.orNull == true

    private fun createSplitModeFrontendDebugJoinLink() = "debug://localhost:${splitModeServerPort.get()}#remoteId=Split%20Mode"

    private fun decorateSplitModeFrontendJoinLink(joinLink: String): String {
        if (!joinLink.startsWith("tcp://")) {
            return joinLink
        }

        return when {
            "remoteId=" in joinLink -> joinLink
            "#" in joinLink -> "$joinLink&remoteId=Split%20Mode"
            else -> "$joinLink#remoteId=Split%20Mode"
        }
    }

    private fun resolveSplitModeFrontendRuntimeDirectory() = runCatching {
        javaLauncher.orNull
            ?.metadata
            ?.installationPath
            ?.asPath
            ?.resolveJavaRuntimeDirectory()
    }.getOrNull() ?: runCatching {
        executable
            ?.takeIf { it.isNotBlank() }
            ?.let(::Path)
            ?.parent
            ?.parent
            ?.resolveJavaRuntimeDirectory()
    }.getOrNull() ?: runtimeDirectory.asPath

    init {
        group = Plugin.GROUP_NAME
        description = "Runs the IDE instance using the currently selected IntelliJ Platform with the built plugin loaded."
        executionMode.convention(ExecutionMode.STANDARD)
        splitModeServerPort.convention(DEFAULT_SPLIT_MODE_SERVER_PORT)
        purgeOldLogDirectories.convention(false)
    }

    companion object : Registrable {
        private const val PREPARE_SANDBOX_RUN_IDE_BACKEND = "${Tasks.PREPARE_SANDBOX}_${Tasks.RUN_IDE_BACKEND}"
        private const val PREPARE_SANDBOX_RUN_IDE_FRONTEND = "${Tasks.PREPARE_SANDBOX}_${Tasks.RUN_IDE_FRONTEND}"

        override fun register(project: Project) {
            project.registerTask<RunIdeTask>(Tasks.RUN_IDE, configureWithType = false) {
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
                applySandboxFrom(prepareSandboxTaskProvider)
            }

            project.registerTask<PrepareSandboxTask>(
                PREPARE_SANDBOX_RUN_IDE_BACKEND,
                PREPARE_SANDBOX_RUN_IDE_FRONTEND,
                configureWithType = false,
            ) {
                splitMode.convention(true)
            }

            project.registerTask<RunIdeTask>(Tasks.RUN_IDE_BACKEND, configureWithType = false) {
                executionMode.convention(ExecutionMode.SPLIT_MODE_BACKEND)
                description = "Runs the IDE backend process in Split Mode."

                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(PREPARE_SANDBOX_RUN_IDE_BACKEND)
                applySandboxFrom(prepareSandboxTaskProvider)
            }

            project.registerTask<RunIdeTask>(Tasks.RUN_IDE_FRONTEND, configureWithType = false) {
                executionMode.convention(ExecutionMode.SPLIT_MODE_FRONTEND)
                description = "Runs the JetBrains Client frontend process in Split Mode."

                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(PREPARE_SANDBOX_RUN_IDE_FRONTEND)
                applyFrontendSandboxFrom(prepareSandboxTaskProvider)
            }

            project.registerTask<RunIdeTask> {
                systemPropertyDefault("idea.classpath.index.enabled", false)
                systemPropertyDefault("idea.is.internal", true)
                systemPropertyDefault("idea.plugin.in.sandbox.mode", true)
                systemPropertyDefault("idea.vendor.name", "JetBrains")
                systemPropertyDefault("ide.no.platform.update", false)
                systemPropertyDefault("jdk.module.illegalAccess.silent", true)

                with(OperatingSystem.current()) {
                    when {
                        isMacOsX -> {
                            systemPropertyDefault("idea.smooth.progress", false)
                            systemPropertyDefault("apple.laf.useScreenMenuBar", true)
                            systemPropertyDefault("apple.awt.fileDialogForDirectories", true)
                        }

                        isUnix -> {
                            systemPropertyDefault("sun.awt.disablegrab", true)
                        }
                    }
                }

                splitModeTarget.conventionFrom(pluginInstallationTarget)
                jvmArgumentProviders.add(
                    ComposeHotReloadArgumentProvider(
                        composeHotReloadAgentConfiguration = composeHotReloadAgentConfiguration,
                        enabled = composeHotReload.zip(executionMode) { hotReloadEnabled, mode ->
                            hotReloadEnabled && mode != ExecutionMode.SPLIT_MODE_FRONTEND
                        },
                    )
                )

            }
        }

        internal fun JavaForkOptions.systemPropertyDefault(name: String, defaultValue: Any) {
            if (!systemProperties.containsKey(name)) {
                systemProperty(name, defaultValue)
            }
        }
    }

    enum class ExecutionMode {
        STANDARD,
        SPLIT_MODE_BACKEND,
        SPLIT_MODE_FRONTEND,
    }
}

private class SplitModeJoinLinkOutputStream(
    private val delegate: OutputStream,
    private val joinLinkPath: java.nio.file.Path,
    private val persistedJoinLinkOverride: String? = null,
) : OutputStream() {

    private val lineBuffer = StringBuilder()

    override fun write(b: Int) {
        delegate.write(b)
        appendByte(b.toByte())
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        delegate.write(b, off, len)
        for (i in off until off + len) {
            appendByte(b[i])
        }
    }

    override fun flush() = delegate.flush()

    override fun close() {
        flushLineBuffer()
        delegate.flush()
    }

    private fun appendByte(byte: Byte) {
        when (byte.toInt().toChar()) {
            '\n', '\r' -> flushLineBuffer()
            else -> lineBuffer.append(byte.toInt().toChar())
        }
    }

    private fun flushLineBuffer() {
        if (lineBuffer.isEmpty()) {
            return
        }

        val line = lineBuffer.toString()
        lineBuffer.setLength(0)

        val joinLink = line.substringAfter(SPLIT_MODE_JOIN_LINK_MARKER, missingDelimiterValue = "")
            .trim()
            .takeIf { it.isNotEmpty() }
            ?: return

        joinLinkPath.parent?.createDirectories()
        joinLinkPath.writeText(persistedJoinLinkOverride ?: joinLink)
    }
}
