// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.LAYOUT_INDEX
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertTrue

class PrintBundledPluginsTaskTest : IntelliJPluginTestBase() {
    override val enableIntelliJPlatformCache = true

    @Test
    fun `print bundled plugins`() {
        build(Tasks.PRINT_BUNDLED_PLUGINS) {
            assertContains(
                """
                > Task :${Tasks.PRINT_BUNDLED_PLUGINS}
                Bundled plugins for IntelliJ IDEA 2025.1.6 (251.28774.11):
                AngularJS (Angular)
                ByteCodeViewer (Bytecode Viewer)
                Coverage (Code Coverage for Java)
                Docker (Docker)
                Git4Idea (Git)
                HtmlTools (HTML Tools)
                JBoss (WildFly)
                JSIntentionPowerPack (JavaScript Intention Power Pack)
                JUnit (JUnit)
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `stores layout index in shared IntelliJ Platform cache`() {
        val layoutIndexDir = intellijPlatformCacheDir.resolve(LAYOUT_INDEX)

        build(Tasks.PRINT_BUNDLED_PLUGINS)

        assertTrue(layoutIndexDir.exists())
    }

    @Test
    fun `reuses configuration cache`() {
        buildWithConfigurationCache(Tasks.PRINT_BUNDLED_PLUGINS)

        buildWithConfigurationCache(Tasks.PRINT_BUNDLED_PLUGINS) {
            assertConfigurationCacheReused()
            assertContains("Bundled plugins for IntelliJ IDEA", output)
        }
    }
}
