// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import kotlin.test.Test

class PrintProductsReleasesTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `print product releases`() {
        build(Tasks.PRINT_PRODUCTS_RELEASES) {
            assertContains(
                """
                > Task :${Tasks.PRINT_PRODUCTS_RELEASES}
                IC-223.8836.26
                """.trimIndent(),
                output,
            )
        }
    }
}
