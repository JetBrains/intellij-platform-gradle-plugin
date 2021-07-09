package org.jetbrains.intellij.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PatchPluginXmlTaskSpec : IntelliJPluginSpecBase() {

    private val patchedPluginXml = lazy { File(buildDirectory, IntelliJPluginConstants.PLUGIN_XML_DIR_NAME).listFiles()?.first() }

    @Test
    fun `patch version and since until builds`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
        """)

        val result = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME)

        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.*" />
            </idea-plugin>
        """)

        assertFalse(result.output.contains("will be overwritten"))
    }

    @Test
    fun `patch description`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            patchPluginXml {
                pluginDescription = 'Plugin pluginDescription'
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output

        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <description>Plugin pluginDescription</description>
              <idea-version since-build="141.1532" until-build="141.*" />
            </idea-plugin>
        """)

        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `patch patching preserves UTF-8 characters`() {
        pluginXml.xml("""
           <idea-plugin someattr="\u2202" /> 
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output

        assertFileContent(patchedPluginXml.value, """
            <idea-plugin someattr="\u2202">
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.*" />
            </idea-plugin>
        """)

        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `patch change notes`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            patchPluginXml {
                changeNotes = 'change notes'
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output

        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <change-notes>change notes</change-notes>
              <idea-version since-build="141.1532" until-build="141.*" />
            </idea-plugin>
        """)

        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `patch id`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
            patchPluginXml {
                pluginId = 'my.plugin.id'
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output

        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <id>my.plugin.id</id>
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.*" />
            </idea-plugin>
        """)

        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `do not update id if pluginId is undefined`() {
        pluginXml.xml("""
            <idea-plugin>
              <id>my.plugin.id</id>
              <vendor>JetBrains</vendor>
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output

        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.*" />
              <id>my.plugin.id</id>
              <vendor>JetBrains</vendor>
            </idea-plugin>
        """)

        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `same since and until builds`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
                sameSinceUntilBuild = true
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output
        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.1532.*" />
            </idea-plugin>
        """)

        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `add version tags in the beginning of file`() {
        pluginXml.xml("""
            <idea-plugin>
              <id>org.jetbrains.erlang</id>
              <vendor>JetBrains</vendor>
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output
        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.*" />
              <id>org.jetbrains.erlang</id>
              <vendor>JetBrains</vendor>
            </idea-plugin>
        """)
        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `override version and since until builds`() {
        pluginXml.xml("""
            <idea-plugin>
              <version>my_version</version>
              <idea-version since-build='1' until-build='2'>my_version</idea-version>
              <vendor>JetBrains</vendor>
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output
        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.*">my_version</idea-version>
              <vendor>JetBrains</vendor>
            </idea-plugin>
        """)

        assertTrue(output.contains("attribute 'since-build=[1]' of 'idea-version' tag will be set to '141.1532'"))
        assertTrue(output.contains("attribute 'until-build=[2]' of 'idea-version' tag will be set to '141.*'"))
        assertTrue(output.contains("value of 'version[my_version]' tag will be set to '0.42.123'"))
    }

    @Test
    fun `take extension setting into account while patching`() {
        pluginXml.xml("""
            <idea-plugin>
              <version>my_version</version>
              <idea-version since-build='1' until-build='2'>my_version</idea-version>
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
                updateSinceUntilBuild = false 
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output

        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="1" until-build="2">my_version</idea-version>
            </idea-plugin>
        """)
        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `do not update version tag if project_version is undefined`() {
        pluginXml.xml("""
            <idea-plugin>
              <version>0.10.0</version>
            </idea-plugin>
        """)

        buildFile.groovy("""
            intellij {
                version = '14.1.4'
            }
        """)

        val output = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME).output

        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <idea-version since-build="141.1532" until-build="141.*" />
              <version>0.10.0</version>
            </idea-plugin>
        """)
        assertFalse(output.contains("will be overwritten"))
    }

    @Test
    fun `skip patch task if intellij version did not changed`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '14.1.4'
            }
        """)

        build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME)
        val result = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME)

        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":${IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME}")?.outcome)
        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.*" />
            </idea-plugin>
        """)
    }

    @Test
    fun `patch version and since until builds on intellij version changing`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '2019.1'
            }
        """)

        build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME)

        buildFile.groovy("""
            intellij {
                version = '14.1.4'
            }
        """)

        val result = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME)

        assertNotEquals(TaskOutcome.UP_TO_DATE, result.task(":${IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME}")?.outcome)
        assertFileContent(patchedPluginXml.value, """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="141.1532" until-build="141.*" />
            </idea-plugin>
        """)
    }


    @Test
    fun `reuse configuration cache`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                version = '2019.1'
            }
        """)

        build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
