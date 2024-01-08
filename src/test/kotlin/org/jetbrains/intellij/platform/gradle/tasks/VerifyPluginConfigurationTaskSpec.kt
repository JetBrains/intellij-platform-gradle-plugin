// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.internal.impldep.org.testng.annotations.BeforeTest
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.io.path.*
import kotlin.test.Test

class VerifyPluginConfigurationTaskSpec : IntelliJPluginSpecBase() {

    @OptIn(ExperimentalPathApi::class)
    @BeforeTest
    override fun setup() {
        super.setup()

        gradleArguments.add("-Duser.home=$gradleHome")
        Path(gradleHome).resolve(".pluginVerifier/ides").run {
            deleteRecursively()
            createDirectories()
        }
    }

    @Test
    fun `do not show errors when configuration is valid`() {
        gradleProperties.properties(
            """
            kotlin.incremental.useClasspathSnapshot = false
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
                <idea-version since-build="212" until-build='212.*' />
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report too low since-build`() {
        buildFile.kotlin(
            """
            intellijPlatform {
                pluginConfiguration {
                    ideaVersion {
                        sinceBuild = "211"
                    }
                }
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
                <idea-version since-build="211" until-build='212.*' />
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains("- The 'since-build' property is lower than the target IntelliJ Platform major version: 211 < 223.", output)
        }
    }

    @Test
    fun `report too low Java sourceCompatibility`() {
        buildFile.kotlin(
            """
            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The Java configuration specifies sourceCompatibility=1.8 but IntelliJ Platform 2022.3.3 requires sourceCompatibility=17.",
                output
            )
        }
    }

    @Test
    fun `report too high Java targetCompatibility`() {
        buildFile.kotlin(
            """
            java {
                targetCompatibility = JavaVersion.VERSION_19
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The Java configuration specifies targetCompatibility=19 but IntelliJ Platform 2022.3.3 requires targetCompatibility=17.",
                output
            )
        }
    }

    @Test
    fun `report too high Kotlin jvmTarget`() {
        buildFile.kotlin(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                kotlinOptions {
                    jvmTarget = "19"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains("- The Kotlin configuration specifies jvmTarget=19 but IntelliJ Platform 2022.3.3 requires jvmTarget=17.", output)
        }
    }

    @Test
    fun `do not report too high patch number in Kotlin apiVersion`() {
        gradleProperties.properties(
            """
            kotlin.incremental.useClasspathSnapshot = false
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
                <idea-version since-build="211" until-build='212.*' />
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.kotlin(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                kotlinOptions {
                    apiVersion = "1.6"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report too high Kotlin apiVersion`() {
        pluginXml.xml(
            """
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
                <idea-version since-build="211" until-build='212.*' />
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.kotlin(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                kotlinOptions {
                    apiVersion = "1.9"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains("- The Kotlin configuration specifies apiVersion=1.9 but since-build='223.8836' property requires apiVersion=1.7.", output)
        }
    }

    @Test
    fun `do not report too low patch number in Kotlin languageVersion`() {
        gradleProperties.properties(
            """
            kotlin.incremental.useClasspathSnapshot = false
            """.trimIndent()
        )

        buildFile.kotlin(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                kotlinOptions {
                    languageVersion = "1.7"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report too low Kotlin languageVersion`() {
        buildFile.kotlin(
            """
            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
                kotlinOptions {
                    languageVersion = "1.3"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The Kotlin configuration specifies languageVersion=1.3 but IntelliJ Platform 2022.3.3 requires languageVersion=1.7.",
                output
            )
        }
    }

    @Test
    fun `report Kotlin stdlib bundling`() {
        // kotlin.stdlib.default.dependency gets unset
        gradleProperties.writeText(
            """
            systemProp.org.gradle.unsafe.kotlin.assignment = true
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib",
                output
            )
        }

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = true
            kotlin.incremental.useClasspathSnapshot = false
            systemProp.org.gradle.unsafe.kotlin.assignment = true
            """.trimIndent()
        )

        build("clean", Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
        }

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = false
            systemProp.org.gradle.unsafe.kotlin.assignment = true
            """.trimIndent()
        )

        build("clean", Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertNotContains(HEADER, output)
        }
    }

    @Test
    fun `report kotlinx-coroutines dependency`() {
        buildFile.kotlin(
            """
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1")
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            assertContains(HEADER, output)
            assertContains(
                "- The Kotlin Coroutines library should not be added explicitly to the project as it is already provided with the IntelliJ Platform.",
                output
            )
        }
    }

    companion object {
        const val HEADER = "The following plugin configuration issues were found"
    }
}
