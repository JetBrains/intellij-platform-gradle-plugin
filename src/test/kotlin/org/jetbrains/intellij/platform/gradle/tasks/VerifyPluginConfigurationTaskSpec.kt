// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.internal.impldep.org.testng.annotations.BeforeTest
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test

@Suppress("PluginXmlCapitalization", "PluginXmlValidity", "ComplexRedundantLet")
class VerifyPluginConfigurationTaskSpec : IntelliJPluginSpecBase() {

    @BeforeTest
    override fun setup() {
        super.setup()

        gradleArguments.add("-Duser.home=$gradleHome")
        File(gradleHome).resolve(".pluginVerifier/ides").run {
            deleteRecursively()
            mkdirs()
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

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertNotContains(HEADER, it.output)
        }
    }

    @Test
    fun `report too low since-build`() {
        buildFile.groovy(
            """
            patchPluginXml {
                sinceBuild = "211"
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

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertContains(HEADER, it.output)
            assertContains("- The 'since-build' property is lower than the target IntelliJ Platform major version: 211 < 221.", it.output)
        }
    }

    @Test
    fun `report too low Java sourceCompatibility`() {
        buildFile.groovy(
            """
            sourceCompatibility = 1.8
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertContains(HEADER, it.output)
            assertContains(
                "- The Java configuration specifies sourceCompatibility=1.8 but IntelliJ Platform 2022.1.4 requires sourceCompatibility=11.",
                it.output
            )
        }
    }

    @Test
    fun `report too high Java targetCompatibility`() {
        buildFile.groovy(
            """
            targetCompatibility = 17
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertContains(HEADER, it.output)
            assertContains(
                "- The Java configuration specifies targetCompatibility=17 but IntelliJ Platform 2022.1.4 requires targetCompatibility=11.",
                it.output
            )
        }
    }

    @Test
    fun `report too high Kotlin jvmTarget`() {
        buildFile.groovy(
            """
            compileKotlin {
                kotlinOptions {
                    jvmTarget = "17"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertContains(HEADER, it.output)
            assertContains("- The Kotlin configuration specifies jvmTarget=17 but IntelliJ Platform 2022.1.4 requires jvmTarget=11.", it.output)
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

        buildFile.groovy(
            """
            compileKotlin {
                kotlinOptions {
                    apiVersion = "1.6"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertNotContains(HEADER, it.output)
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

        buildFile.groovy(
            """
            compileKotlin {
                kotlinOptions {
                    apiVersion = "1.9"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertContains(HEADER, it.output)
            assertContains("- The Kotlin configuration specifies apiVersion=1.9 but since-build='221.6008' property requires apiVersion=1.6.", it.output)
        }
    }

    @Test
    fun `do not report too low patch number in Kotlin languageVersion`() {
        gradleProperties.properties(
            """
            kotlin.incremental.useClasspathSnapshot = false
            """.trimIndent()
        )

        buildFile.groovy(
            """
            compileKotlin {
                kotlinOptions {
                    languageVersion = "1.6"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertNotContains(HEADER, it.output)
        }
    }

    @Test
    fun `report too low Kotlin languageVersion`() {
        buildFile.groovy(
            """
            compileKotlin {
                kotlinOptions {
                    languageVersion = "1.3"
                }
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertContains(HEADER, it.output)
            assertContains(
                "- The Kotlin configuration specifies languageVersion=1.3 but IntelliJ Platform 2022.1.4 requires languageVersion=1.6.",
                it.output
            )
        }
    }

    @Test
    fun `report Kotlin stdlib bundling`() {
        // kotlin.stdlib.default.dependency gets unset
        gradleProperties.writeText("")

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertContains(HEADER, it.output)
            assertContains(
                "- The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib",
                it.output
            )
        }

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = true
            kotlin.incremental.useClasspathSnapshot = false
            """.trimIndent()
        )

        build("clean", Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertNotContains(HEADER, it.output)
        }

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = false
            """.trimIndent()
        )

        build("clean", Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertNotContains(HEADER, it.output)
        }
    }

    @Test
    fun `report kotlinx-coroutines dependency`() {
        buildFile.groovy(
            """
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1")
            }
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_CONFIGURATION).let {
            assertContains(HEADER, it.output)
            assertContains(
                "- The Kotlin Coroutines library should not be added explicitly to the project as it is already provided with the IntelliJ Platform.",
                it.output
            )
        }
    }

    companion object {
        const val HEADER = "The following plugin configuration issues were found"
    }
}
