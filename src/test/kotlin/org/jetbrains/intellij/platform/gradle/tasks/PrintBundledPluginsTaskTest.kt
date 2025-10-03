// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import kotlin.test.Test

class PrintBundledPluginsTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `print bundled plugins`() {
        build(Tasks.PRINT_BUNDLED_PLUGINS) {
            assertContains(
                """
                > Task :${Tasks.PRINT_BUNDLED_PLUGINS}
                Bundled plugins for IntelliJ IDEA 2025.1.6 (251.28774.11):
                ByteCodeViewer (Bytecode Viewer)
                Coverage (Code Coverage for Java)
                Git4Idea (Git)
                HtmlTools (HTML Tools)
                JUnit (JUnit)
                PerforceDirectPlugin (Perforce Helix Core)
                Subversion (Subversion)
                TestNG-J (TestNG)
                """.trimIndent(),
                output,
            )
        }
    }
}
