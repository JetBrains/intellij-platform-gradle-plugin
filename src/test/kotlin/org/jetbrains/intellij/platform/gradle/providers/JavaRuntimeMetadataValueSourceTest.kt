// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.internal.jvm.Jvm
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaRuntimeMetadataValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `include runtime output when the metadata probe fails`() {
        val isWindows = System.getProperty("os.name").startsWith("Windows")
        val executable = dir.resolve(if (isWindows) "runtime.bat" else "runtime")
        executable.writeText(
            if (isWindows) {
                "@echo off\r\necho Runtime standard output\r\necho Runtime startup failed 1>&2\r\nexit /b 4\r\n"
            } else {
                "#!/bin/sh\nprintf 'Runtime standard output\\n'\nprintf 'Runtime startup failed\\n' >&2\nexit 4\n"
            }
        )
        assertTrue(executable.toFile().setExecutable(true))

        buildFile write //language=kotlin
                """
                abstract class ScriptRuntimeMetadataValueSource @javax.inject.Inject constructor(
                    private val delegate: org.gradle.process.ExecOperations,
                ) : org.jetbrains.intellij.platform.gradle.providers.JavaRuntimeMetadataValueSource() {
                    override val execOperations = object : org.gradle.process.ExecOperations {
                        override fun exec(action: Action<in org.gradle.process.ExecSpec>) = delegate.exec {
                            action.execute(this)
                            if (System.getProperty("os.name").startsWith("Windows")) {
                                commandLine = listOf("cmd.exe", "/d", "/c") + commandLine
                            }
                        }

                        override fun javaexec(action: Action<in org.gradle.process.JavaExecSpec>) = delegate.javaexec(action)
                    }
                }

                val runtimeMetadata = providers.of(ScriptRuntimeMetadataValueSource::class) {
                    parameters.executable = file("${executable.invariantSeparatorsPathString}")
                }

                tasks.register("$randomTaskName") {
                    doLast {
                        println(runtimeMetadata.get())
                    }
                }
                """.trimIndent()

        buildAndFail(randomTaskName) {
            assertContains("Runtime startup failed", output)
            assertContains("Runtime standard output", output)
            assertContains("non-zero exit value 4", output)
            assertContains(executable.fileName.toString(), output)
        }
    }

    @Test
    fun `resolve the architecture of the provided JVM`() {
        val executablePath = Jvm.current().javaExecutable.toPath().invariantSeparatorsPathString
        val currentArch = System.getProperty("os.arch")

        buildFile write //language=kotlin
                """
                tasks {
                    val runtimeMetadata = providers.of(org.jetbrains.intellij.platform.gradle.providers.JavaRuntimeMetadataValueSource::class) {
                        parameters {
                            executable = file("$executablePath")
                        }
                    }
                    
                    register("$randomTaskName") {
                        doLast {
                            println("Runtime Metadata: " + runtimeMetadata.get()["os.arch"])
                        }
                    }
                }
                """.trimIndent()

        build(randomTaskName) {
            assertLogValue("Runtime Metadata: ") {
                assertEquals(currentArch, it)
            }
        }
    }
}
