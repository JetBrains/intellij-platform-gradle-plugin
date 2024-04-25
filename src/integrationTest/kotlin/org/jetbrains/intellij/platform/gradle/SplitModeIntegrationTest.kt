// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Ignore
import kotlin.test.Test

class SplitModeIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "split-mode",
) {

    private val sufficientVersion = "2024.1"

    override val defaultProjectProperties
        get() = super.defaultProjectProperties + mapOf("splitMode" to true)

    @Test
    @Ignore("${Tasks.RUN_IDE} task never finishes")
    fun `run IDE in Split Mode`() {
        buildAndFail(Tasks.RUN_IDE, projectProperties = defaultProjectProperties + mapOf("intellijPlatform.version" to sufficientVersion)) {
            assertContains("com.intellij.idea.Main splitMode", output)
            assertContains("Timeout has been exceeded", output)
        }
    }

    @Test
    fun `fail running Split Mode with too low IntelliJ Platform version`() {
        buildAndFail(Tasks.RUN_IDE, projectProperties = defaultProjectProperties) {
            assertContains("Split Mode requires the IntelliJ Platform in version '241.14473' or later, but '223.8836.41' was provided.", output)
        }
    }

    @Test
    fun `fail when applying custom arguments to the task with args`() {
        buildFile write //language=kotlin
                """            
                tasks {
                    runIde {
                        splitMode = splitModeProperty
                        args("foo")
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.RUN_IDE, projectProperties = defaultProjectProperties + mapOf("intellijPlatform.version" to sufficientVersion)) {
            assertContains("Passing arguments directly is not supported in Split Mode. Use `argumentProviders` instead.", output)
        }
    }

    @Test
    @Ignore("${Tasks.RUN_IDE} task never finishes")
    fun `accept arguments provided with argumentProviders`() {
        buildFile write //language=kotlin
                """            
                tasks {
                    runIde {
                        splitMode = splitModeProperty
                        argumentProviders.add(CommandLineArgumentProvider { listOf("foo") })
                    }
                }
                """.trimIndent()

        build(Tasks.RUN_IDE, projectProperties = defaultProjectProperties + mapOf("intellijPlatform.version" to sufficientVersion)) {
            assertContains("com.intellij.idea.Main splitMode foo", output)
        }
    }
}
