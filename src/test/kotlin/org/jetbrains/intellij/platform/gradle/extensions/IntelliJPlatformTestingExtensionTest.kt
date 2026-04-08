// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.outputStream
import kotlin.io.path.writeText
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
    fun `testFramework excludes bundled IntelliJ Platform modules from transitive dependencies`() {
        val localIdePath = dir.resolve("fake-idea")
        localIdePath.resolve("product-info.json") write //language=json
                """
                {
                    "name": "IntelliJ IDEA Ultimate",
                    "version": "2026.2",
                    "buildNumber": "262.12345.67",
                    "productCode": "IU"
                }
                """.trimIndent()
        localIdePath.resolve("lib").createDirectories().resolve("util-8.jar").writeText("")
        localIdePath.resolve("modules").createDirectories().resolve("module-descriptors.jar").writeModuleDescriptorsJar(
            "intellij.platform.util.xml" to """
                <?xml version="1.0" encoding="UTF-8"?>
                <module name="intellij.platform.util" namespace="jps" visibility="public">
                  <resources>
                    <resource-root path="../lib/util-8.jar"/>
                  </resources>
                </module>
            """.trimIndent(),
        )

        buildFile overwrite //language=kotlin
                """
                import org.gradle.api.artifacts.ExternalModuleDependency
                import org.jetbrains.intellij.platform.gradle.TestFrameworkType
                
                plugins {
                    id("java")
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
                        testFramework(TestFrameworkType.Platform, "262-SNAPSHOT")
                    }
                }
                
                intellijPlatform {
                    buildSearchableOptions = false
                    instrumentCode = false
                }
                
                tasks.register("printExcludes") {
                    doLast {
                        val dependency = configurations
                            .getByName("intellijPlatformTestDependencies")
                            .dependencies
                            .filterIsInstance<ExternalModuleDependency>()
                            .single { it.group == "com.jetbrains.intellij.platform" && it.name == "test-framework" }
                        val excludes = dependency.excludeRules
                            .mapNotNull { rule ->
                                val group = rule.group ?: return@mapNotNull null
                                val module = rule.module ?: return@mapNotNull null
                                "${'$'}group:${'$'}module"
                            }
                            .sorted()
                        
                        println("Excludes=" + excludes.joinToString(";"))
                    }
                }
                """.trimIndent()

        build("printExcludes") {
            assertContains("Excludes=", output)
            assertContains("com.jetbrains.intellij.platform:util", output)
            assertContains("junit:junit", output)
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

    private fun java.nio.file.Path.writeModuleDescriptorsJar(vararg entries: Pair<String, String>) {
        outputStream().use { outputStream ->
            ZipOutputStream(outputStream).use { zip ->
                entries.forEach { (entryName, content) ->
                    zip.putNextEntry(ZipEntry(entryName))
                    zip.write(content.toByteArray())
                    zip.closeEntry()
                }
            }
        }
    }
}
