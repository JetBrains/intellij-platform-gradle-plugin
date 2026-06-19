// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.assertNotContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.cacheDirectory
import org.jetbrains.intellij.platform.gradle.overwrite
import org.jetbrains.intellij.platform.gradle.write
import javax.tools.ToolProvider
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RunIdeTaskTest : IntelliJPluginTestBase() {

    private val isWindows = System.getProperty("os.name").startsWith("Windows")
    private val sandbox
        get() = cacheDirectory.resolve(Sandbox.CONTAINER).resolve("projectName")
            .resolve("$intellijPlatformType-$intellijPlatformVersion")

    private fun configureFrontendJoinLinkProvider(
        delayMs: Int,
        port: Int,
        joinLink: String,
    ) {
        buildFile.toFile().appendText(
            """

            tasks.named("${Tasks.RUN_IDE_FRONTEND}") {
                doFirst {
                    val joinLinkFileProvider = javaClass
                        .getMethod("getSplitModeFrontendJoinLinkFile")
                        .invoke(this) as org.gradle.api.provider.Provider<*>
                    val joinLinkFile = (joinLinkFileProvider.get() as org.gradle.api.file.RegularFile).asFile
                    Thread {
                        Thread.sleep($delayMs)
                        val serverSocketClass = Class.forName("java.net.ServerSocket")
                        val serverSocket = serverSocketClass
                            .getConstructor(Int::class.javaPrimitiveType)
                            .newInstance($port)
                        try {
                            joinLinkFile.parentFile.mkdirs()
                            joinLinkFile.writeText("$joinLink")
                            Thread.sleep(5_000)
                        } finally {
                            serverSocketClass.getMethod("close").invoke(serverSocket)
                        }
                    }.start()
                }
            }
            """.trimIndent()
        )
    }

    private fun configureFakeJavaLauncher() {
        val fakeJdk = dir.resolve("fake-jdk")
        val fakeLauncherClasses = fakeJdk.resolve("fake-launcher-classes")
        val fakeJavaExecutable = fakeJdk.resolve("bin/java${if (isWindows) ".exe" else ""}")
        val realJavaExecutable =
            java.nio.file.Path.of(System.getProperty("java.home"), "bin", "java${if (isWindows) ".exe" else ""}")

        fakeJdk.resolve("bin").createDirectories()
        fakeJavaExecutable overwrite ""
        compileFakeJavaLauncherClasses(fakeLauncherClasses)

        val realJavaExecutablePath = realJavaExecutable.toRealPath().invariantSeparatorsPathString
        val fakeLauncherClassesPath = fakeLauncherClasses.toRealPath().invariantSeparatorsPathString
        val fakeJavaDebugLogPath = fakeJdk.resolve("fake-java-debug.log").invariantSeparatorsPathString

        buildFile.toFile().appendText(
            """

            tasks.withType<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>().configureEach {
                val fakeJdk = layout.projectDirectory.dir("fake-jdk")
                val realJavaExecutablePath = project.file("$realJavaExecutablePath")
                val realJavaExecutable = project.layout.file(project.provider { realJavaExecutablePath }).get()
                javaLauncher = provider {
                    object : org.gradle.jvm.toolchain.JavaLauncher {
                        override fun getExecutablePath() = realJavaExecutable

                        override fun getMetadata() = object : org.gradle.jvm.toolchain.JavaInstallationMetadata {
                            override fun getLanguageVersion() = org.gradle.jvm.toolchain.JavaLanguageVersion.of(21)

                            override fun getJavaRuntimeVersion() = "21"

                            override fun getJvmVersion() = "21"

                            override fun getVendor() = "Fake"

                            override fun getInstallationPath() = fakeJdk

                            @Suppress("UnstableApiUsage")
                            override fun isCurrentJvm() = false
                        }
                    }
                }
                jvmArgs(
                    "-Xbootclasspath/a:$fakeLauncherClassesPath",
                    "-Dfake.java.debug.log=$fakeJavaDebugLogPath",
                    "-Dfake.java.fakeLauncherClasses=$fakeLauncherClassesPath",
                )
            }
            """.trimIndent()
        )
    }

    private fun compileFakeJavaLauncherClasses(outputDirectory: java.nio.file.Path) {
        val sourceDirectory = dir.resolve("fake-jdk/fake-launcher-src")
        val helperSource = sourceDirectory.resolve("fake/launcher/FakeLauncher.java")
        val ideaMainSource = sourceDirectory.resolve("com/intellij/idea/Main.java")
        val intellijLoaderSource = sourceDirectory.resolve("com/intellij/platform/runtime/loader/IntellijLoader.java")

        helperSource.parent.createDirectories()
        ideaMainSource.parent.createDirectories()
        intellijLoaderSource.parent.createDirectories()
        outputDirectory.createDirectories()

        helperSource overwrite //language=java
                """
                package fake.launcher;

                import java.lang.management.ManagementFactory;
                import java.net.ServerSocket;
                import java.nio.file.Files;
                import java.nio.file.Path;
                import java.nio.file.StandardOpenOption;
                import java.util.ArrayList;
                import java.util.Arrays;
                import java.util.List;

                public final class FakeLauncher {
                    private FakeLauncher() {}

                    public static void run(String mainClass, String[] args) throws Exception {
                        var debugLines = new ArrayList<String>();
                        debugLines.add("=== fake java start ===");
                        debugLines.add("MAIN_CLASS=" + mainClass);
                        debugLines.add("CWD=" + Path.of("").toAbsolutePath());
                        for (var arg : args) {
                            debugLines.add("ARG=" + arg);
                        }

                        printAndLog("JETBRAINS_CLIENT_JDK=" + getenv("JETBRAINS_CLIENT_JDK"), debugLines);
                        printAndLog("JETBRAINS_CLIENT_VM_OPTIONS=" + getenv("JETBRAINS_CLIENT_VM_OPTIONS"), debugLines);
                        printAndLog("JETBRAINS_CLIENT_PROPERTIES=" + getenv("JETBRAINS_CLIENT_PROPERTIES"), debugLines);
                        printAndLog("JETBRAINS_CLIENT_VM_OPTIONS_CONTENT_START", debugLines);
                        readVmOptions(debugLines);
                        printAndLog("JETBRAINS_CLIENT_VM_OPTIONS_CONTENT_END", debugLines);
                        printAndLog("MAIN_CLASS=" + mainClass, debugLines);

                        var jvmArgs = String.join(" ", filteredInputArguments());
                        if (!jvmArgs.isBlank()) {
                            printAndLog("JVM_ARGS=" + jvmArgs, debugLines);
                        }

                        var appArgs = String.join(" ", args);
                        if (!appArgs.isBlank()) {
                            printAndLog("APP_ARGS=" + appArgs, debugLines);
                        }

                        var argsList = Arrays.asList(args);
                        var exitCode = resolveExitCode(argsList);
                        if (argsList.contains("serverMode")) {
                            var port = 5990;
                            var portOptionIndex = argsList.indexOf("-p");
                            if (portOptionIndex >= 0 && portOptionIndex + 1 < args.length) {
                                port = Integer.parseInt(args[portOptionIndex + 1]);
                            }

                            var aliveMs = System.getProperty("fake.backend.alive.ms");
                            if (aliveMs != null && !aliveMs.isBlank()) {
                                try (var serverSocket = new ServerSocket(port)) {
                                    printAndLog("Join link: tcp://127.0.0.1:" + port + "#cb=fake", debugLines);
                                    try {
                                        Thread.sleep(Long.parseLong(aliveMs));
                                    } catch (InterruptedException interrupted) {
                                        Thread.currentThread().interrupt();
                                    }
                                }
                            } else {
                                printAndLog("Join link: tcp://127.0.0.1:" + port + "#cb=fake", debugLines);
                            }
                        }

                        debugLines.add("=== fake java end ===");
                        writeDebugLog(debugLines);
                        if (exitCode != 0) {
                            System.exit(exitCode);
                        }
                    }

                    private static int resolveExitCode(List<String> argsList) {
                        var propertyName = "fake.exit.code";
                        if (argsList.contains("serverMode")) {
                            propertyName = "fake.backend.exit.code";
                        } else if (argsList.contains("thinClient")) {
                            propertyName = "fake.frontend.exit.code";
                        }

                        var configured = System.getProperty(propertyName, System.getProperty("fake.exit.code", "0"));
                        if (configured == null || configured.isBlank()) {
                            return 0;
                        }
                        return Integer.parseInt(configured);
                    }

                    private static String getenv(String name) {
                        return System.getenv().getOrDefault(name, "");
                    }

                    private static void readVmOptions(List<String> debugLines) throws Exception {
                        var vmOptions = getenv("JETBRAINS_CLIENT_VM_OPTIONS");
                        if (vmOptions.isBlank()) {
                            return;
                        }

                        var vmOptionsPath = Path.of(vmOptions);
                        if (!Files.exists(vmOptionsPath)) {
                            return;
                        }

                        for (var line : Files.readAllLines(vmOptionsPath)) {
                            printAndLog(line, debugLines);
                        }
                    }

                    private static List<String> filteredInputArguments() {
                        var fakeLauncherClasses = System.getProperty("fake.java.fakeLauncherClasses", "");
                        var filtered = new ArrayList<String>();
                        for (var argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                            if (argument.startsWith("-Dfake.java.")) {
                                continue;
                            }
                            if (argument.startsWith("-Xbootclasspath/a:") && argument.contains(fakeLauncherClasses)) {
                                continue;
                            }
                            filtered.add(argument);
                        }
                        return filtered;
                    }

                    private static void printAndLog(String line, List<String> debugLines) {
                        System.out.println(line);
                        debugLines.add(line);
                    }

                    private static void writeDebugLog(List<String> debugLines) throws Exception {
                        var debugLog = System.getProperty("fake.java.debug.log", "");
                        if (debugLog.isBlank()) {
                            return;
                        }
                        Files.write(
                            Path.of(debugLog),
                            debugLines,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE
                        );
                    }
                }
                """.trimIndent()
        ideaMainSource overwrite //language=java
                """
                package com.intellij.idea;

                public final class Main {
                    private Main() {}

                    public static void main(String[] args) throws Exception {
                        fake.launcher.FakeLauncher.run(Main.class.getName(), args);
                    }
                }
                """.trimIndent()
        intellijLoaderSource overwrite //language=java
                """
                package com.intellij.platform.runtime.loader;

                public final class IntellijLoader {
                    private IntellijLoader() {}

                    public static void main(String[] args) throws Exception {
                        fake.launcher.FakeLauncher.run(IntellijLoader.class.getName(), args);
                    }
                }
                """.trimIndent()

        val compiler = checkNotNull(ToolProvider.getSystemJavaCompiler()) {
            "A JDK with the system Java compiler is required to run RunIdeTaskTest."
        }
        val result = compiler.run(
            null,
            null,
            null,
            "-d",
            outputDirectory.toString(),
            helperSource.toString(),
            ideaMainSource.toString(),
            intellijLoaderSource.toString(),
        )
        check(result == 0) { "Failed to compile fake Java launcher classes, exit code: $result" }
    }

    @Test
    fun `propagate launcher override to split mode frontend`() {
        buildFile write //language=kotlin
                """
                tasks {
                    runIde {
                        splitMode = true
                    }
                }
                """.trimIndent()
        configureFakeJavaLauncher()
        val expectedJavaHome = dir.resolve("fake-jdk").toRealPath().invariantSeparatorsPathString

        build(Tasks.RUN_IDE) {
            assertContains("JETBRAINS_CLIENT_JDK=$expectedJavaHome", output)
            assertContains("JETBRAINS_CLIENT_PROPERTIES=", output)
            assertContains("JETBRAINS_CLIENT_VM_OPTIONS=", output)
            assertNotContains("frontend.vmoptions", output)
        }
    }

    @Test
    fun `runIde keeps old log directories by default`() {
        configureFakeJavaLauncher()
        val staleLogFile = sandbox.resolve("${Sandbox.LOG}/old-session/idea.log")
        staleLogFile.parent.createDirectories()
        staleLogFile.toFile().writeText("stale")

        build(Tasks.RUN_IDE)

        assertTrue(staleLogFile.exists())
    }

    @Test
    fun `runIde removes old log directories when requested`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<RunIdeTask>("${Tasks.RUN_IDE}") {
                purgeOldLogDirectories.set(true)
            }
            """.trimIndent()
        )

        val logDirectory = sandbox.resolve(Sandbox.LOG)
        val staleLogFile = logDirectory.resolve("old-session/idea.log")
        staleLogFile.parent.createDirectories()
        staleLogFile.toFile().writeText("stale")

        build(Tasks.RUN_IDE)

        assertFalse(staleLogFile.exists())
        assertTrue(logDirectory.exists())
    }

    @Test
    fun `runIdeBackend launches serverMode on default port when none is configured`() {
        configureFakeJavaLauncher()

        build(Tasks.RUN_IDE_BACKEND) {
            assertContains("MAIN_CLASS=com.intellij.idea.Main", output)
            assertContains("APP_ARGS=serverMode -p 5990", output)
        }

        val joinLinkFiles = dir.toFile()
            .walkTopDown()
            .filter { it.name == "split-mode-frontend.join.link" }
            .toList()

        kotlin.test.assertEquals(1, joinLinkFiles.size)
        kotlin.test.assertEquals("tcp://127.0.0.1:5990#cb=fake", joinLinkFiles.single().readText().trim())
    }

    @Test
    fun `runIdeSplitMode starts backend before frontend`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeSplitModeTask>("${Tasks.RUN_IDE_SPLIT_MODE}") {
                jvmArgs("-Dfake.backend.alive.ms=1000")
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_SPLIT_MODE) {
            val backendArgs = "APP_ARGS=serverMode -p 5990"
            val frontendArgs = "APP_ARGS=thinClient tcp://127.0.0.1:5990#cb=fake&remoteId=Split%20Mode --refresh-split-mode-token=true"

            assertContains(backendArgs, output)
            assertContains(frontendArgs, output)
            assertTrue(
                output.indexOf(backendArgs) < output.indexOf(frontendArgs),
                "Expected backend launch output before frontend launch output."
            )
            assertContains("Split-mode backend sandbox paths:", output)
            assertContains("config_${Tasks.RUN_IDE_BACKEND}", output)
            assertContains("system_${Tasks.RUN_IDE_BACKEND}", output)
            assertContains("log_${Tasks.RUN_IDE_BACKEND}", output)
            assertContains("plugins_${Tasks.RUN_IDE_BACKEND}", output)
            assertContains("Split-mode frontend sandbox paths:", output)
            assertContains("config_${Tasks.RUN_IDE_FRONTEND}/frontend", output)
            assertContains("system_${Tasks.RUN_IDE_FRONTEND}/frontend", output)
            assertContains("log_${Tasks.RUN_IDE_FRONTEND}/frontend", output)
            assertContains("plugins_${Tasks.RUN_IDE_FRONTEND}/frontend", output)
            assertContains("-Didea.properties.file=", output)
            assertContains("${Tasks.RUN_IDE_SPLIT_MODE}-frontend.properties", output)
        }

        val frontendProperties = sandbox.resolve("${Tasks.RUN_IDE_SPLIT_MODE}-frontend.properties")
        assertTrue(frontendProperties.exists())
        frontendProperties.readText().let { properties ->
            assertContains("idea.config.path=", properties)
            assertContains("config_${Tasks.RUN_IDE_FRONTEND}/frontend", properties)
            assertContains("idea.system.path=", properties)
            assertContains("system_${Tasks.RUN_IDE_FRONTEND}/frontend", properties)
            assertContains("idea.log.path=", properties)
            assertContains("log_${Tasks.RUN_IDE_FRONTEND}/frontend", properties)
            assertContains("idea.plugins.path=", properties)
            assertContains("plugins_${Tasks.RUN_IDE_FRONTEND}/frontend", properties)
        }
    }

    @Test
    fun `runIdeSplitMode keeps JVM debugging only on backend`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeSplitModeTask>("${Tasks.RUN_IDE_SPLIT_MODE}") {
                jvmArgs("-Dfake.backend.alive.ms=1000")
                debugOptions.enabled.set(true)
                debugOptions.server.set(true)
                debugOptions.port.set(0)
                debugOptions.suspend.set(false)
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_SPLIT_MODE) {
            val backendArgs = "APP_ARGS=serverMode -p 5990"
            val frontendArgs = "APP_ARGS=thinClient debug://localhost:5990#remoteId=Split%20Mode --refresh-split-mode-token=true"
            val jvmArgLinesWithJdwp = output.lineSequence()
                .filter { it.startsWith("JVM_ARGS=") && "jdwp" in it }
                .toList()

            assertContains(backendArgs, output)
            assertContains(frontendArgs, output)
            assertContains("-agentlib:jdwp=", jvmArgLinesWithJdwp.single())
            assertTrue(
                output.indexOf(jvmArgLinesWithJdwp.single()) < output.indexOf(frontendArgs),
                "Expected only backend launch to receive JDWP JVM arguments."
            )
            assertContains("Java debugging is disabled for the frontend process launched by `${Tasks.RUN_IDE_SPLIT_MODE}`.", output)
        }
    }

    @Test
    fun `runIdeSplitMode reuses configuration cache`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeSplitModeTask>("${Tasks.RUN_IDE_SPLIT_MODE}") {
                jvmArgs("-Dfake.backend.alive.ms=1000")
            }
            """.trimIndent()
        )

        buildWithConfigurationCache(Tasks.RUN_IDE_SPLIT_MODE)
        buildWithConfigurationCache(Tasks.RUN_IDE_SPLIT_MODE) {
            assertConfigurationCacheReused()
        }
    }

    @Test
    fun `runIdeSplitMode accepts expected shutdown exit code`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeSplitModeTask>("${Tasks.RUN_IDE_SPLIT_MODE}") {
                jvmArgs(
                    "-Dfake.backend.alive.ms=1000",
                    "-Dfake.frontend.exit.code=193",
                    "-Dfake.backend.exit.code=193",
                )
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_SPLIT_MODE) {
            assertContains("Split-mode frontend process exited with expected shutdown code 193.", output)
            assertContains("Split-mode backend process exited with expected shutdown code 193.", output)
        }
    }

    @Test
    fun `runIdeSplitMode fails on unexpected frontend exit code`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeSplitModeTask>("${Tasks.RUN_IDE_SPLIT_MODE}") {
                jvmArgs(
                    "-Dfake.backend.alive.ms=1000",
                    "-Dfake.frontend.exit.code=17",
                )
            }
            """.trimIndent()
        )

        buildAndFail(Tasks.RUN_IDE_SPLIT_MODE) {
            assertContains("Process 'command", output)
            assertContains("finished with non-zero exit value 17", output)
        }
    }

    @Test
    fun `runIdeBackend selects a random free port when configured to zero`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("${Tasks.RUN_IDE_BACKEND}") {
                splitModeServerPort.set(0)
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_BACKEND) {
            assertContains("`splitModeServerPort` is set to 0; selected random free port ", output)
            assertContains("Split-mode backend sandbox paths:", output)
            assertContains("idea.config.path=", output)
            assertContains("config_${Tasks.RUN_IDE_BACKEND}", output)
            assertContains("idea.system.path=", output)
            assertContains("system_${Tasks.RUN_IDE_BACKEND}", output)
            assertContains("idea.log.path=", output)
            assertContains("log_${Tasks.RUN_IDE_BACKEND}", output)
            assertContains("idea.plugins.path=", output)
            assertContains("plugins_${Tasks.RUN_IDE_BACKEND}", output)

            val appArgsLine = output.lineSequence().first { it.startsWith("APP_ARGS=serverMode -p ") }
            val port = appArgsLine.removePrefix("APP_ARGS=serverMode -p ").trim().toInt()
            assertTrue(port in 5990..6989, "Expected a port in 5990..6989 but was $port")
        }
    }

    @Test
    fun `runIdeBackend logs the launched backend process id`() {
        configureFakeJavaLauncher()
        // Keep the launched backend process alive long enough for the PID tracker to observe it deterministically.
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("${Tasks.RUN_IDE_BACKEND}") {
                jvmArgs("-Dfake.backend.alive.ms=2000")
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_BACKEND) {
            assertContains("Started split-mode backend (PID ", output)
        }
    }

    @Test
    fun `runIdeBackend fails when a split-mode backend is already running`() {
        configureFakeJavaLauncher()

        val runtimeSandbox = dir.resolve(".intellijPlatform/sandbox/projectName/$intellijPlatformType-$intellijPlatformVersion")
        runtimeSandbox.createDirectories()
        val currentPid = ProcessHandle.current().pid()
        runtimeSandbox.resolve("split-mode-backend.pid").toFile().writeText(currentPid.toString())

        buildAndFail(Tasks.RUN_IDE_BACKEND) {
            assertContains("A split-mode backend is already running (PID $currentPid", output)
        }
    }

    @Test
    fun `runIdeBackend warns but proceeds when the configured port is already in use`() {
        configureFakeJavaLauncher()

        java.net.ServerSocket(0).use { serverSocket ->
            val port = serverSocket.localPort
            buildFile.toFile().appendText(
                """

                tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("${Tasks.RUN_IDE_BACKEND}") {
                    splitModeServerPort.set($port)
                }
                """.trimIndent()
            )

            build(Tasks.RUN_IDE_BACKEND) {
                assertContains("Split-mode backend port $port appears to be in use", output)
                // The launch must still proceed instead of being blocked.
                assertContains("APP_ARGS=serverMode -p $port", output)
            }
        }
    }

    @Test
    fun `runIdeBackend ignores a stale backend PID file`() {
        configureFakeJavaLauncher()

        val runtimeSandbox = dir.resolve(".intellijPlatform/sandbox/projectName/$intellijPlatformType-$intellijPlatformVersion")
        runtimeSandbox.createDirectories()
        // A PID that does not resolve to any live process must be treated as a stale marker, not a running backend.
        val deadPid = 999_999_999L
        runtimeSandbox.resolve("split-mode-backend.pid").toFile().writeText(deadPid.toString())

        build(Tasks.RUN_IDE_BACKEND) {
            assertContains("APP_ARGS=serverMode -p 5990", output)
        }
    }

    @Test
    fun `runIdeBackend writes debug frontend join link when backend debugging is enabled`() {
        configureFakeJavaLauncher()
        val debugPort = java.net.ServerSocket(0).use { it.localPort }
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("${Tasks.RUN_IDE_BACKEND}") {
                debugOptions.enabled.set(true)
                debugOptions.port.set($debugPort)
                debugOptions.suspend.set(false)
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_BACKEND)

        val joinLinkFiles = dir.toFile()
            .walkTopDown()
            .filter { it.name == "split-mode-frontend.join.link" }
            .toList()

        kotlin.test.assertEquals(1, joinLinkFiles.size)
        kotlin.test.assertEquals(
            "debug://localhost:5990#remoteId=Split%20Mode",
            joinLinkFiles.single().readText().trim()
        )
    }

    @Test
    fun `runIdeBackend keeps old log directories by default`() {
        configureFakeJavaLauncher()
        val staleLogFile = sandbox.resolve("log_${Tasks.RUN_IDE_BACKEND}/old-session/idea.log")
        staleLogFile.parent.createDirectories()
        staleLogFile.toFile().writeText("stale")

        build(Tasks.RUN_IDE_BACKEND)

        assertTrue(staleLogFile.exists())
    }

    @Test
    fun `runIdeBackend removes old log directories when requested`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<RunIdeTask>("${Tasks.RUN_IDE_BACKEND}") {
                purgeOldLogDirectories.set(true)
            }
            """.trimIndent()
        )

        val backendLogDirectory = sandbox.resolve("log_${Tasks.RUN_IDE_BACKEND}")
        val staleLogFile = backendLogDirectory.resolve("old-session/idea.log")
        staleLogFile.parent.createDirectories()
        staleLogFile.toFile().writeText("stale")

        build(Tasks.RUN_IDE_BACKEND)

        assertFalse(staleLogFile.exists())
        assertTrue(backendLogDirectory.exists())
    }

    @Test
    fun `runIdeBackend removes old log directories when requested via task option`() {
        configureFakeJavaLauncher()
        val backendLogDirectory = sandbox.resolve("log_${Tasks.RUN_IDE_BACKEND}")
        val staleLogFile = backendLogDirectory.resolve("old-session/idea.log")
        staleLogFile.parent.createDirectories()
        staleLogFile.toFile().writeText("stale")

        val result = builder(
            gradleVersion,
            Tasks.RUN_IDE_BACKEND,
            "--$PURGE_OLD_LOG_DIRECTORIES_OPTION",
        ).build()

        assertContains("APP_ARGS=serverMode -p 5990", result.output)
        assertFalse(staleLogFile.exists())
        assertTrue(backendLogDirectory.exists())
    }

    @Test
    fun `runIdeFrontend uses debug join link written by debug backend`() {
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(
            delayMs = 0,
            port = 6092,
            joinLink = "debug://localhost:6092#remoteId=Split%20Mode"
        )

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("MAIN_CLASS=com.intellij.platform.runtime.loader.IntellijLoader", output)
            assertContains(
                "APP_ARGS=thinClient debug://localhost:6092#remoteId=Split%20Mode --refresh-split-mode-token=true",
                output
            )
        }
    }

    @Test
    fun `runIdeFrontend launches thin client with frontend properties`() {
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(delayMs = 0, port = 6093, joinLink = "tcp://127.0.0.1:6093#cb=fake")

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("MAIN_CLASS=com.intellij.platform.runtime.loader.IntellijLoader", output)
            assertContains(
                "APP_ARGS=thinClient tcp://127.0.0.1:6093#cb=fake&remoteId=Split%20Mode --refresh-split-mode-token=true",
                output
            )
            assertContains("Split-mode frontend sandbox paths:", output)
            assertContains("idea.config.path=", output)
            assertContains("config_${Tasks.RUN_IDE_FRONTEND}/frontend", output)
            assertContains("idea.system.path=", output)
            assertContains("system_${Tasks.RUN_IDE_FRONTEND}/frontend", output)
            assertContains("idea.log.path=", output)
            assertContains("log_${Tasks.RUN_IDE_FRONTEND}/frontend", output)
            assertContains("idea.plugins.path=", output)
            assertContains("plugins_${Tasks.RUN_IDE_FRONTEND}/frontend", output)
            assertContains("frontend.properties", output)
            assertContains("-Didea.platform.prefix=JetBrainsClient", output)
            assertNotContains("coroutines-javaagent", output)
            assertNotContains("hot-reload-agent", output)
            assertNotContains("AllowEnhancedClassRedefinition", output)
            assertNotContains("-Xbootclasspath/a:", output)
            assertNotContains("java.nio.file.spi.DefaultFileSystemProvider", output)
            assertNotContains("idea.reset.classpath.from.manifest", output)
            assertNotContains("-Didea.config.path=", output)
            assertNotContains("-Didea.system.path=", output)
            assertNotContains("-Didea.log.path=", output)
            assertNotContains("-Didea.plugins.path=", output)
            assertNotContains("-Dplugin.path=", output)
            assertNotContains(
                "APP_ARGS=thinClient tcp://127.0.0.1:6093#cb=fake&remoteId=Split%20Mode splitMode",
                output
            )
        }
    }

    @Test
    fun `runIdeFrontend removes old frontend log directories when requested`() {
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(delayMs = 0, port = 6096, joinLink = "tcp://127.0.0.1:6096#cb=fake")
        buildFile.toFile().appendText(
            """

            tasks.named<RunIdeTask>("${Tasks.RUN_IDE_FRONTEND}") {
                purgeOldLogDirectories.set(true)
            }
            """.trimIndent()
        )

        val frontendLogDirectory = sandbox.resolve("log_${Tasks.RUN_IDE_FRONTEND}/frontend")
        val staleLogDirectoryOne = frontendLogDirectory.resolve("client-1")
        val staleLogDirectoryTwo = frontendLogDirectory.resolve("client-2")
        staleLogDirectoryOne.createDirectories()
        staleLogDirectoryTwo.createDirectories()
        staleLogDirectoryOne.resolve("idea.log").toFile().writeText("stale-1")
        staleLogDirectoryTwo.resolve("idea.log").toFile().writeText("stale-2")

        build(Tasks.RUN_IDE_FRONTEND)

        assertFalse(staleLogDirectoryOne.exists())
        assertFalse(staleLogDirectoryTwo.exists())
        assertTrue(frontendLogDirectory.exists())
    }

    @Test
    fun `runIdeFrontend uses shared sandbox plugins directory for BOTH target`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    splitMode = true
                    pluginInstallationTarget = org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.PluginInstallationTarget.BOTH
                }

                tasks.named("${Tasks.RUN_IDE_FRONTEND}") {
                    doFirst {
                        val sandboxPluginsDirectoryProvider = javaClass
                            .getMethod("getSandboxPluginsDirectory")
                            .invoke(this) as org.gradle.api.file.DirectoryProperty
                        println("FRONTEND_SANDBOX_PLUGINS=" + sandboxPluginsDirectoryProvider.get().asFile.invariantSeparatorsPath)
                    }
                }
                """.trimIndent()
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(delayMs = 0, port = 6095, joinLink = "tcp://127.0.0.1:6095#cb=both")

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("FRONTEND_SANDBOX_PLUGINS=", output)
            assertContains("plugins_runIdeFrontend", output)
            assertNotContains("plugins_runIdeFrontend/frontend", output)
        }
    }

    @Test
    fun `runIdeFrontend waits for delayed join link file`() {
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(delayMs = 500, port = 6094, joinLink = "tcp://127.0.0.1:6094#cb=delayed")

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("MAIN_CLASS=com.intellij.platform.runtime.loader.IntellijLoader", output)
            assertContains(
                "APP_ARGS=thinClient tcp://127.0.0.1:6094#cb=delayed&remoteId=Split%20Mode --refresh-split-mode-token=true",
                output
            )
        }
    }

    @Test
    fun `runIdeFrontend ignores stale join link and waits for live backend`() {
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(delayMs = 500, port = 6091, joinLink = "tcp://127.0.0.1:6091#cb=fresh")

        val sandboxDir = dir.resolve(".intellijPlatform/sandbox/projectName/IU-2025.1.6")
        sandboxDir.toFile().mkdirs()
        sandboxDir.resolve("split-mode-frontend.join.link").toFile().writeText("tcp://127.0.0.1:6090#cb=stale")

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("MAIN_CLASS=com.intellij.platform.runtime.loader.IntellijLoader", output)
            assertContains(
                "APP_ARGS=thinClient tcp://127.0.0.1:6091#cb=fresh&remoteId=Split%20Mode --refresh-split-mode-token=true",
                output
            )
            assertNotContains(
                "APP_ARGS=thinClient tcp://127.0.0.1:6090#cb=stale&remoteId=Split%20Mode --refresh-split-mode-token=true",
                output
            )
        }
    }
}
