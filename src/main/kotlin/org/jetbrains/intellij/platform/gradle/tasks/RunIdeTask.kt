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
import org.gradle.process.ExecOperations
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
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicBoolean
import java.util.stream.Collectors
import javax.inject.Inject
import kotlin.concurrent.thread
import kotlin.io.path.*

internal const val SPLIT_MODE_FRONTEND_COMMAND = "thinClient"
private const val SPLIT_MODE_BACKEND_COMMAND = "serverMode"
private const val SPLIT_MODE_FRONTEND_ENV_VAR_BASE_NAME = "JETBRAINS_CLIENT"
internal const val SPLIT_MODE_FRONTEND_MAIN_CLASS = "com.intellij.platform.runtime.loader.IntellijLoader"
private const val DEFAULT_SPLIT_MODE_SERVER_PORT = 5990
private const val SPLIT_MODE_SERVER_PORT_RANDOM = 0
internal const val SPLIT_MODE_SERVER_PORT_RANDOM_MIN = 5990
internal const val SPLIT_MODE_SERVER_PORT_RANDOM_MAX = 6989
private const val SPLIT_MODE_SERVER_PORT_RANDOM_ATTEMPTS = 50
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
private const val SPLIT_MODE_PORT_PROBE_TIMEOUT_MS = 200
private const val SPLIT_MODE_PID_POLL_INTERVAL_MS = 50L
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

    /**
     * Caches the split-mode backend port resolved for this execution, so a randomly selected free port stays stable
     * across the multiple reads during a single run.
     */
    private var resolvedSplitModeServerPort: Int? = null

    @get:Inject
    abstract val execOperations: ExecOperations

    @get:Internal
    abstract val executionMode: Property<ExecutionMode>

    /**
     * The backend port used by split-mode backend/frontend tasks; `runIdeBackend` passes it to `serverMode -p`.
     *
     * Defaults to [DEFAULT_SPLIT_MODE_SERVER_PORT]. Set it to `0` to have a random free port in the range
     * [[SPLIT_MODE_SERVER_PORT_RANDOM_MIN], [SPLIT_MODE_SERVER_PORT_RANDOM_MAX]] selected at execution time and logged.
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
        logSplitModeSandboxPaths()

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
                args(SPLIT_MODE_BACKEND_COMMAND, "-p", resolveSplitModeServerPort().toString())
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

        when (executionMode.get()) {
            ExecutionMode.SPLIT_MODE_BACKEND -> runSplitModeBackend()
            else -> super.exec()
        }
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

    /**
     * Runs the split-mode backend through Gradle's managed [JavaExec] execution, while making the launched IDE process
     * observable:
     *  - before launching, [checkForConflictingSplitModeBackend] fails fast when a backend this plugin recorded is
     *    still running, and warns when the configured port already appears to be in use;
     *  - during the run, a background tracker records the launched process identifier (PID) to
     *    [SplitModeAware.splitModeBackendPidFile] and logs it;
     *  - on exit, the PID file is removed so it never points to a dead process.
     *
     * The process itself is started by Gradle (not [ProcessBuilder]) because Gradle instruments direct process launches
     * in plugin code, which fails at task-execution time.
     */
    private fun runSplitModeBackend() {
        checkForConflictingSplitModeBackend()

        val pidPath = splitModeBackendPidFile.asPath
        val trackerStopped = AtomicBoolean(false)
        val tracker = startSplitModeBackendPidTracker(pidPath, trackerStopped)
        try {
            super.exec()
        } finally {
            trackerStopped.set(true)
            tracker.interrupt()
            runCatching { tracker.join(SECONDS.toMillis(2)) }
            pidPath.deleteIfExists()
        }
    }

    /**
     * Starts a background daemon thread that detects the IDE process launched by [JavaExec] (a newly appeared
     * descendant running the split-mode backend command), then records and logs its PID.
     *
     * Detection is best-effort: it observes child processes of the current build process and stops as soon as the
     * backend is found or [trackerStopped] is set.
     */
    private fun startSplitModeBackendPidTracker(pidPath: Path, trackerStopped: AtomicBoolean): Thread {
        val port = resolveSplitModeServerPort()
        val currentProcess = ProcessHandle.current()
        val preexistingPids = currentProcess.descendants().map { it.pid() }.collect(Collectors.toSet())

        return thread(isDaemon = true, name = "split-mode-backend-pid-tracker") {
            while (!trackerStopped.get()) {
                val backendHandle = currentProcess.descendants()
                    .filter { it.isAlive && it.pid() !in preexistingPids }
                    .filter { it.looksLikeSplitModeBackend() }
                    .findFirst()
                    .orElse(null)

                if (backendHandle != null) {
                    runCatching {
                        writeSplitModeBackendPidFile(pidPath, backendHandle.pid())
                        log.lifecycle(
                            "Started split-mode backend (PID ${backendHandle.pid()}) on port $port. " +
                                "PID file: '${pidPath.safePathString}'."
                        )
                    }
                    return@thread
                }

                try {
                    Thread.sleep(SPLIT_MODE_PID_POLL_INTERVAL_MS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return@thread
                }
            }
        }
    }

    private fun ProcessHandle.looksLikeSplitModeBackend(): Boolean {
        // When the OS does not expose the command line we cannot positively confirm this descendant is the backend,
        // so we skip it rather than record a PID we cannot verify later (e.g. frequently on Windows).
        val commandLine = info().commandLine().orElse(null) ?: return false
        return commandLine.contains(SPLIT_MODE_BACKEND_COMMAND)
    }

    /**
     * Surfaces a conflicting split-mode backend before launching a fresh one.
     *
     * Two independent signals are checked, with different severities:
     *  - a previously written [SplitModeAware.splitModeBackendPidFile] that still points to a live IDE process this
     *    plugin launched: an unambiguous conflict that **fails fast**;
     *  - the configured [splitModeServerPort] already accepting connections: this may be any unrelated process, so it
     *    only **warns** (best-effort PID lookup via `lsof`) and lets the IDE attempt to bind, preserving the
     *    pre-feature behavior.
     */
    private fun checkForConflictingSplitModeBackend() {
        val pidPath = splitModeBackendPidFile.asPath
        val runningHandle = readRunningSplitModeBackendHandle(pidPath)
        if (runningHandle != null) {
            val commandSuffix = runningHandle.info().command()
                .map { ", command: '$it'" }
                .orElse("")
            throw InvalidUserDataException(
                "A split-mode backend is already running (PID ${runningHandle.pid()}$commandSuffix), recorded in '${pidPath.safePathString}'. " +
                    "Stop it first (e.g. `kill ${runningHandle.pid()}`) or change `splitModeServerPort` before launching `${Tasks.RUN_IDE_BACKEND}` again."
            )
        }
        // Either no PID file, or it referenced a process that is no longer alive; drop the stale marker.
        pidPath.deleteIfExists()

        val port = resolveSplitModeServerPort()
        if (isSplitModeServerPortInUse(port)) {
            val holders = findListeningProcessIds(port)
            val holderText = when {
                holders.isEmpty() -> "the owning process could not be determined"
                else -> "PID(s) ${holders.joinToString(", ")} are listening on it"
            }
            log.warn(
                "Split-mode backend port $port appears to be in use ($holderText). " +
                    "The backend may fail to bind it. If startup fails, stop the conflicting process " +
                    "or set a different `splitModeServerPort` before launching `${Tasks.RUN_IDE_BACKEND}`."
            )
        }
    }

    /**
     * Reads the PID recorded in [pidPath] and returns its [ProcessHandle] only when that process is still alive and
     * still looks like an IDE process. Returns `null` (and treats the file as stale) otherwise.
     */
    private fun readRunningSplitModeBackendHandle(pidPath: Path): ProcessHandle? {
        if (pidPath.notExists()) {
            return null
        }

        val pid = runCatching { pidPath.readText().trim().toLong() }.getOrNull() ?: return null
        val handle = ProcessHandle.of(pid).orElse(null) ?: return null
        if (!handle.isAlive) {
            return null
        }

        // Guard against PID reuse: only treat the recorded PID as a running backend when the OS exposes a command line
        // that clearly looks like an IDE process. When the command line is unavailable we cannot positively confirm
        // the process, so we treat the marker as stale rather than block a launch over an unrelated reused PID.
        val commandLine = handle.info().commandLine().orElse(null) ?: return null
        val looksLikeIdeProcess = listOf("java", "idea", "Main", "JetBrains")
            .any { commandLine.contains(it, ignoreCase = true) }
        return handle.takeIf { looksLikeIdeProcess }
    }

    private fun writeSplitModeBackendPidFile(pidPath: Path, pid: Long) {
        pidPath.parent?.createDirectories()
        pidPath.writeText(pid.toString())
    }

    /**
     * Resolves the split-mode backend port for this run: the configured [splitModeServerPort] (defaulting to
     * [DEFAULT_SPLIT_MODE_SERVER_PORT]), or a random free port from
     * [SPLIT_MODE_SERVER_PORT_RANDOM_MIN]..[SPLIT_MODE_SERVER_PORT_RANDOM_MAX] when it is explicitly set to
     * [SPLIT_MODE_SERVER_PORT_RANDOM] (`0`).
     *
     * The resolved value is cached so repeated reads within a single execution stay consistent.
     */
    private fun resolveSplitModeServerPort(): Int {
        resolvedSplitModeServerPort?.let { return it }

        val port = when (val configured = splitModeServerPort.get()) {
            SPLIT_MODE_SERVER_PORT_RANDOM -> findFreeSplitModeServerPort().also {
                log.lifecycle("`splitModeServerPort` is set to 0; selected random free port $it for the split-mode backend.")
            }

            else -> configured
        }
        resolvedSplitModeServerPort = port
        return port
    }

    /**
     * Picks a random free port within [SPLIT_MODE_SERVER_PORT_RANDOM_MIN]..[SPLIT_MODE_SERVER_PORT_RANDOM_MAX],
     * falling back to an OS-assigned ephemeral port if no candidate in the range could be bound.
     */
    private fun findFreeSplitModeServerPort(): Int {
        repeat(SPLIT_MODE_SERVER_PORT_RANDOM_ATTEMPTS) {
            val candidate = (SPLIT_MODE_SERVER_PORT_RANDOM_MIN..SPLIT_MODE_SERVER_PORT_RANDOM_MAX).random()
            if (isPortFree(candidate)) {
                return candidate
            }
        }
        return ServerSocket(0).use { it.localPort }
    }

    private fun isPortFree(port: Int) = runCatching {
        ServerSocket().use { serverSocket ->
            serverSocket.reuseAddress = false
            serverSocket.bind(InetSocketAddress("127.0.0.1", port))
        }
        true
    }.getOrDefault(false)

    private fun isSplitModeServerPortInUse(port: Int) = runCatching {
        Socket().use { socket ->
            socket.connect(InetSocketAddress("127.0.0.1", port), SPLIT_MODE_PORT_PROBE_TIMEOUT_MS)
        }
        true
    }.getOrDefault(false)

    /**
     * Best-effort lookup of the PIDs listening on [port] using `lsof`, available on macOS and Linux.
     * Returns an empty list when the tool is unavailable (e.g. on Windows) or no holder could be resolved.
     *
     * `lsof` is launched through Gradle's [ExecOperations] service rather than [ProcessBuilder], because Gradle
     * instruments direct process launches in plugin code and fails at task-execution time.
     */
    private fun findListeningProcessIds(port: Int): List<Long> {
        if (OperatingSystem.current().isWindows) {
            return emptyList()
        }

        val output = ByteArrayOutputStream()
        return runCatching {
            execOperations.exec {
                commandLine("lsof", "-nP", "-iTCP:$port", "-sTCP:LISTEN", "-t")
                standardOutput = output
                errorOutput = OutputStream.nullOutputStream()
                isIgnoreExitValue = true
            }
            output.toString()
                .lineSequence()
                .mapNotNull { it.trim().toLongOrNull() }
                .distinct()
                .toList()
        }.getOrDefault(emptyList())
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

    private fun logSplitModeSandboxPaths() {
        val mode = when (executionMode.get()) {
            ExecutionMode.SPLIT_MODE_BACKEND -> "backend"
            ExecutionMode.SPLIT_MODE_FRONTEND -> "frontend"
            ExecutionMode.STANDARD -> return
        }

        log.lifecycle(
            "Split-mode $mode sandbox paths:\n" +
                "  idea.config.path=${sandboxConfigDirectory.asPath.safePathString}\n" +
                "  idea.system.path=${sandboxSystemDirectory.asPath.safePathString}\n" +
                "  idea.log.path=${sandboxLogDirectory.asPath.safePathString}\n" +
                "  idea.plugins.path=${sandboxPluginsDirectory.asPath.safePathString}"
        )
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

    private fun createSplitModeFrontendDebugJoinLink() = "debug://localhost:${resolveSplitModeServerPort()}#remoteId=Split%20Mode"

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
                systemPropertyDefault("intellij.console.log.level", "warning")
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
