// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.BeforeTest
import kotlin.test.Test

@Suppress("ComplexRedundantLet", "RedundantSuppression")
class PublishPluginTaskSpec : IntelliJPluginSpecBase() {

    @BeforeTest
    override fun setup() {
        super.setup()

        pluginXml.xml(
            """
            <idea-plugin>
                <name>PluginName</name>
                <version>0.0.1</version>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )
    }

    @Test
    fun `skip publishing if token is missing`() {
        buildFile.groovy(
            """
            publishPlugin { }
            verifyPlugin {
                ignoreFailures = true
            }
            """.trimIndent()
        )

        buildAndFail(PUBLISH_PLUGIN_TASK_NAME).let {
            assertContains("token property must be specified for plugin publishing", it.output)
        }
    }
}
