// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.resolvers.latestVersion.IntelliJPlatformGradlePluginLatestVersionResolver
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertFalse

class InitializeIntelliJPlatformPluginTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `report outdated plugin`() {
        gradleProperties.properties(
            """
            org.jetbrains.intellij.platform.buildFeature.selfUpdateCheck = true
            """.trimIndent()
        )

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            val latestVersion = IntelliJPlatformGradlePluginLatestVersionResolver().resolve()

            assertContains("${Plugin.NAME} is outdated: 0.0.0. Update `${Plugin.ID}` to: $latestVersion", output)
        }
    }

    @Test
    fun `skip version check when offline`() {
        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN, "--offline") {
            assertNotContains("${Plugin.NAME} is outdated: 0.0.0.", output)
        }
    }

    @Test
    fun `skip version check is disabled with BuildFeature`() {
        gradleProperties.properties(
            """
            org.jetbrains.intellij.platform.buildFeature.selfUpdateCheck=false
            """.trimIndent()
        )

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertNotContains("${Plugin.NAME} is outdated: 0.0.0.", output)
        }
    }

    @Test
    fun `skip version check is disabled with existing lock file`() {
        val file = dir.resolve("lockFile").createFile()

        buildFile.kotlin(
            """
            tasks {
                ${Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN} {
                    selfUpdateLock = file("${file.name}")
                }
            }
            """.trimIndent()
        )

        assertExists(file)

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertNotContains("${Plugin.NAME} is outdated: 0.0.0.", output)
        }
    }

    @Test
    fun `creates coroutines-javaagent file`() {
        val file = dir.resolve("coroutines-javaagent.jar")

        buildFile.kotlin(
            """
            tasks {
                ${Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN} {
                    coroutinesJavaAgent = file("${file.name}")
                }
            }
            """.trimIndent()
        )

        assertFalse(file.exists())

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

        assertExists(file)
    }
}
