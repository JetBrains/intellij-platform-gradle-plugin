// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.Test

class PluginXmlPatchingIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "plugin-xml-patching",
) {

    @Test
    fun `patch plugin_xml`() {
        build("build") {
            output containsText ":patchPluginXml"

            buildDirectory containsFile "patchedPluginXmlFiles/plugin.xml" // TODO: fix location

            patchedPluginXml containsText "<version>1.0.0</version>"
            patchedPluginXml containsText "<idea-version since-build=\"211\" until-build=\"213.*\" />"
        }
    }
}
