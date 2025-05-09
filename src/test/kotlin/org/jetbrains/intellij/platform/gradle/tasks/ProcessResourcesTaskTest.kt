// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test

private const val PROCESS_RESOURCES = "processResources"

class ProcessResourcesTaskTest : IntelliJPluginTestBase() {

    private val outputPluginXml
        get() = buildDirectory.resolve("resources/main/META-INF/").listDirectoryEntries().first()

    @Test
    fun `use patched plugin xml files`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(PROCESS_RESOURCES)

        assertFileContent(
            outputPluginXml,
            """
            <idea-plugin>
              <idea-version since-build="223.8836" />
              <version>1.0.0</version>
            </idea-plugin>
            """.trimIndent()
        )
    }

    @Test
    fun `do not break incremental processing`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(PROCESS_RESOURCES)

        build(PROCESS_RESOURCES) {
            assertTaskOutcome(PROCESS_RESOURCES, TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `update resources on updated patched xml files`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(PROCESS_RESOURCES)

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "Oh"
                        }
                    }
                }
                """.trimIndent()

        build(PROCESS_RESOURCES) {
            assertTaskOutcome(PROCESS_RESOURCES, TaskOutcome.SUCCESS)

            assertFileContent(
                outputPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="Oh" />
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent()
            )
        }
    }
}
