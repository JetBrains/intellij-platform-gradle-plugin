// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DumpBundledModulesTaskTest : IntelliJPluginTestBase() {
    override val enableIntelliJPlatformCache = true

    @Test
    fun `dump bundled modules`() {
        build(Tasks.DUMP_BUNDLED_MODULES)

        val outputFile = dir.resolve("build/tmp/${Tasks.DUMP_BUNDLED_MODULES}/bundled-modules.txt")
        assertTrue(outputFile.exists())

        val modules = outputFile.readLines()
        assertTrue(modules.any { it.startsWith("intellij.platform.coverage\t") || it == "intellij.platform.coverage" })
        assertTrue(modules.any { it.startsWith("intellij.platform.vcs.impl\t") || it == "intellij.platform.vcs.impl" })
        assertEquals(modules.distinct().sorted(), modules)
    }

    @Test
    fun `reuses configuration cache`() {
        buildWithConfigurationCache(Tasks.DUMP_BUNDLED_MODULES)

        buildWithConfigurationCache(Tasks.DUMP_BUNDLED_MODULES) {
            assertConfigurationCacheReused()
        }
    }
}
