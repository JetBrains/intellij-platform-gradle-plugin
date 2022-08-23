// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("PluginXmlCapitalization", "PluginXmlValidity")
class VerifyPluginConfigurationTaskSpec : IntelliJPluginSpecBase() {

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

        assertFalse(result.output.contains("The following compatibility configuration issues were found"))
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

        assertTrue(result.output.contains("The following compatibility configuration issues were found"))
        assertTrue(result.output.contains("- The 'since-build' property is lower than the target IntelliJ Platform major version: 211 < 212."))
    }

    @Test
    fun `report too low Java sourceCompatibility`() {
        buildFile.groovy("""
            sourceCompatibility = 1.8
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertTrue(result.output.contains("The following compatibility configuration issues were found"))
        assertTrue(result.output.contains("- The Java configuration specifies sourceCompatibility=1.8 but IntelliJ Platform 2021.2.4 requires sourceCompatibility=11."))
    }

    @Test
    fun `report too high Java targetCompatibility`() {
        buildFile.groovy("""
            targetCompatibility = 17
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)

        assertTrue(result.output.contains("The following compatibility configuration issues were found"))
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

        assertTrue(result.output.contains("The following compatibility configuration issues were found"))
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

        assertTrue(result.output.contains("The following compatibility configuration issues were found"))
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

        assertTrue(result.output.contains("The following compatibility configuration issues were found"))
        assertTrue(result.output.contains("- The Kotlin configuration specifies languageVersion=1.3 but IntelliJ Platform 2021.2.4 requires languageVersion=1.5.10."))
    }

    @Test
    fun `reuse configuration cache`() {
        build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
