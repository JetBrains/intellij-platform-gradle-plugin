// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.exists
import kotlin.io.path.notExists
import kotlin.test.Ignore
import kotlin.test.Test

private const val CUSTOM_RUN_IDE_TASK_NAME = "customRunIde"

class MultiModuleIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "multi-module",
) {

    @Ignore
    @Test
    fun `ext plugin uses base plugin from zip distribution`() {
        build(":ext:" + Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties) {

        }
    }

    @Test
    fun `custom task should refer to the composed jar of base module`() {
        val ext = dir.resolve("ext")
        val sandboxDirectory = ext.resolve("build/idea-sandbox").resolve("$intellijPlatformType-$intellijPlatformVersion")
        val suffix = "_$CUSTOM_RUN_IDE_TASK_NAME"
        val pluginsDirectory = sandboxDirectory.resolve(Sandbox.PLUGINS + suffix)

        ext.resolve("build.gradle.kts") write //language=kotlin
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
            assert(pluginsDirectory.resolve("base/lib/base.jar").exists())
        }
    }
}
