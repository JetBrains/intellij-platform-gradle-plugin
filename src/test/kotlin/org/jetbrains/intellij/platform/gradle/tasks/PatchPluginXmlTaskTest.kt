// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class PatchPluginXmlTaskTest : IntelliJPluginTestBase() {

    private val patchedPluginXml
        get() = buildDirectory.resolve("tmp/${Tasks.PATCH_PLUGIN_XML}/plugin.xml")

    @Test
    fun `patch version and since until builds`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `patch description`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        description = "<p>Plugin pluginDescription</p>"
                    }
                }
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <description><![CDATA[<p>Plugin pluginDescription</p>]]></description>
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `patch patching preserves UTF-8 characters`() {
        pluginXml write //language=xml
                """
                <idea-plugin someattr="\u2202"/>
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin someattr="\u2202">
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `patch change notes`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        changeNotes = "change notes"
                    }
                }
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <change-notes><![CDATA[change notes]]></change-notes>
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `patch id`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        id = "my.plugin.id"
                    }
                }
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
                  <id>my.plugin.id</id>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `do not update id if pluginId is undefined`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                  <id>my.plugin.id</id>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
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
        pluginXml write //language=xml
                """
                <idea-plugin>
                  <id>org.jetbrains.erlang</id>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
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
        pluginXml write //language=xml
                """
                <idea-plugin>
                  <version>my_version</version>
                  <idea-version since-build='1' until-build='2'>my_version</idea-version>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <version>1.0.0</version>
                  <idea-version since-build="223.8836">my_version</idea-version>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent(),
            )

            assertContains("Patching plugin.xml: attribute 'since-build=[1]' of 'idea-version' tag will be set to '223.8836'", output)
            assertContains("Patching plugin.xml: value of 'version[my_version]' tag will be set to '1.0.0'", output)
        }
    }

    @Test
    fun `do not update version tag if project_version is undefined`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                  <version>0.10.0</version>
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                version = Project.DEFAULT_VERSION
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>0.10.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `skip patch task if intellij version did not changed`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML)
        build(Tasks.PATCH_PLUGIN_XML) {
            assertTaskOutcome(Tasks.PATCH_PLUGIN_XML, TaskOutcome.UP_TO_DATE)
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `unset the until-build attribute with null-provider passed to extension`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            untilBuild.set(provider { null })
                        }
                    }
                }
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `unset the until-build attribute with null-provider passed to task`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks {
                    patchPluginXml {
                        untilBuild.set(provider { null })
                    }
                }
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `ignore unsetting the until-build with null passed to extension`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            untilBuild = null
                        }
                    }
                }
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
                </idea-plugin>
                """.trimIndent(),
            )

            assertNotContains("will be overwritten", output)
        }
    }

    @Test
    fun `override the description specified in the XML file`() {
        pluginXml write //language=xml
                """
                <idea-plugin>
                  <description>Foo</description>
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        description = "Bar"
                    }
                }
                """.trimIndent()

        build(Tasks.PATCH_PLUGIN_XML) {
            assertFileContent(
                patchedPluginXml,
                """
                <idea-plugin>
                  <idea-version since-build="223.8836" />
                  <version>1.0.0</version>
                  <description><![CDATA[Bar]]></description>
                </idea-plugin>
                """.trimIndent(),
            )
        }
    }
}
