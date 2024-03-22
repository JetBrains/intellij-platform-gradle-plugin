// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import java.nio.file.Path
import kotlin.test.Test

class PluginXmlPatchingIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "plugin-xml-patching",
) {

    /**
     * Path to the patched `plugin.xml` file located within the build directory of the integration tests single project,
     * e.g., `/Users/hsz/Projects/JetBrains/gradle-intellij-plugin/integration-tests/plugin-xml-patching/build/patchedPluginXmlFiles/plugin.xml`.
     */
    val patchedPluginXml: Path
        get() = buildDirectory.resolve("patchedPluginXmlFiles/plugin.xml").also(::assertExists) // TODO: fix location

    @Test
    fun `patch plugin_xml`() {
        build(Tasks.BUILD_PLUGIN) {
            output containsText ":patchPluginXml"

            buildDirectory containsFile "patchedPluginXmlFiles/plugin.xml" // TODO: fix location

            patchedPluginXml containsText "<version>1.0.0</version>"
            patchedPluginXml containsText "<idea-version since-build=\"211\" until-build=\"213.*\" />"
        }
    }
}
