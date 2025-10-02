// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test

class PrintProductsReleasesTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `print product releases`() {
        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.PRINT_PRODUCTS_RELEASES} {
                        channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                    }
                }
                """.trimIndent()

        build(Tasks.PRINT_PRODUCTS_RELEASES) {
            assertContains(
                """
                > Task :${Tasks.PRINT_PRODUCTS_RELEASES}
                IC-2025.2.3
                IC-2025.1.6
                """.trimIndent(),
                output,
            )
        }
    }
}
