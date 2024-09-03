// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

private const val CUSTOM_RUN_IDE_TASK_NAME = "customRunIde"
private const val CUSTOM_TEST_IDE_TASK_NAME = "customTestIde"

class SandboxIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "sandbox",
) {

    @Test
    fun `create sandbox in a default location`() {
        build(Tasks.RUN_IDE, projectProperties = defaultProjectProperties) {
            val sandboxDirectory = buildDirectory.resolve("idea-sandbox").resolve("$intellijPlatformType-$intellijPlatformVersion")

            assertExists(sandboxDirectory)
            assertExists(sandboxDirectory.resolve(Sandbox.CONFIG))
            assertExists(sandboxDirectory.resolve(Sandbox.LOG))
            assertExists(sandboxDirectory.resolve(Sandbox.PLUGINS))
            assertExists(sandboxDirectory.resolve(Sandbox.SYSTEM))
        }
    }

    @Test
    fun `create sandbox for a custom task in a default location`() {
        build(CUSTOM_RUN_IDE_TASK_NAME, projectProperties = defaultProjectProperties) {
            val sandboxDirectory = buildDirectory.resolve("idea-sandbox").resolve("$intellijPlatformType-$intellijPlatformVersion")
            val suffix = "_$CUSTOM_RUN_IDE_TASK_NAME"

            assertExists(sandboxDirectory)
            assertExists(sandboxDirectory.resolve(Sandbox.CONFIG + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.LOG + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.PLUGINS + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.SYSTEM + suffix))
        }
    }

    @Test
    fun `create test sandbox in a default location`() {
        build(Tasks.External.TEST, projectProperties = defaultProjectProperties) {
            val sandboxDirectory = buildDirectory.resolve("idea-sandbox").resolve("$intellijPlatformType-$intellijPlatformVersion")
            val suffix = "-test"

            assertExists(sandboxDirectory)
            assertExists(sandboxDirectory.resolve(Sandbox.CONFIG + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.LOG + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.PLUGINS + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.SYSTEM + suffix))
        }
    }

    @Test
    fun `create test sandbox for a custom task in a default location`() {
        build(CUSTOM_TEST_IDE_TASK_NAME, projectProperties = defaultProjectProperties) {
            val sandboxDirectory = buildDirectory.resolve("idea-sandbox").resolve("$intellijPlatformType-$intellijPlatformVersion")
            val suffix = "_$CUSTOM_TEST_IDE_TASK_NAME"

            assertExists(sandboxDirectory)
            assertExists(sandboxDirectory.resolve(Sandbox.CONFIG + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.LOG + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.PLUGINS + suffix))
            assertExists(sandboxDirectory.resolve(Sandbox.SYSTEM + suffix))
        }
    }

    @Test
    fun `create sandbox in a custom container location`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    sandboxContainer = layout.buildDirectory.dir("custom-sandbox-container")
                }
                """.trimIndent()

        build(Tasks.RUN_IDE, projectProperties = defaultProjectProperties) {
            val sandboxDirectory = buildDirectory.resolve("custom-sandbox-container").resolve("$intellijPlatformType-$intellijPlatformVersion")

            assertExists(sandboxDirectory)
            assertExists(sandboxDirectory.resolve(Sandbox.CONFIG))
            assertExists(sandboxDirectory.resolve(Sandbox.LOG))
            assertExists(sandboxDirectory.resolve(Sandbox.PLUGINS))
            assertExists(sandboxDirectory.resolve(Sandbox.SYSTEM))
        }
    }

    @Test
    fun `create sandbox in a custom directory within the common sandbox container`() {
        buildFile write //language=kotlin
                """
                tasks {
                    prepareSandbox {
                        sandboxDirectory = intellijPlatform.sandboxContainer.map { it.dir("custom-directory") }
                    }
                }
                """.trimIndent()

        build(Tasks.RUN_IDE, projectProperties = defaultProjectProperties) {
            val sandboxDirectory = buildDirectory.resolve("idea-sandbox").resolve("custom-directory")

            assertExists(sandboxDirectory)
            assertExists(sandboxDirectory.resolve(Sandbox.CONFIG))
            assertExists(sandboxDirectory.resolve(Sandbox.LOG))
            assertExists(sandboxDirectory.resolve(Sandbox.PLUGINS))
            assertExists(sandboxDirectory.resolve(Sandbox.SYSTEM))
        }
    }

    @Test
    fun `invalidate sandbox content when disabledPlugins property is changed`() {
        val sandboxDirectory = buildDirectory.resolve("idea-sandbox").resolve("$intellijPlatformType-$intellijPlatformVersion")
        val suffix = "_$CUSTOM_RUN_IDE_TASK_NAME"
        val disabledPluginsFile = sandboxDirectory.resolve(Sandbox.CONFIG + suffix).resolve("disabled_plugins.txt")

        build(CUSTOM_RUN_IDE_TASK_NAME, projectProperties = defaultProjectProperties) {
            assertExists(disabledPluginsFile)
            assertEquals("", disabledPluginsFile.readText().trim())
        }

        buildFile write //language=kotlin
                """
                tasks.named<org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask>("prepareSandbox_$CUSTOM_RUN_IDE_TASK_NAME") {
                    disabledPlugins.add("Git4Idea")
                }
                """.trimIndent()


        build(CUSTOM_RUN_IDE_TASK_NAME, projectProperties = defaultProjectProperties) {
            assertExists(disabledPluginsFile)
            assertEquals("Git4Idea", disabledPluginsFile.readText().trim())
        }
    }
}
