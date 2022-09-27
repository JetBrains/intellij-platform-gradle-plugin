// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test

@Suppress("PluginXmlCapitalization", "PluginXmlValidity")
class VerifyPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `do not fail on warning by default`() {
        buildFile.groovy("""
            version '1.0'
        """)

        pluginXml.xml("""
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertContains("Plugin name specified in plugin.xml should not contain the word 'plugin'", result.output)
    }

    @Test
    fun `fail on warning if option is disabled`() {
        buildFile.groovy("""
            version '1.0'

            verifyPlugin {
                ignoreWarnings = false
            }
        """)

        pluginXml.xml("""
            <idea-plugin version="2">
                <name>intellijtest</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
        """)

        val result = buildAndFail(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertContains("Plugin name specified in plugin.xml should not contain the word 'IntelliJ'", result.output)
    }

    @Test
    fun `fail on unacceptable warnings by default`() {
        buildFile.groovy("""
            version '1.0'
        """)

        pluginXml.xml("""
            <idea-plugin>
                <name>PluginName</name>
                <description>Lorem ipsum.</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
        """)

        val result = buildAndFail(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertContains("Description is too short", result.output)
    }

    @Test
    fun `do not fail on unacceptable warnings if option is enabled`() {
        buildFile.groovy("""
            version '1.0'

            verifyPlugin {
                ignoreUnacceptableWarnings = true
            }
        """)

        pluginXml.xml("""
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Привет, Мир!</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertContains("Description is too short", result.output)
    }

    @Test
    fun `fail on errors by default`() {
        val result = buildAndFail(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertContains("Plugin descriptor 'plugin.xml' is not found", result.output)
    }

    @Test
    fun `do not fail on errors if option is enabled`() {
        buildFile.groovy("""
            verifyPlugin {
                ignoreFailures = true
            }
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertContains("Plugin descriptor 'plugin.xml' is not found", result.output)
    }

    @Test
    fun `fail on errors if ignore unacceptable warnings option is enabled`() {
        buildFile.groovy("""
            version '1.0'

            verifyPlugin {
                ignoreUnacceptableWarnings = true
            }
        """)

        pluginXml.xml("""
            <idea-plugin version="2">
                <name>Plugin display name here</name>
                <description>Привет, Мир!</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
        """)

        val result = buildAndFail(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertContains("<name> must not be equal to default value:", result.output)
    }

    @Test
    fun `do not fail on unacceptable warnings if ignoreFailures option is enabled`() {
        buildFile.groovy("""
            version '1.0'

            verifyPlugin {
                ignoreFailures = true
            }
        """)

        pluginXml.xml("""
            <idea-plugin version="2">
                <name>PluginName</name>
                <description>Привет, Мир!</description>
                <vendor>Zolotov</vendor>
            </idea-plugin>
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertContains("Description is too short", result.output)
    }

    @Test
    fun `do not fail if there are no errors and warnings`() {
        buildFile.groovy("""
            version '1.0'

            verifyPlugin { 
                ignoreWarnings = false 
            }
        """)

        pluginXml.xml("""
            <idea-plugin>
                <name>Verification test</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
                <depends>com.intellij.modules.lang</depends>
            </idea-plugin>
        """)

        val result = build(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        assertNotContains("Plugin verification", result.output)
    }

    @Test
    fun `reuse configuration cache`() {
        buildAndFail(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, "--configuration-cache")
        val result = buildAndFail(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, "--configuration-cache")
        assertContains("Reusing configuration cache.", result.output)
    }
}
