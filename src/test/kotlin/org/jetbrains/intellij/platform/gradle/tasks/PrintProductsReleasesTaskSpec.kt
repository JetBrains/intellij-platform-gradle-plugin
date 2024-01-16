// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Test

class PrintProductsReleasesTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `print product releases`() {
        build(Tasks.PRINT_PRODUCTS_RELEASES) {
            assertContains(
                listOf(
                    "> Task :${Tasks.PRINT_PRODUCTS_RELEASES}",
                    "IC-223.8836.26",
                ).joinToString(System.lineSeparator()),
                output,
            )
        }
    }
}
