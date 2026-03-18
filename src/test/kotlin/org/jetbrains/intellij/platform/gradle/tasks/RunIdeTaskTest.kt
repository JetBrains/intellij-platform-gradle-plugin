// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.assertNotContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test

class RunIdeTaskTest : IntelliJPluginTestBase() {

    private val isWindows = System.getProperty("os.name").startsWith("Windows")

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
        val fakeJavaExecutable = dir.resolve("fake-jdk/bin/java" + if (isWindows) ".bat" else "")

        fakeJavaExecutable write if (isWindows) {
            //language=bat
            """
            @echo off
            echo JETBRAINS_CLIENT_JDK=%JETBRAINS_CLIENT_JDK%
            echo JETBRAINS_CLIENT_VM_OPTIONS=%JETBRAINS_CLIENT_VM_OPTIONS%
            echo JETBRAINS_CLIENT_PROPERTIES=%JETBRAINS_CLIENT_PROPERTIES%
            echo JETBRAINS_CLIENT_VM_OPTIONS_CONTENT_START
            if not "%JETBRAINS_CLIENT_VM_OPTIONS%"=="" type "%JETBRAINS_CLIENT_VM_OPTIONS%"
            echo JETBRAINS_CLIENT_VM_OPTIONS_CONTENT_END
            echo %* | findstr /C:"serverMode" >nul && echo Join link: tcp://127.0.0.1:5990#cb=fake
            echo ARGS=%*
            """.trimIndent()
        } else {
            //language=shell script
            """
            #!/bin/sh
            echo "JETBRAINS_CLIENT_JDK=${'$'}JETBRAINS_CLIENT_JDK"
            echo "JETBRAINS_CLIENT_VM_OPTIONS=${'$'}JETBRAINS_CLIENT_VM_OPTIONS"
            echo "JETBRAINS_CLIENT_PROPERTIES=${'$'}JETBRAINS_CLIENT_PROPERTIES"
            echo "JETBRAINS_CLIENT_VM_OPTIONS_CONTENT_START"
            if [ -n "${'$'}JETBRAINS_CLIENT_VM_OPTIONS" ]; then
              cat "${'$'}JETBRAINS_CLIENT_VM_OPTIONS"
            fi
            echo "JETBRAINS_CLIENT_VM_OPTIONS_CONTENT_END"
            case "$*" in
              *serverMode*) echo "Join link: tcp://127.0.0.1:5990#cb=fake" ;;
            esac
            echo "ARGS=$*"
            """.trimIndent()
        }
        fakeJavaExecutable.toFile().setExecutable(true)

        buildFile.toFile().appendText(
            """

            tasks.withType<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>().configureEach {
                val fakeJdk = layout.projectDirectory.dir("fake-jdk")
                javaLauncher = provider {
                    object : org.gradle.jvm.toolchain.JavaLauncher {
                        override fun getExecutablePath() = fakeJdk.file("bin/java${if (isWindows) ".bat" else ""}")

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
            }
            """.trimIndent()
        )
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
    fun `runIdeBackend launches serverMode on configured port`() {
        configureFakeJavaLauncher()

        build(Tasks.RUN_IDE_BACKEND) {
            assertContains("ARGS=", output)
            assertContains("com.intellij.idea.Main serverMode -p 5990", output)
        }

        val joinLinkFiles = dir.toFile()
            .walkTopDown()
            .filter { it.name == "split-mode-frontend.join.link" }
            .toList()

        kotlin.test.assertEquals(1, joinLinkFiles.size)
        kotlin.test.assertEquals("tcp://127.0.0.1:5990#cb=fake", joinLinkFiles.single().readText().trim())
    }

    @Test
    fun `runIdeBackend writes debug frontend join link when backend debugging is enabled`() {
        configureFakeJavaLauncher()
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("${Tasks.RUN_IDE_BACKEND}") {
                debugOptions.enabled.set(true)
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_BACKEND)

        val joinLinkFiles = dir.toFile()
            .walkTopDown()
            .filter { it.name == "split-mode-frontend.join.link" }
            .toList()

        kotlin.test.assertEquals(1, joinLinkFiles.size)
        kotlin.test.assertEquals("debug://localhost:5990#remoteId=Split%20Mode", joinLinkFiles.single().readText().trim())
    }

    @Test
    fun `runIdeFrontend uses debug join link written by debug backend`() {
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(delayMs = 0, port = 6092, joinLink = "debug://localhost:6092#remoteId=Split%20Mode")

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("com.intellij.platform.runtime.loader.IntellijLoader thinClient debug://localhost:6092#remoteId=Split%20Mode", output)
        }
    }

    @Test
    fun `runIdeFrontend launches thin client with frontend properties`() {
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(delayMs = 0, port = 6093, joinLink = "tcp://127.0.0.1:6093#cb=fake")

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("ARGS=", output)
            assertContains("com.intellij.platform.runtime.loader.IntellijLoader thinClient tcp://127.0.0.1:6093#cb=fake", output)
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
            assertNotContains("thinClient tcp://127.0.0.1:6093#cb=fake splitMode", output)
        }
    }

    @Test
    fun `runIdeFrontend waits for delayed join link file`() {
        configureFakeJavaLauncher()
        configureFrontendJoinLinkProvider(delayMs = 500, port = 6094, joinLink = "tcp://127.0.0.1:6094#cb=delayed")

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("com.intellij.platform.runtime.loader.IntellijLoader thinClient tcp://127.0.0.1:6094#cb=delayed", output)
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
            assertContains("com.intellij.platform.runtime.loader.IntellijLoader thinClient tcp://127.0.0.1:6091#cb=fresh", output)
            assertNotContains("com.intellij.platform.runtime.loader.IntellijLoader thinClient tcp://127.0.0.1:6090#cb=stale", output)
        }
    }
}
