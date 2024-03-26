// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class PluginXmlPatchingIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "plugin-xml-patching",
) {

    @Test
    fun `patch plugin_xml`() {
        build(Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties) {
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
}
