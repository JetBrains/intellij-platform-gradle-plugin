// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class PluginXmlPatchingIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "plugin-xml-patching",
) {

    override val defaultProjectProperties
        get() = super.defaultProjectProperties + mapOf(
            "languageVersion" to "17",
            "sinceBuild" to "231",
        )

    @Test
    fun `patch plugin_xml`() {
        build(Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties + mapOf(
            "untilBuild" to "233.*"
        )) {
            assertTaskOutcome(Tasks.PATCH_PLUGIN_XML, TaskOutcome.SUCCESS)

            val patchedPluginXml = buildDirectory.resolve("tmp/patchPluginXml/plugin.xml")
            assertExists(patchedPluginXml)

            patchedPluginXml containsText //language=xml
                    """
                    <version>1.0.0</version>
                    """.trimIndent()

            patchedPluginXml containsText //language=xml
                    """
                    <idea-version since-build="231" until-build="233.*" />
                    """.trimIndent()
        }
    }

    @Test
    fun `patch plugin_xml and set the until-build to null if targeted 243+`() {
        build(Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties + mapOf(
            "intellijPlatform.version" to "2024.3",
            "languageVersion" to "21",
            "sinceBuild" to "243",
        )) {
            assertTaskOutcome(Tasks.PATCH_PLUGIN_XML, TaskOutcome.SUCCESS)

            val patchedPluginXml = buildDirectory.resolve("tmp/patchPluginXml/plugin.xml")
            assertExists(patchedPluginXml)

            patchedPluginXml containsText //language=xml
                    """
                    <idea-version since-build="243" />
                    """.trimIndent()
        }
    }
}
