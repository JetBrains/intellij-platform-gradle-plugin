// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.Version
import kotlin.io.path.*
import kotlin.test.Test

private const val CLEAN = "clean"
private const val HEADER = "The following plugin configuration issues were found"

class VerifyPluginProjectConfigurationTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `do not show errors when configuration is valid`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="212" until-build='212.*' />
                </idea-plugin>
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report too low since-build`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "211"
                        }
                    }
                }
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="211" until-build='212.*' />
                </idea-plugin>
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            val major = Version.parse(intellijPlatformBuildNumber).major

            assertContains(HEADER, output)
            assertContains(
                "- since-build is lower than target platform version The since-build='211' (major version 211) is lower than the target IntelliJ Platform major version '$major'. This means your plugin declares support for older IDE versions than you're building against. Update since-build in plugin.xml to match or exceed the target platform version: '$major'.", output
            )
        }
    }

    @Test
    fun `report too low Java sourceCompatibility`() {
        buildFile write //language=kotlin
                """
                java {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- Java sourceCompatibility too low for target platform Java sourceCompatibility is set to '1.8', but IntelliJ Platform '$intellijPlatformVersion' requires Java '21'. This mismatch may prevent your plugin from compiling or using platform APIs correctly. Update sourceCompatibility to '21' in your build configuration.", output
            )
        }
    }

    @Test
    fun `report too high Java targetCompatibility`() {
        buildFile write //language=kotlin
                """
                java {
                    targetCompatibility = JavaVersion.VERSION_25
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- Java targetCompatibility too high for target platform Java targetCompatibility is set to '25', but IntelliJ Platform '$intellijPlatformVersion' only supports Java '21'. This creates bytecode that cannot be executed by the target platform. Lower targetCompatibility to '21' to match the platform's Java version.", output
            )
        }
    }

    @Test
    fun `do not report too high patch number in Kotlin apiVersion`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="211" until-build='212.*' />
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                kotlin {
                    compilerOptions {
                        apiVersion = KotlinVersion.fromVersion("1.6")
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `do not report too low patch number in Kotlin languageVersion`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="212" until-build='212.*' />
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                kotlin {
                    compilerOptions {
                        languageVersion = KotlinVersion.fromVersion("2.1")
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report too low Kotlin languageVersion`() {
        buildFile write //language=kotlin
                """
                kotlin {
                    compilerOptions {
                        languageVersion = KotlinVersion.fromVersion("1.5")
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- Kotlin languageVersion too low for target platform Kotlin languageVersion is set to '1.5', but IntelliJ Platform '$intellijPlatformVersion' requires Kotlin '2.1'. This may cause compatibility issues with platform APIs. Update Kotlin languageVersion to '2.1' in your build configuration.", output
            )
        }
    }

    @Test
    fun `report Kotlin stdlib bundling`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="212" until-build='212.*' />
                </idea-plugin>
                """.trimIndent()

        // kotlin.stdlib.default.dependency gets unset
        gradleProperties overwrite ""

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- Kotlin stdlib dependency conflict The Kotlin Standard Library (stdlib) is automatically added by the Gradle Kotlin plugin and may conflict with the version bundled in IntelliJ Platform. This can cause ClassNotFoundException or version mismatch issues at runtime. Exclude the Kotlin stdlib dependency by setting 'kotlin.stdlib.default.dependency=false' in gradle.properties. See: https://jb.gg/intellij-platform-kotlin-stdlib",
                output
            )
        }

        gradleProperties overwrite //language=properties
                """
                kotlin.stdlib.default.dependency = true
                """.trimIndent()

        build(CLEAN, Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- Kotlin stdlib dependency conflict The Kotlin Standard Library (stdlib) is automatically added by the Gradle Kotlin plugin and may conflict with the version bundled in IntelliJ Platform. This can cause ClassNotFoundException or version mismatch issues at runtime. Exclude the Kotlin stdlib dependency by setting 'kotlin.stdlib.default.dependency=false' in gradle.properties. See: https://jb.gg/intellij-platform-kotlin-stdlib",
                output
            )
        }

        gradleProperties overwrite //language=properties
                """
                kotlin.stdlib.default.dependency = false
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report kotlinx-coroutines dependency`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1")
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- Kotlin Coroutines library must not be added explicitly The Kotlin Coroutines library is bundled with IntelliJ Platform and should not be added as a project dependency. Including it explicitly may cause version conflicts and runtime errors. Remove kotlinx-coroutines dependencies from your build configuration. The platform provides the correct version automatically. See: https://jb.gg/intellij-platform-kotlin-coroutines",
                output
            )
        }
    }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `report IntelliJ Platform cache missing in gitignore`() {
        val message =
            "- IntelliJ Platform cache not excluded from VCS The IntelliJ Platform cache directory ('$CACHE_DIRECTORY') is not listed in .gitignore. This directory contains downloaded IDE dependencies and should not be committed to version control. Add '$CACHE_DIRECTORY' entry to your .gitignore file."

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="223.8836" until-build='223.*' />
                </idea-plugin>
                """.trimIndent()

        // default IntelliJ Platform cache, missing .gitignore -> skip
        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }

        val gitignore = dir.resolve(".gitignore").createFile()

        // default IntelliJ Platform cache, present .gitignore, entry missing -> warn
        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(message, output)
        }

        gitignore.appendText(CACHE_DIRECTORY)

        // default IntelliJ Platform cache, present .gitignore, entry present -> skip
        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }

        gitignore.writeText("")
        dir.resolve(CACHE_DIRECTORY).deleteRecursively()
        val cachePath = dir.resolve(".foo").invariantSeparatorsPathString
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.intellijPlatformCache=$cachePath
                """.trimIndent()

        // custom IntelliJ Platform cache, present .gitignore, entry missing -> skip
        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report used until-build`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "243"
                            untilBuild = "243.*"
                        }
                    }
                }
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="243" until-build="243.*" />
                </idea-plugin>
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- until-build property should be removed For IntelliJ Platform 2024.3+ (build 243+), the until-build property restricts plugin compatibility with future IDE versions. This prevents users from installing your plugin when they update to newer IDE versions. Remove the until-build property from plugin.xml to allow forward compatibility with future IDE versions.", output
            )
        }
    }

    @Test
    fun `report invalid sinceBuild if contains wildcard`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "211.*"
                        }
                    }
                }
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="211.*" until-build='212.*' />
                </idea-plugin>
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- Invalid since-build version format The since-build='211.*' contains a wildcard character (*). Wildcards are not supported in since-build declarations and may cause compatibility issues. Remove the wildcard from since-build in plugin.xml. Use a specific version number like '211'.", output
            )
        }
    }

    @Test
    fun `mute specific messages`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "211.*"
                        }
                    }
                }
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <description>Lorem ipsum.</description>
                    <vendor>JetBrains</vendor>
                    <idea-version since-build="211.*" until-build='212.*' />
                </idea-plugin>
                """.trimIndent()

        // First run without muting - should show the warning
        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains("Invalid since-build version format", output)
        }

        // Now add the muted message pattern to gradle.properties
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.verifyPluginProjectConfigurationMutedMessages=Invalid since-build version format
                """.trimIndent()

        // Run again with muting - should not show the warning
        build(CLEAN, Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains("Invalid since-build version format The since-build='211.*'", output)
        }
    }
}
