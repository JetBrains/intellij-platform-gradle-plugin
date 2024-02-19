// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.Test

class ApplyRecommendedRepositoriesIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "apply-recommended-repositories",
) {

    @Test
    fun `attach bundled plugin sources`() {
        build("buildPlugin", projectProperties = mapOf(
            "intellijPlatform.version" to intellijPlatformVersion,
            "intellijPlatform.type" to intellijPlatformType,
        )) {
            val goPluginIvyFileName = "go-goland-GO-212.5457.54-withSources-3.xml"

            pluginsCacheDirectory containsFile goPluginIvyFileName

            val ivyFile = pluginsCacheDirectory.resolve(goPluginIvyFileName)
            ivyFile containsText """<artifact name="lib/src/go-openapi-src" type="jar" ext="jar" conf="sources" m:classifier="unzipped.com.jetbrains.plugins"/>"""
        }
    }
}
