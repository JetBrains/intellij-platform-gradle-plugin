// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("PluginXmlCapitalization", "PluginXmlValidity")
class VerifyPluginConfigurationTaskSpec : IntelliJPluginSpecBase() {

    companion object {
        const val HEADER = "The following plugin configuration issues were found"
    }
    
    @Test
    fun `do not show errors when configuration is valid`() {
        pluginXml.xml("""
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
                <idea-version since-build="212" until-build='212.*' />
            </idea-plugin>
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertFalse(result.output.contains(HEADER))
    }

    @Test
    fun `report too low since-build`() {
        buildFile.groovy("""
            patchPluginXml {
                sinceBuild = "211"
            }
        """)

        pluginXml.xml("""
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
                <idea-version since-build="211" until-build='212.*' />
            </idea-plugin>
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertTrue(result.output.contains(HEADER))
        assertTrue(result.output.contains("- The 'since-build' property is lower than the target IntelliJ Platform major version: 211 < 212."))
    }

    @Test
    fun `report too low Java sourceCompatibility`() {
        buildFile.groovy("""
            sourceCompatibility = 1.8
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertTrue(result.output.contains(HEADER))
        assertTrue(result.output.contains("- The Java configuration specifies sourceCompatibility=1.8 but IntelliJ Platform 2021.2.4 requires sourceCompatibility=11."))
    }

    @Test
    fun `report too high Java targetCompatibility`() {
        buildFile.groovy("""
            targetCompatibility = 17
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertTrue(result.output.contains(HEADER))
        assertTrue(result.output.contains("- The Java configuration specifies targetCompatibility=17 but IntelliJ Platform 2021.2.4 requires targetCompatibility=11."))
    }

    @Test
    fun `report too high Kotlin jvmTarget`() {
        buildFile.groovy("""
            compileKotlin {
                kotlinOptions {
                    jvmTarget = "17"
                }
            }
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertTrue(result.output.contains(HEADER))
        assertTrue(result.output.contains("- The Kotlin configuration specifies jvmTarget=17 but IntelliJ Platform 2021.2.4 requires jvmTarget=11."))
    }

    @Test
    fun `report too high Kotlin apiVersion`() {
        pluginXml.xml("""
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
                <idea-version since-build="211" until-build='212.*' />
            </idea-plugin>
        """)

        buildFile.groovy("""
            compileKotlin {
                kotlinOptions {
                    apiVersion = "1.9"
                }
            }
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertTrue(result.output.contains(HEADER))
        assertTrue(result.output.contains("- The Kotlin configuration specifies apiVersion=1.9 but since-build='212.5712' property requires apiVersion=1.5.10."))
    }

    @Test
    fun `report too low Kotlin languageVersion`() {
        buildFile.groovy("""
            compileKotlin {
                kotlinOptions {
                    languageVersion = "1.3"
                }
            }
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertTrue(result.output.contains(HEADER))
        assertTrue(result.output.contains("- The Kotlin configuration specifies languageVersion=1.3 but IntelliJ Platform 2021.2.4 requires languageVersion=1.5.10."))
    }

    @Test
    fun `report Kotlin stdlib bundling`() {
        // kotlin.stdlib.default.dependency gets unset
        gradleProperties.writeText("")

        build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME).let { result ->
            assertTrue(result.output.contains(HEADER))
            assertTrue(result.output.contains("- The dependency on the Kotlin Standard Library (stdlib) is automatically added when using the Gradle Kotlin plugin and may conflict with the version provided with the IntelliJ Platform, see: https://jb.gg/intellij-platform-kotlin-stdlib"))
        }

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = true
        """
        )

        build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME).let { result ->
            assertFalse(result.output.contains(HEADER))
        }

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = false
        """
        )

        build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME).let { result ->
            assertFalse(result.output.contains(HEADER))
        }
    }

    @Test
    fun `reuse configuration cache`() {
        build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
