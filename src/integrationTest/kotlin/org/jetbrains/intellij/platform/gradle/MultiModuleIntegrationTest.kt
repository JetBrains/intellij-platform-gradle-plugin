// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Ignore
import kotlin.test.Test

@Ignore
class MultiModuleIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "multi-module",
) {

    @Test
    fun `ext plugin uses base plugin from zip distribution`() {
        build(":ext:" + Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties) {

        }
    }
}
