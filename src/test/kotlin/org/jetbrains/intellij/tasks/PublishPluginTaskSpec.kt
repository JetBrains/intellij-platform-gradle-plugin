package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PublishPluginTaskSpec : IntelliJPluginSpecBase() {

    @BeforeTest
    override fun setUp() {
        super.setUp()

        pluginXml.xml("""
            <idea-plugin>
                <name>PluginName</name>
                <version>0.0.1</version>
                <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                <vendor>JetBrains</vendor>
            </idea-plugin>
        """)
    }

    @Test
    fun `skip publishing if token is missing`() {
        buildFile.groovy("""
            publishPlugin { }
            verifyPlugin {
                ignoreFailures = true
            }
        """)

        val result = buildAndFail(IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME)

        assertTrue(result.output.contains("token property must be specified for plugin publishing"))
    }

    @Test
    fun `reuse configuration cache`() {
        buildFile.groovy("""
            publishPlugin { }
            verifyPlugin {
                ignoreFailures = true
            }
        """)

        buildAndFail(IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME, "--configuration-cache")
        val result = buildAndFail(IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
