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

    private val defaultSandbox
        get() = sandboxDirectory.resolve("test").resolve("$intellijPlatformType-$intellijPlatformVersion")

    @Test
    fun `create sandbox in a default location`() {
        build(Tasks.RUN_IDE, projectProperties = defaultProjectProperties) {
            val sandbox = defaultSandbox

            assertExists(sandbox)
            assertExists(sandbox.resolve(Sandbox.CONFIG))
            assertExists(sandbox.resolve(Sandbox.LOG))
            assertExists(sandbox.resolve(Sandbox.PLUGINS))
            assertExists(sandbox.resolve(Sandbox.SYSTEM))
        }
    }

    @Test
    fun `create sandbox for a custom task in a default location`() {
        build(CUSTOM_RUN_IDE_TASK_NAME, projectProperties = defaultProjectProperties) {
            val sandbox = defaultSandbox
            val suffix = "_$CUSTOM_RUN_IDE_TASK_NAME"

            assertExists(sandbox)
            assertExists(sandbox.resolve(Sandbox.CONFIG + suffix))
            assertExists(sandbox.resolve(Sandbox.LOG + suffix))
            assertExists(sandbox.resolve(Sandbox.PLUGINS + suffix))
            assertExists(sandbox.resolve(Sandbox.SYSTEM + suffix))
        }
    }

    @Test
    fun `create test sandbox in a default location`() {
        build(Tasks.External.TEST, projectProperties = defaultProjectProperties) {
            val sandbox = defaultSandbox
            val suffix = "-test"

            assertExists(sandbox)
            assertExists(sandbox.resolve(Sandbox.CONFIG + suffix))
            assertExists(sandbox.resolve(Sandbox.LOG + suffix))
            assertExists(sandbox.resolve(Sandbox.PLUGINS + suffix))
            assertExists(sandbox.resolve(Sandbox.SYSTEM + suffix))
        }
    }

    @Test
    fun `create test sandbox for a custom task in a default location`() {
        build(CUSTOM_TEST_IDE_TASK_NAME, projectProperties = defaultProjectProperties) {
            val sandbox = defaultSandbox
            val suffix = "_$CUSTOM_TEST_IDE_TASK_NAME"

            assertExists(sandbox)
            assertExists(sandbox.resolve(Sandbox.CONFIG + suffix))
            assertExists(sandbox.resolve(Sandbox.LOG + suffix))
            assertExists(sandbox.resolve(Sandbox.PLUGINS + suffix))
            assertExists(sandbox.resolve(Sandbox.SYSTEM + suffix))
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

        build(Tasks.PREPARE_SANDBOX, projectProperties = defaultProjectProperties) {
            val sandbox = buildDirectory
                .resolve("custom-sandbox-container")
                .resolve("test")
                .resolve("$intellijPlatformType-$intellijPlatformVersion")

            assertExists(sandbox)
            assertExists(sandbox.resolve(Sandbox.CONFIG))
            assertExists(sandbox.resolve(Sandbox.LOG))
            assertExists(sandbox.resolve(Sandbox.PLUGINS))
            assertExists(sandbox.resolve(Sandbox.SYSTEM))
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
            val sandbox = sandboxDirectory.resolve("custom-directory")

            assertExists(sandbox)
            assertExists(sandbox.resolve(Sandbox.CONFIG))
            assertExists(sandbox.resolve(Sandbox.LOG))
            assertExists(sandbox.resolve(Sandbox.PLUGINS))
            assertExists(sandbox.resolve(Sandbox.SYSTEM))
        }
    }

    @Test
    fun `invalidate sandbox content when disabledPlugins property is changed`() {
        val sandbox = defaultSandbox
        val suffix = "_$CUSTOM_RUN_IDE_TASK_NAME"
        val disabledPluginsFile = sandbox.resolve(Sandbox.CONFIG + suffix).resolve("disabled_plugins.txt")

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
