// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.internal.jvm.Jvm
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.test.Test

class ExecutableArchValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve the architecture of the provided JVM`() {
        val executablePath = Jvm.current().javaExecutable
        val currentArch = System.getProperty("os.arch")

        buildFile.kotlin(
            """
            tasks {
                val executableArch = providers.of(org.jetbrains.intellij.platform.gradle.providers.ExecutableArchValueSource::class) {
                    parameters {
                        executable = file("$executablePath")
                    }
                }
                
                val resolveExecutableArch by registering {
                    doLast {
                        println("Executable Arch: " + executableArch.get())
                    }
                }
            }
            """.trimIndent()
        )

        build("resolveExecutableArch") {
            assertContains("Executable Arch: $currentArch", output)
        }
    }
}
