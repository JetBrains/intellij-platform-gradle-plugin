// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Suppress("GroovyAssignabilityCheck", "ComplexRedundantLet")
class IntelliJPluginSpec : IntelliJPluginSpecBase() {

    @Test
    fun `add require plugin id parameter in test tasks`() {
        writeTestFile()

        pluginXml.xml(
            """
            <idea-plugin>
                <name>Name</name>
                <id>com.intellij.mytestid</id>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(JavaPlugin.TEST_TASK_NAME, "--info").let {
            assertEquals(
                "com.intellij.mytestid",
                parseCommand(it.output).properties["idea.required.plugins.id"],
            )
        }
    }

    private fun parseCommand(output: String) = output.lines()
        .find { it.startsWith("Starting process ") && !it.contains("vm_stat") }
        .let { assertNotNull(it) }
        .substringAfter("Command: ")
        .let(::ProcessProperties)

    class ProcessProperties(command: String) {

        private val jvmArgs = mutableSetOf<String>()

        val properties = mutableMapOf<String, String>()
        var xms = ""
        var xmx = ""
        var xclasspath = ""

        init {
            assertNotNull(command)
            command.trim().split("\\s+".toRegex()).forEach {
                when {
                    it.startsWith("-D") -> {
                        (it.substring(2).split('=') + "").let { (key, value) ->
                            properties[key] = value
                        }
                    }

                    it.startsWith("-Xms") -> xms = it.substring(4)
                    it.startsWith("-Xmx") -> xmx = it.substring(4)
                    it.startsWith("-Xbootclasspath") -> xclasspath = it.substring(15)
                    else -> jvmArgs.add(it)
                }
            }
        }
    }
}
