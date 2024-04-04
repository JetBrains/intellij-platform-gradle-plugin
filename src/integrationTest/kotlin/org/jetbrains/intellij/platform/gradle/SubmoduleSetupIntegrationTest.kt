// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class SubmoduleSetupIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "submodule-setup",
) {

    @Test
    fun `submodule shouldn't contain the runIde task`() {
        build(Tasks.RUN_IDE, projectProperties = defaultProjectProperties, args = listOf("--dry-run")) {
            assertContains(":runIde SKIPPED", output)
            assertNotContains(":submodule:runIde SKIPPED", output)
        }
    }
}
