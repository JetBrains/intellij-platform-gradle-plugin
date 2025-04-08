// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

/**
 * This is something we must support since a few big corporations requested such a feature, because they use similar
 * configuration in very big projects. [Link](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html#configuration.dependencyResolutionManagement)
 */
class SettingsStrictResolutionManagementIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "settings-strict-resolution-management",
) {

    @Test
    fun `build plugin with strict settings resolution management mode`() {
        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties,
            args = listOf("--info")
        ) {
            buildDirectory containsFile "libs/test-1.0.0.jar"
        }
    }
}
