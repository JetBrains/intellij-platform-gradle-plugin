// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.fileSize
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * [Support for dependency locking, do not use absolute paths](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1778)
 */
class DependencyLockingIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "dependency-locking",
) {

    @Test
    fun `build plugin with dependency locks`() {
        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties,
            args = listOf("--info", "--write-locks")
        ) {
            assertLockFilesExist()
        }

        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties,
        ) {
            buildDirectory containsFile "libs/test-1.0.0.jar"
            assertLockFilesExist()
        }
    }

    private fun assertLockFilesExist() {
        buildDirectory.resolve("../gradle/locks/root/gradle.lockfile").let {
            assertExists(it)
            assertTrue(0 < it.fileSize())
        }
        buildDirectory.resolve("../gradle/locks/root/gradle-buildscript.lockfile").let {
            assertExists(it)
            assertTrue(0 < it.fileSize())
        }
        buildDirectory.resolve("../gradle/locks/root/settings-gradle-buildscript.lockfile").let {
            assertExists(it)
            assertTrue(0 < it.fileSize())
        }
    }
}
