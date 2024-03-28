// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.*
import kotlin.test.Test

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
            assertContains(HEADER, output)
            assertContains(
                "- The since-build='211' is lower than the target IntelliJ Platform major version: '223'.", output
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
                "- The Java configuration specifies sourceCompatibility='1.8' but IntelliJ Platform '2022.3.3' requires sourceCompatibility='17'.", output
            )
        }
    }

    @Test
    fun `report too high Java targetCompatibility`() {
        buildFile write //language=kotlin
                """
                java {
                    targetCompatibility = JavaVersion.VERSION_19
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The Java configuration specifies targetCompatibility='19' but IntelliJ Platform '2022.3.3' requires targetCompatibility='17'.", output
            )
        }
    }

    @Test
    fun `report too high Kotlin jvmTarget`() {
        buildFile write //language=kotlin
                """
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                    kotlinOptions {
                        jvmTarget = "19"
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The Kotlin configuration specifies jvmTarget='19' but IntelliJ Platform '2022.3.3' requires jvmTarget='17'.", output
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
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                    kotlinOptions {
                        apiVersion = "1.6"
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report too high Kotlin apiVersion`() {
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
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                    kotlinOptions {
                        apiVersion = "1.9"
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The Kotlin configuration specifies apiVersion='1.9' but since-build='223.8836' property requires apiVersion='1.7'.", output
            )
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
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                    kotlinOptions {
                        languageVersion = "1.7"
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
                tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                    kotlinOptions {
                        languageVersion = "1.3"
                    }
                }
                """.trimIndent()

        build(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The Kotlin configuration specifies languageVersion='1.3' but IntelliJ Platform '2022.3.3' requires languageVersion='1.7'.", output
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

        build(Tasks.External.CLEAN, Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
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
                "- The Kotlin Coroutines library must not be added explicitly to the project as it is already provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-coroutines",
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
}
