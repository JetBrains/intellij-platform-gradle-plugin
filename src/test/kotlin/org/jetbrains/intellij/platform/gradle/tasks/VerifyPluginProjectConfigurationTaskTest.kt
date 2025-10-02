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
                "- The since-build='211' is lower than the target IntelliJ Platform major version: '$major'.", output
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
                "- The Java configuration specifies sourceCompatibility='1.8' but IntelliJ Platform '$intellijPlatformVersion' requires sourceCompatibility='21'.", output
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
                "- The Java configuration specifies targetCompatibility='25' but IntelliJ Platform '$intellijPlatformVersion' requires targetCompatibility='21'.", output
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
                "- The Kotlin configuration specifies languageVersion='1.5' but IntelliJ Platform '$intellijPlatformVersion' requires languageVersion='2.1'.", output
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
                "- The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib",
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
                "- The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib",
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
                "- The Kotlin Coroutines library must not be added explicitly to the project nor as a transitive dependency as it is already provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-coroutines",
                output
            )
        }
    }

    @Test
    @OptIn(ExperimentalPathApi::class)
    fun `report IntelliJ Platform cache missing in gitignore`() {
        val message =
            "- The IntelliJ Platform cache directory should be excluded from the version control system. Add the '$CACHE_DIRECTORY' entry to the '.gitignore' file"

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
                "- The until-build property is not recommended for use. Consider using empty until-build for future plugin versions, so users can use your plugin when they update IDE to the latest version.", output
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
                "- The since-build='211.*' should not contain wildcard.", output
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
            assertContains(
                "- The since-build='211.*' should not contain wildcard.", output
            )
        }

        // Now add the muted message pattern to gradle.properties
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.verifyPluginProjectConfigurationMutedMessages=should not contain wildcard
                """.trimIndent()

        // Run again with muting - should not show the warning
        build(CLEAN, Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(
                "- The since-build='211.*' should not contain wildcard.", output
            )
        }
    }
}
