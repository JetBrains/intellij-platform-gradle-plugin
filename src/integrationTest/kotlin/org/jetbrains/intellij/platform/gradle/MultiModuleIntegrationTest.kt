// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertTrue

private const val CUSTOM_RUN_IDE_TASK_NAME = "customRunIde"

class MultiModuleIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "multi-module",
    useCache = false,
) {

    @Ignore
    @Test
    fun `ext plugin uses base plugin from zip distribution`() {
        build(":ext:" + Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties) {

        }
    }

    @Test
    fun `module project dependency is packaged to lib modules automatically`() {
        val pluginDirectory = sandboxDirectory
            .resolve("ext")
            .resolve("$intellijPlatformType-$intellijPlatformVersion")
            .resolve(Sandbox.PLUGINS)
            .resolve("ext")

        build(":ext:" + Tasks.PREPARE_SANDBOX, projectProperties = defaultProjectProperties) {
            assertTrue(pluginDirectory.resolve("${Sandbox.Plugin.LIB_MODULES}/test.submodule.jar").exists())
            assertTrue(pluginDirectory.resolve("${Sandbox.Plugin.LIB}/test.submodule.jar").notExists())
            assertTrue(pluginDirectory.resolve("${Sandbox.Plugin.LIB}/dummy-0.1.2.jar").exists())
            assertTrue(pluginDirectory.resolve("${Sandbox.Plugin.LIB}/raw-1.0.0.jar").exists())
        }
    }

    @Test
    fun `custom task should refer to the composed jar of base module`() {
        val suffix = "_$CUSTOM_RUN_IDE_TASK_NAME"
        val pluginsDirectory = sandboxDirectory
            .resolve("ext")
            .resolve("$intellijPlatformType-$intellijPlatformVersion")
            .resolve(Sandbox.PLUGINS + suffix)

        dir.resolve("ext").resolve("build.gradle.kts") write //language=kotlin
                """
                intellijPlatformTesting.runIde {
                    register("$CUSTOM_RUN_IDE_TASK_NAME") {
                        task {
                            enabled = false
                        }
                    }
                }
                """.trimIndent()

        build(":ext:customRunIde", projectProperties = defaultProjectProperties) {
            assert(pluginsDirectory.resolve("base-base.jar").notExists())
            assert(pluginsDirectory.resolve("base/lib/base-1.0.0.jar").exists())
        }
    }

    @Test
    fun `module project packaging works with isolated projects enabled`() {
        gradleProperties += //language=properties
                """
                org.gradle.unsafe.isolated-projects=true
                """.trimIndent()

        val pluginDirectory = sandboxDirectory
            .resolve("ext")
            .resolve("$intellijPlatformType-$intellijPlatformVersion")
            .resolve(Sandbox.PLUGINS)
            .resolve("ext")

        build(":ext:" + Tasks.PREPARE_SANDBOX, projectProperties = defaultProjectProperties) {
            assertTrue(pluginDirectory.resolve("${Sandbox.Plugin.LIB_MODULES}/test.submodule.jar").exists())
            assertTrue(pluginDirectory.resolve("${Sandbox.Plugin.LIB}/test.submodule.jar").notExists())
        }
    }
}
