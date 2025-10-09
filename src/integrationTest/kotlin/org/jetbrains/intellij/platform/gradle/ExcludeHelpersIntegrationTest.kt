// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.Test

private const val DEPENDENCIES = "dependencies"

/**
 * Tests for excludeKotlinStdlib() and excludeCoroutines() helper functions.
 */
class ExcludeHelpersIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "exclude-helpers",
) {

    @Test
    fun `excludeCoroutines excludes kotlin coroutines transitive dependencies`() {
        build(DEPENDENCIES, "--configuration=implementation", projectProperties = defaultProjectProperties) {
            assertNotContains("org.jetbrains.kotlinx:kotlinx-coroutines-core", output)
        }
    }
}
