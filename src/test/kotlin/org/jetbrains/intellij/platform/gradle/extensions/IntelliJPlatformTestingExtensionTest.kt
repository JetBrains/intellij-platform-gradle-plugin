// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test

class IntelliJPlatformTestingExtensionTest : IntelliJPluginTestBase() {

    @Test
    fun `custom testIde task with testFramework can be registered`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        name = "myPluginName"
                    }
                }
                
                val customTest by intellijPlatformTesting.testIde.registering {
                    testFramework(TestFrameworkType.Platform)
                
                    task {
                        useJUnitPlatform()
                    }
                }
                """.trimIndent()

        build("tasks", "--all") {
            // Verify the task is registered successfully
            assertContains("customTest", output)
        }
    }

    @Test
    fun `custom testIde task with JUnit5 testFramework can be registered`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        name = "myPluginName"
                    }
                }
                
                val myCustomTest by intellijPlatformTesting.testIde.registering {
                    testFramework(TestFrameworkType.JUnit5)
                
                    task {
                        useJUnitPlatform()
                    }
                }
                """.trimIndent()

        build("tasks", "--all") {
            // Verify the task is registered successfully
            assertContains("myCustomTest", output)
        }
    }

    @Test
    fun `custom testIde task with custom version testFramework can be registered`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        name = "myPluginName"
                    }
                }
                
                val versionedTest by intellijPlatformTesting.testIde.registering {
                    testFramework(TestFrameworkType.Platform, "1.0.0")
                
                    task {
                        useJUnitPlatform()
                    }
                }
                """.trimIndent()

        build("tasks", "--all") {
            // Verify the task is registered successfully
            assertContains("versionedTest", output)
        }

        // Verify the test framework dependency is in the configuration
        build("dependencies", "--configuration", "intellijPlatformTestDependencies_versionedTest") {
            assertContains("com.jetbrains.intellij.platform:test-framework", output)
        }
    }

    @Test
    fun `custom testIde task with testFramework and Provider version can be registered`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        name = "myPluginName"
                    }
                }
                
                val versionProvider = provider { "1.0.0" }
                
                val providerTest by intellijPlatformTesting.testIde.registering {
                    testFramework(TestFrameworkType.Platform, versionProvider)
                
                    task {
                        useJUnitPlatform()
                    }
                }
                """.trimIndent()

        build("tasks", "--all") {
            // Verify the task is registered successfully
            assertContains("providerTest", output)
        }
    }
}
