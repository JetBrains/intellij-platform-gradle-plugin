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
        buildFile.toFile().appendText(
            """

            tasks.named<org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask>("${Tasks.RUN_IDE_BACKEND}") {
                debugOptions.enabled.set(true)
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_BACKEND)

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("com.intellij.platform.runtime.loader.IntellijLoader thinClient debug://localhost:5990#remoteId=Split%20Mode", output)
        }
    }

    @Test
    fun `runIdeFrontend launches thin client with frontend properties`() {
        configureFakeJavaLauncher()

        build(Tasks.RUN_IDE_BACKEND)

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("ARGS=", output)
            assertContains("com.intellij.platform.runtime.loader.IntellijLoader thinClient tcp://127.0.0.1:5990#cb=fake", output)
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
            assertNotContains("thinClient tcp://127.0.0.1:5990#cb=fake splitMode", output)
        }
    }

    @Test
    fun `runIdeFrontend waits for delayed join link file`() {
        configureFakeJavaLauncher()

        buildFile.toFile().appendText(
            """

            tasks.named("${Tasks.RUN_IDE_FRONTEND}") {
                doFirst {
                    val joinLinkFileProvider = javaClass
                        .getMethod("getSplitModeFrontendJoinLinkFile")
                        .invoke(this) as org.gradle.api.provider.Provider<*>
                    val joinLinkFile = (joinLinkFileProvider.get() as org.gradle.api.file.RegularFile).asFile
                    Thread {
                        Thread.sleep(500)
                        joinLinkFile.parentFile.mkdirs()
                        joinLinkFile.writeText("tcp://127.0.0.1:5990#cb=delayed")
                    }.start()
                }
            }
            """.trimIndent()
        )

        build(Tasks.RUN_IDE_FRONTEND) {
            assertContains("com.intellij.platform.runtime.loader.IntellijLoader thinClient tcp://127.0.0.1:5990#cb=delayed", output)
        }
    }
}
