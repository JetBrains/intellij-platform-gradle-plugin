package org.jetbrains.intellij

import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PublishTaskSpec : IntelliJPluginSpecBase() {

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
    fun `skip publishing plugin is distribution file is missing`() {
        buildFile.groovy("""
            publishPlugin {
                token = 'asd'
                distributionFile = null
            }
            verifyPlugin {
                ignoreFailures = true
            }
        """)

        val result = buildAndFail(IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME)

        assertTrue(result.output.contains("No value has been specified for property 'distributionFile'"))
    }

    @Test
    fun `skip publishing if token is missing`() {
        buildFile.groovy("""
            publishPlugin { }
            verifyPlugin {
                ignoreFailures = true
            }
        """)

        val result = buildAndFail(IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME)

        assertTrue(result.output.contains("token property must be specified for plugin publishing"))
    }
}
