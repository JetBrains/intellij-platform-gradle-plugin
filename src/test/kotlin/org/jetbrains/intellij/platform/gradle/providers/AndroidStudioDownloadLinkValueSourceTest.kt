// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidStudioDownloadLinkValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve the architecture of the provided JVM`() {
        val executablePath = Jvm.current().javaExecutable.toPath().invariantSeparatorsPathString
        val currentArch = System.getProperty("os.arch")

        buildFile write //language=kotlin
                """
                tasks {
                    val androidStudioDownloadLink = providers.of(org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource::class) {
                        parameters {
                            androidStudio = file("${resource("products-releases/android-studio-releases-list.xml")}")
                            androidStudioVersion = "2023.3.1.9"
                        }
                    }
                    
                    register("$randomTaskName") {
                        doLast {
                            println("Download Link: " + androidStudioDownloadLink.get())
                        }
                    }
                }
                """.trimIndent()

        val os = OperatingSystem.current()
        val link = mapOf(
            OperatingSystem.
        )


        build(randomTaskName) {
            assertLogValue("Download Link: ") {
                assertEquals(currentArch, it)
            }
        }
    }
}
