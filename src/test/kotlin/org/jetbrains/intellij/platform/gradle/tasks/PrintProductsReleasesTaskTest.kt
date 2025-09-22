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
                IC-2025.2.2
                IC-2025.1.5
                IC-2024.3.7
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
