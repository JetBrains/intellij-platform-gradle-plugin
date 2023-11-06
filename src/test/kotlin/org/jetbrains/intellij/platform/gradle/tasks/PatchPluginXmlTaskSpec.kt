// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_XML_DIR_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

@Suppress("PluginXmlValidity")
class PatchPluginXmlTaskSpec : IntelliJPluginSpecBase() {

    private val patchedPluginXml = lazy { File(buildDirectory, PLUGIN_XML_DIR_NAME).listFiles()?.first() }

    @Test
    fun `patch version and since until builds`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.*" />
                </idea-plugin>
                """.trimIndent()
            )
            assertNotContains("will be overwritten", it.output)
        }
    }

    @Test
    fun `patch description`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            patchPluginXml {
                pluginDescription = 'Plugin pluginDescription'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <description>Plugin pluginDescription</description>
                  <idea-version since-build="141.1532" until-build="141.*" />
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
        }
    }

    @Test
    fun `patch patching preserves UTF-8 characters`() {
        pluginXml.xml(
            """
            <idea-plugin someattr="\u2202" /> 
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin someattr="\u2202">
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.*" />
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
        }
    }

    @Test
    fun `patch change notes`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            patchPluginXml {
                changeNotes = 'change notes'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <change-notes>change notes</change-notes>
                  <idea-version since-build="141.1532" until-build="141.*" />
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
        }
    }

    @Test
    fun `patch id`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            patchPluginXml {
                pluginId = 'my.plugin.id'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <id>my.plugin.id</id>
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.*" />
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
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

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.*" />
                  <id>my.plugin.id</id>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
        }
    }

    @Test
    fun `same since and until builds`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
                sameSinceUntilBuild = true
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.1532.*" />
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
        }
    }

    @Test
    fun `add version tags in the beginning of file`() {
        pluginXml.xml(
            """
            <idea-plugin>
              <id>org.jetbrains.erlang</id>
              <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.*" />
                  <id>org.jetbrains.erlang</id>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
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

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.*">my_version</idea-version>
                  <vendor>JetBrains</vendor>
                </idea-plugin>
                """.trimIndent()
            )

            assertContains("attribute 'since-build=[1]' of 'idea-version' tag will be set to '141.1532'", it.output)
            assertContains("attribute 'until-build=[2]' of 'idea-version' tag will be set to '141.*'", it.output)
            assertContains("value of 'version[my_version]' tag will be set to '0.42.123'", it.output)
        }
    }

    @Test
    fun `take extension setting into account while patching`() {
        pluginXml.xml(
            """
            <idea-plugin>
              <version>my_version</version>
              <idea-version since-build='1' until-build='2'>my_version</idea-version>
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
                updateSinceUntilBuild = false 
            }
        """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="1" until-build="2">my_version</idea-version>
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
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

        buildFile.groovy(
            """
            intellij {
                version = '14.1.4'
            }
        """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <idea-version since-build="141.1532" until-build="141.*" />
                  <version>0.10.0</version>
                </idea-plugin>
                """.trimIndent()
            )

            assertNotContains("will be overwritten", it.output)
        }
    }

    @Test
    fun `skip patch task if intellij version did not changed`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML)
        build(Tasks.PATCH_PLUGIN_XML).let {
            assertEquals(TaskOutcome.UP_TO_DATE, it.task(":${Tasks.PATCH_PLUGIN_XML}")?.outcome)
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.*" />
                </idea-plugin>
                """.trimIndent()
            )
        }
    }

    @Test
    fun `patch version and since until builds on intellij version changing`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                version = '$intellijVersion'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML)

        buildFile.groovy(
            """
            intellij {
                version = '14.1.4'
            }
            """.trimIndent()
        )

        build(Tasks.PATCH_PLUGIN_XML).let {
            assertNotEquals(TaskOutcome.UP_TO_DATE, it.task(":${Tasks.PATCH_PLUGIN_XML}")?.outcome)
            assertFileContent(
                patchedPluginXml.value,
                """
                <idea-plugin>
                  <version>0.42.123</version>
                  <idea-version since-build="141.1532" until-build="141.*" />
                </idea-plugin>
                """.trimIndent()
            )
        }
    }
}
