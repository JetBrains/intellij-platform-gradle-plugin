// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.internal.jvm.Jvm
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.kotlin
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertEquals

class JavaRuntimeArchitectureValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve the architecture of the provided JVM`() {
        val executablePath = Jvm.current().javaExecutable.toPath().invariantSeparatorsPathString
        val currentArch = System.getProperty("os.arch")

        buildFile.kotlin(
            """
            tasks {
                val runtimeArchitecture = providers.of(org.jetbrains.intellij.platform.gradle.providers.JavaRuntimeArchitectureValueSource::class) {
                    parameters {
                        executable = file("$executablePath")
                    }
                }
                
                register("$randomTaskName") {
                    doLast {
                        println("Runtime Architecture: " + runtimeArchitecture.get())
                    }
                }
            }
            """.trimIndent()
        )

        build(randomTaskName) {
            assertLogValue("Runtime Architecture: ") {
                assertEquals(currentArch, it)
            }
        }
    }
}
