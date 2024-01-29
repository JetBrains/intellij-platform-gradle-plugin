// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.io.path.deleteIfExists
import kotlin.test.Test

class VerifyPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `do not fail on warning by default`() {
        buildFile.kotlin(
            """
            intellijPlatform {
                pluginConfiguration {
                    name = "intellijtest"
                }
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
                <depends>com.intellij.modules.lang</depends>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertContains("Invalid plugin descriptor 'plugin.xml'. The plugin name should not contain the word 'IntelliJ'.", output)
        }
    }

    @Test
    fun `fail on warning if option is disabled`() {
        buildFile.kotlin(
            """
            intellijPlatform {
                pluginConfiguration {
                    name = "intellijtest"
                }
            }
            
            tasks {
                verifyPluginStructure {
                    ignoreWarnings = false
                }
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin version="2">
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildAndFail(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertContains("The plugin name should not contain the word 'IntelliJ'.", output)
        }
    }

    @Test
    fun `fail on unacceptable warnings by default`() {
        pluginXml.xml(
            """
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildAndFail(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertContains("Invalid plugin descriptor 'description'. The plugin description is shorter than 40 characters and/or the plugin description contains non-Latin characters.", output)
        }
    }

    @Test
    fun `do not fail on unacceptable warnings if option is enabled`() {
        buildFile.kotlin(
            """
            tasks {
                verifyPluginStructure {
                    ignoreUnacceptableWarnings = true
                }
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Привет, Мир!</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertContains("Invalid plugin descriptor 'description'. The plugin description is shorter than 40 characters and/or the plugin description contains non-Latin characters.", output)
        }
    }

    @Test
    fun `fail on errors by default`() {
        pluginXml.deleteIfExists()
        buildAndFail(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertContains("The plugin descriptor 'plugin.xml' is not found.", output)
        }
    }

    @Test
    fun `do not fail on errors if option is enabled`() {
        buildFile.kotlin(
            """
            tasks {
                verifyPluginStructure {
                    ignoreFailures = true
                }
            }
            """.trimIndent()
        )

        pluginXml.deleteIfExists()
        build(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertContains("The plugin descriptor 'plugin.xml' is not found.", output)
        }
    }

    @Test
    fun `fail on errors if ignore unacceptable warnings option is enabled`() {
        buildFile.kotlin(
            """
            intellijPlatform {
                pluginConfiguration {
                    name = "Plugin display name here"
                }
            }
            
            tasks {
                verifyPluginStructure {
                    ignoreUnacceptableWarnings = true
                }
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin version="2">
                <name>Plugin display name here</name>
                <description>Привет, Мир!</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildAndFail(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertContains("Please ensure that <name> is not equal to the default value 'Plugin display name here'.", output)
        }
    }

    @Test
    fun `do not fail on unacceptable warnings if ignoreFailures option is enabled`() {
        buildFile.kotlin(
            """
            tasks {
                verifyPluginStructure {
                    ignoreFailures = true
                }
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Привет, Мир!</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertContains("Invalid plugin descriptor 'description'. The plugin description is shorter than 40 characters and/or the plugin description contains non-Latin characters.", output)
        }
    }

    @Test
    fun `do not fail if there are no errors and warnings`() {
        buildFile.kotlin(
            """
            tasks {
                verifyPluginStructure { 
                    ignoreWarnings = false 
                }
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>Verification test</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
                <depends>com.intellij.modules.lang</depends>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN_STRUCTURE) {
            assertNotContains("Plugin verification", output)
        }
    }
}
