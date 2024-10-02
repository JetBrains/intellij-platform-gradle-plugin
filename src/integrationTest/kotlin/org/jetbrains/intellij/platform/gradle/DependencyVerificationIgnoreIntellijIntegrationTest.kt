// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * For this test `src/integrationTest/resources/dependency-verification-ignore-intellij/gradle/verification-metadata.xml`
 * adds "bundledModule" & "bundledPlugin" to trusted artifacts, so that we can test dependency locking & verification
 * in isolation from Intellij Artifacts.
 *
 * [Support for dependency locking, do not use absolute paths](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1778)
 * [Support dependency verification](https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1779)
 * @see DependencyLockingIntegrationTest
 * @see DependencyLockingAndVerificationIntegrationTest
 */
class DependencyVerificationIgnoreIntellijIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "dependency-verification-ignore-intellij",
) {

    @Test
    fun `build plugin with dependency locks & hash verification ignoring intellij artifacts`() {
        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties,
            args = listOf("--info", "--write-verification-metadata", "md5,sha1,sha256,sha512")
        ) {
            assertMetadataSignaturesGenerated()
        }

        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties
        ) {
            buildDirectory containsFile "libs/test-1.0.0.jar"
            assertMetadataSignaturesGenerated()
        }
    }

    @Test
    fun `build plugin with dependency locks, hash & signature verification ignoring intellij artifacts`() {
        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties,
            args = listOf("--info", "--write-verification-metadata", "md5,sha1,sha256,sha512,pgp")
        ) {
            assertMetadataSignaturesGenerated()
            assertMetadataTrustedKeysGenerated()
        }

        build(
            Tasks.External.CLEAN,
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties
        ) {
            buildDirectory containsFile "libs/test-1.0.0.jar"
            assertMetadataSignaturesGenerated()
            assertMetadataTrustedKeysGenerated()
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
