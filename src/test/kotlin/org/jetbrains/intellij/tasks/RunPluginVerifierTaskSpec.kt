// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity", "ComplexRedundantLet")
class RunPluginVerifierTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `run plugin verifier in specified version`() {
        writePluginXmlFile()
        buildFile.groovy(
            """
            version = "1.0.0"
            
            runPluginVerifier {
                ideVersions = ["2020.2.3"]
                verifierVersion = "1.255"
            }
            """.trimIndent()
        )

        build(RUN_PLUGIN_VERIFIER_TASK_NAME).let {
            assertContains("Starting the IntelliJ Plugin Verifier 1.255", it.output)
        }
    }

    private fun writePluginXmlFile() {
        pluginXml.xml(
            """
            <idea-plugin>
                <name>MyName</name>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
                <depends>com.intellij.modules.platform</depends>
            </idea-plugin>
            """.trimIndent()
        )
    }
}
