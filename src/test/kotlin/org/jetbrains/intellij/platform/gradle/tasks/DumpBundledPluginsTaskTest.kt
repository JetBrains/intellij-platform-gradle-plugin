// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DumpBundledPluginsTaskTest : IntelliJPluginTestBase() {
    override val enableIntelliJPlatformCache = true

    @Test
    fun `dump bundled plugins`() {
        build(Tasks.DUMP_BUNDLED_PLUGINS)

        val outputFile = dir.resolve("build/tmp/${Tasks.DUMP_BUNDLED_PLUGINS}/bundled-plugins.txt")
        assertTrue(outputFile.exists())

        val plugins = outputFile.readLines()
        assertContains(plugins, "Git4Idea\tGit")
        assertContains(plugins, "JUnit\tJUnit")
        assertEquals(plugins.distinct().sorted(), plugins)
    }

    @Test
    fun `reuses configuration cache`() {
        buildWithConfigurationCache(Tasks.DUMP_BUNDLED_PLUGINS)

        buildWithConfigurationCache(Tasks.DUMP_BUNDLED_PLUGINS) {
            assertConfigurationCacheReused()
        }
    }
}
