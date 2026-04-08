// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.*
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test

class IntelliJPlatformTestingExtensionTest : IntelliJPluginTestBase() {

    @Test
    fun `deprecated splitModeTarget keeps backend default in extension and testing DSL`() {
        buildFile write //language=kotlin
                """
                @file:Suppress("DEPRECATION")

                println("EXT_TARGET=" + intellijPlatform.splitModeTarget.get())

                val customRun by intellijPlatformTesting.runIde.registering {
                    println("CUSTOM_TARGET=" + splitModeTarget.get())
                }
                """.trimIndent()

        build("help") {
            assertContains("EXT_TARGET=BACKEND", output)
            assertContains("CUSTOM_TARGET=BACKEND", output)
        }
    }

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

    @Test
    fun `custom testIde task prefers base local IntelliJ Platform dependency over computed remote archive`() {
        val localIdePath = dir.resolve("android-studio-local")
        localIdePath.resolve("product-info.json") write //language=json
                """
                {
                    "name": "Android Studio",
                    "version": "2024.3",
                    "buildNumber": "243.12345.67",
                    "productCode": "AI"
                }
                """.trimIndent()

        buildFile overwrite //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform")
                }
                
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        local("${localIdePath.invariantSeparatorsPathString}")
                    }
                }
                
                intellijPlatform {
                    buildSearchableOptions = false
                    instrumentCode = false
                    
                    caching {
                        ides {
                            enabled = false
                        }
                    }
                }
                
                val customTest by intellijPlatformTesting.testIde.registering {
                    task {
                        enabled = false
                    }
                }
                """.trimIndent()

        build("dependencies", "--configuration", "intellijPlatformDependency_customTest") {
            assertContains("localIde:AI:AI-243.12345.67", output)
            assertNotContains("com.google.android.studio:android-studio", output)
        }
    }

    @Test
    fun `custom testIde task uses explicit custom IntelliJ Platform dependency over base local dependency`() {
        val localIdePath = dir.resolve("android-studio-local")
        localIdePath.resolve("product-info.json") write //language=json
                """
                {
                    "name": "Android Studio",
                    "version": "2024.3",
                    "buildNumber": "243.12345.67",
                    "productCode": "AI"
                }
                """.trimIndent()

        buildFile overwrite //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform")
                }
                
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        local("${localIdePath.invariantSeparatorsPathString}")
                    }
                }
                
                intellijPlatform {
                    buildSearchableOptions = false
                    instrumentCode = false
                    
                    caching {
                        ides {
                            enabled = false
                        }
                    }
                }
                
                val customTest by intellijPlatformTesting.testIde.registering {
                    type = org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity
                    version = "$intellijPlatformVersion"
                    useInstaller = true
                    
                    task {
                        enabled = false
                    }
                }
                
                configurations.named("intellijPlatformDependency_customTest") {
                    withDependencies {
                        throw Exception(toList().joinToString())
                    }
                }
                """.trimIndent()

        buildAndFail("customTest") {
            assertContains("idea:ideaIC:$intellijPlatformVersion", output)
            assertNotContains("localIde:AI:AI-243.12345.67", output)
        }
    }
}
