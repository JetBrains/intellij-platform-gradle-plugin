// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Test

@Suppress("PluginXmlCapitalization", "PluginXmlValidity", "ComplexRedundantLet")
class VerifyPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `do not fail on warning by default`() {
        buildFile.groovy(
            """
            version '1.0'
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.VERIFY_PLUGIN).let {
            assertContains("Plugin name specified in plugin.xml should not contain the word 'plugin'", it.output)
        }
    }

    @Test
    fun `fail on warning if option is disabled`() {
        buildFile.groovy(
            """
            version '1.0'
            
            verifyPlugin {
                ignoreWarnings = false
            }
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin version="2">
                <name>intellijtest</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildAndFail(Tasks.VERIFY_PLUGIN).let {
            assertContains("Plugin name specified in plugin.xml should not contain the word 'IntelliJ'", it.output)
        }
    }

    @Test
    fun `fail on unacceptable warnings by default`() {
        buildFile.groovy(
            """
            version '1.0'
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildAndFail(Tasks.VERIFY_PLUGIN).let {
            assertContains("Invalid plugin descriptor 'description': Please provide a long-enough English description.", it.output)
        }
    }

    @Test
    fun `do not fail on unacceptable warnings if option is enabled`() {
        buildFile.groovy(
            """
            version '1.0'
            
            verifyPlugin {
                ignoreUnacceptableWarnings = true
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

        build(Tasks.VERIFY_PLUGIN).let {
            assertContains("Invalid plugin descriptor 'description': Please provide a long-enough English description.", it.output)
        }
    }

    @Test
    fun `fail on errors by default`() {
        pluginXml.delete()
        buildAndFail(Tasks.VERIFY_PLUGIN).let {
            assertContains("Plugin descriptor 'plugin.xml' is not found", it.output)
        }
    }

    @Test
    fun `do not fail on errors if option is enabled`() {
        buildFile.groovy(
            """
            verifyPlugin {
                ignoreFailures = true
            }
            """.trimIndent()
        )

        pluginXml.delete()
        build(Tasks.VERIFY_PLUGIN).let {
            assertContains("Plugin descriptor 'plugin.xml' is not found", it.output)
        }
    }

    @Test
    fun `fail on errors if ignore unacceptable warnings option is enabled`() {
        buildFile.groovy(
            """
            version '1.0'
            
            verifyPlugin {
                ignoreUnacceptableWarnings = true
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

        buildAndFail(Tasks.VERIFY_PLUGIN).let {
            assertContains("<name> must not be equal to default value:", it.output)
        }
    }

    @Test
    fun `do not fail on unacceptable warnings if ignoreFailures option is enabled`() {
        buildFile.groovy(
            """
            version '1.0'
            
            verifyPlugin {
                ignoreFailures = true
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

        build(Tasks.VERIFY_PLUGIN).let {
            assertContains("Invalid plugin descriptor 'description': Please provide a long-enough English description.", it.output)
        }
    }

    @Test
    fun `do not fail if there are no errors and warnings`() {
        buildFile.groovy(
            """
            version '1.0'
            
            verifyPlugin { 
                ignoreWarnings = false 
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

        build(Tasks.VERIFY_PLUGIN).let {
            assertNotContains("Plugin verification", it.output)
        }
    }
}
