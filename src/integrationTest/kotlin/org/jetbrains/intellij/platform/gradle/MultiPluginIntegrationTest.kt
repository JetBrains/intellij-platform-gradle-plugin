// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.Test

class MultiPluginIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "multi-plugin",
) {

    @Test
    fun `Parallel dependency resolution should work`() {
        build(
            "plugin1:dependencies", "plugin2:dependencies",
            projectProperties = defaultProjectProperties,
            args = listOf(
                "--parallel", "--no-configuration-cache"
            )
        )
    }
}
