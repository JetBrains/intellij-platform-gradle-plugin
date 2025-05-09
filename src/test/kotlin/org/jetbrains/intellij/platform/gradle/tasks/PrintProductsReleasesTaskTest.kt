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
                IC-2025.1.1
                IC-2024.3.5
                IC-2024.2.6
                IC-2024.1.7
                IC-2023.3.8
                IC-2023.2.8
                IC-2023.1.7
                IC-2022.3.3
                """.trimIndent(),
                output,
            )
        }
    }
}
