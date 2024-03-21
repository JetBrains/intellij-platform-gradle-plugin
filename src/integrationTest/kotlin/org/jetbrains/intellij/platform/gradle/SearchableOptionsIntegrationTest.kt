// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.Test

class SearchableOptionsIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "searchable-options",
) {

    @Test
    fun `test manifest file`() {
        build(
            ":jarSearchableOptions",
            projectProperties = mapOf(
                "intellijPlatform.version" to intellijPlatformVersion,
                "intellijPlatform.type" to intellijPlatformType,
            )
        ) {
//            pluginJar containsFileInArchive "META-INF/MANIFEST.MF"
//            with(pluginJar readEntry "META-INF/MANIFEST.MF") {
//                this containsText "Version: 1.0.0"
//                this containsText "Build-Plugin: IntelliJ Platform Gradle Plugin"
//                this containsText "Build-Plugin-Version:"
//                this containsText "Build-OS:"
//                this containsText "Build-SDK: IC-2022.1.4"
//            }
        }
    }
}
