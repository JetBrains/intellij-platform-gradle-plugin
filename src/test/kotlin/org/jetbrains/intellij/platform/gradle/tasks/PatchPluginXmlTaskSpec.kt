// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertEquals

class PatchPluginXmlTaskSpec : IntelliJPluginSpecBase() {

    private val patchedPluginXml = lazy { buildDirectory.resolve("tmp/${Tasks.PATCH_PLUGIN_XML}/plugin.xml") }

    @Test
    fun `patch version and since until builds`() {
        pluginXml.xml("<idea-plugin />")

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <version>1.0.0</version>
                  <name>projectName</name>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `patch description`() {
        pluginXml.xml("<idea-plugin />")

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginConfiguration {
                    description = "Plugin pluginDescription"
                }
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <description>Plugin pluginDescription</description>
                  <version>1.0.0</version>
                  <name>projectName</name>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `patch patching preserves UTF-8 characters`() {
        pluginXml.xml("<idea-plugin someattr=\"\\u2202\" />")

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin someattr="\u2202">
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <version>1.0.0</version>
                  <name>projectName</name>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `patch change notes`() {
        pluginXml.xml("<idea-plugin />")

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginConfiguration {
                    changeNotes = "change notes"
                }
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <change-notes>change notes</change-notes>
                  <version>1.0.0</version>
                  <name>projectName</name>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `patch id`() {
        pluginXml.xml("<idea-plugin />")

        buildFile.kotlin(
            """
            intellijPlatform {
                pluginConfiguration {
                    id = "my.plugin.id"
                }
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <version>1.0.0</version>
                  <name>projectName</name>
                  <id>my.plugin.id</id>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `do not update id if pluginId is undefined`() {
        pluginXml.xml(
            """
            <idea-plugin>
              <id>my.plugin.id</id>
              <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <version>1.0.0</version>
                  <name>projectName</name>
                  <id>my.plugin.id</id>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `add version tags in the beginning of file`() {
        pluginXml.xml(
            """
            <idea-plugin>
              <name>projectName</name>
              <id>org.jetbrains.erlang</id>
              <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <version>1.0.0</version>
                  <name>projectName</name>
                  <id>org.jetbrains.erlang</id>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `override version and since until builds`() {
        pluginXml.xml(
            """
            <idea-plugin>
              <version>my_version</version>
              <idea-version since-build='1' until-build='2'>my_version</idea-version>
              <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <name>projectName</name>
                  <version>1.0.0</version>
                  <idea-version since-build="223.8836" until-build="223.*">my_version</idea-version>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent(),
            )

            assertContains("Patching plugin.xml: attribute 'until-build=[2]' of 'idea-version' tag will be set to '223.*'", output)
            assertContains("Patching plugin.xml: attribute 'since-build=[1]' of 'idea-version' tag will be set to '223.8836'", output)
            assertContains("Patching plugin.xml: value of 'version[my_version]' tag will be set to '1.0.0'", output)
        }
    }

    @Test
    fun `do not update version tag if project_version is undefined`() {
        pluginXml.xml(
            """
            <idea-plugin>
              <version>0.10.0</version>
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.kotlin(
            """
            version = Project.DEFAULT_VERSION
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <name>projectName</name>
                  <version>0.10.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `skip patch task if intellij version did not changed`() {
        pluginXml.xml("<idea-plugin />")

        build(Tasks.PATCH_PLUGIN_XML)
        build(Tasks.PATCH_PLUGIN_XML) {
            assertEquals(TaskOutcome.UP_TO_DATE, task(":${Tasks.PATCH_PLUGIN_XML}")?.outcome)
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <version>1.0.0</version>
                  <name>projectName</name>
                </idea-plugin>
                """.trimIndent(),
            )
        }
    }
}
