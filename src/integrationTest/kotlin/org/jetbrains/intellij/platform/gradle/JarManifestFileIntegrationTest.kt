// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class JarManifestFileIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "jar-manifest-file",
) {

    @Test
    fun `test manifest file`() {
        build(Tasks.External.ASSEMBLE, projectProperties = defaultProjectProperties) {
            val pluginJar = buildDirectory.resolve("libs/test-1.0.0-base.jar")
            assertExists(pluginJar)

            pluginJar containsFileInArchive "META-INF/MANIFEST.MF"
            with(pluginJar readEntry "META-INF/MANIFEST.MF") {
                this containsText "Created-By: Gradle $gradleVersion"
                this containsText "Build-Plugin: ${Plugin.NAME}"
                this containsText "Build-Plugin-Version:"
                this containsText "Platform-Type: $intellijPlatformType"
                this containsText "Platform-Version: $intellijPlatformVersion"
                this containsText "Kotlin-Stdlib-Bundled: false"
                this containsText "Kotlin-Version:"
            }
        }
    }
}
