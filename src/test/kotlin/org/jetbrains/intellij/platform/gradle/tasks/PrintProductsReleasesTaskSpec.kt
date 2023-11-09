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
                """
                > Task :${Tasks.PRINT_PRODUCTS_RELEASES}
                IC-2022.3.3
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `print product releases for a wider range`() {
        buildFile(
            tasks =
            """
            patchPluginXml {
                untilBuild.set("232.*")
            }
            """.trimIndent()
        )
        build(Tasks.PRINT_PRODUCTS_RELEASES) {
            assertContains(
                """
                > Task :${Tasks.PRINT_PRODUCTS_RELEASES}
                IC-2023.2.4
                IC-2023.1.5
                IC-2022.3.3
                """.trimIndent(),
                output,
            )
        }
    }
}
