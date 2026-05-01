// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.buildDirectory
import org.jetbrains.intellij.platform.gradle.sandboxDirectory
import org.jetbrains.intellij.platform.gradle.write
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.test.Test
import kotlin.test.assertTrue

class CleanSandboxTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `clean only sandbox directory from prepareSandbox`() {
        val currentSandbox = sandboxDirectory.resolve("projectName").resolve("$intellijPlatformType-$intellijPlatformVersion").createDirectories()
        val anotherProjectSandbox = sandboxDirectory.resolve("other-project").resolve("$intellijPlatformType-$intellijPlatformVersion").createDirectories()
        val anotherVersionSandbox = sandboxDirectory.resolve("projectName").resolve("$intellijPlatformType-other-version").createDirectories()

        assertTrue(currentSandbox.exists())
        assertTrue(anotherProjectSandbox.exists())
        assertTrue(anotherVersionSandbox.exists())

        build(Tasks.CLEAN_SANDBOX)

        assertTrue(currentSandbox.notExists())
        assertTrue(anotherProjectSandbox.exists())
        assertTrue(anotherVersionSandbox.exists())
    }

    @Test
    fun `clean sandbox from custom prepareSandbox directory`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    sandboxContainer = layout.buildDirectory.dir("custom-sandbox-container")
                }
                
                tasks {
                    prepareSandbox {
                        sandboxDirectory = intellijPlatform.sandboxContainer.map { it.dir("custom-directory") }
                    }
                }
                """.trimIndent()

        val customSandboxContainer = buildDirectory.resolve("custom-sandbox-container")
        val customSandboxDirectory = customSandboxContainer.resolve("custom-directory").createDirectories()
        val defaultProjectSandbox = customSandboxContainer.resolve("projectName").resolve("$intellijPlatformType-$intellijPlatformVersion").createDirectories()
        val otherProjectSandbox = customSandboxContainer.resolve("other-project").resolve("$intellijPlatformType-$intellijPlatformVersion").createDirectories()

        build(Tasks.CLEAN_SANDBOX)

        assertTrue(customSandboxDirectory.notExists())
        assertTrue(defaultProjectSandbox.exists())
        assertTrue(otherProjectSandbox.exists())
    }

    @Test
    fun `reuses configuration cache`() {
        val currentSandbox = sandboxDirectory.resolve("projectName").resolve("$intellijPlatformType-$intellijPlatformVersion").createDirectories()

        buildWithConfigurationCache(Tasks.CLEAN_SANDBOX)

        buildWithConfigurationCache(Tasks.CLEAN_SANDBOX) {
            assertConfigurationCacheReused()
        }

        assertTrue(currentSandbox.notExists())
    }
}
