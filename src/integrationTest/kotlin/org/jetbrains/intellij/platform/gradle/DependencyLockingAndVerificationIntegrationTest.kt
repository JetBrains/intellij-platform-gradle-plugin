// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.fileSize
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Tests dependency locking & verification in combination.
 * [DependencyLockingIntegrationTest] + [DependencyVerificationIgnoreIntellijIntegrationTest] but no artifacts are
 * ignored in this test.
 * [Support dependency verification](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1779)
 */
class DependencyLockingAndVerificationIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "dependency-locking-and-verification",
) {

    @Test
    fun `build plugin with dependency locks & hash verification`() {
        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties,
            args = listOf("--info", "--write-locks", "--write-verification-metadata", "md5,sha1,sha256,sha512")
        ) {
            assertLockFilesExist()
            assertMetadataSignaturesGenerated()
        }

        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties
        ) {
            buildDirectory containsFile "libs/test-1.0.0.jar"
            assertLockFilesExist()
            assertMetadataSignaturesGenerated()
        }
    }

    @Test
    fun `build plugin with dependency locks, hash & signature verification`() {
        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties,
            args = listOf("--info", "--write-locks", "--write-verification-metadata", "md5,sha1,sha256,sha512,pgp")
        ) {
            assertLockFilesExist()
            assertMetadataSignaturesGenerated()
            assertMetadataTrustedKeysGenerated()
        }

        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties
        ) {
            buildDirectory containsFile "libs/test-1.0.0.jar"
            assertLockFilesExist()
            assertMetadataSignaturesGenerated()
            assertMetadataTrustedKeysGenerated()
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

    private fun assertMetadataSignaturesGenerated() {
        buildDirectory.resolve("../gradle/verification-metadata.xml").let {
            assertExists(it)
            val xmlText = it.readText(Charsets.UTF_8)
            assertTrue(xmlText.contains("<components>"))
            assertTrue(xmlText.contains("<component"))
            assertTrue(xmlText.contains("<artifact"))
            assertTrue(xmlText.contains("<md5"))
            assertTrue(xmlText.contains("<sha1"))
            assertTrue(xmlText.contains("<sha256"))
            assertTrue(xmlText.contains("<sha512"))
        }
    }

    private fun assertMetadataTrustedKeysGenerated() {
        buildDirectory.resolve("../gradle/verification-metadata.xml").let {
            assertExists(it)
            val xmlText = it.readText(Charsets.UTF_8)
            assertTrue(xmlText.contains("<trusted-keys>"))
            assertTrue(xmlText.contains("<trusted-key"))
        }
    }
}
