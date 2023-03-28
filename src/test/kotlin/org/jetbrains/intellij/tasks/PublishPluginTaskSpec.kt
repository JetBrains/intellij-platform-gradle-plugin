// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginSpecBase
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
