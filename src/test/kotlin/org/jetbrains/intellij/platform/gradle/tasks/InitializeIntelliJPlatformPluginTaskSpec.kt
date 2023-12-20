// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import org.jetbrains.intellij.platform.gradle.utils.LatestVersionResolver
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InitializeIntelliJPlatformPluginTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `report outdated plugin`() {
        gradleProperties.properties(
            """
            org.jetbrains.intellij.platform.buildFeature.selfUpdateCheck = true
            """.trimIndent()
        )

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            val latestVersion = LatestVersionResolver.plugin()

            assertContains("$PLUGIN_NAME is outdated: 0.0.0. Update `$PLUGIN_ID` to: $latestVersion", output)
        }
    }

    @Test
    fun `skip version check when offline`() {
        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN, "--offline") {
            assertNotContains("$PLUGIN_NAME is outdated: 0.0.0.", output)
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
            assertNotContains("$PLUGIN_NAME is outdated: 0.0.0.", output)
        }
    }

    @Test
    fun `skip version check is disabled with existing lock file`() {
        val file = dir.resolve("lockFile").createFile()

        buildFile.groovy(
            """
            tasks {
                ${Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN} {
                    selfUpdateLock = file("${file.name}")
                }
            }
            """.trimIndent()
        )

        assertTrue(file.exists())

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertNotContains("$PLUGIN_NAME is outdated: 0.0.0.", output)
        }
    }

    @Test
    fun `creates coroutines-javaagent file`() {
        val file = dir.resolve("coroutines-javaagent.jar")

        buildFile.groovy(
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

        assertTrue(file.exists())
    }
}
