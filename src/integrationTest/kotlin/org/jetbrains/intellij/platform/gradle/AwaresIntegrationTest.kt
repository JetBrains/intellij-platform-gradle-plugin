// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class AwaresIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "awares",
) {

    @Test
    @Ignore("Awares imports don't work")
    fun `create custom tasks with Aware interfaces`() {
        disableDebug()
        build("retrievePluginName", projectProperties = defaultProjectProperties) {
            assertLogValue("Plugin Name: ") {
                assertEquals("Custom name", it)
            }
        }
    }
}
