// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.jetbrains.intellij.dependency.PluginDependencyNotation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class UtilsTest {

    @Test
    fun `dependency parsing`() {
        assertEquals(PluginDependencyNotation("hello", "1.23", "alpha"), PluginDependencyNotation.parsePluginDependencyString("hello:1.23@alpha"))
        assertEquals(PluginDependencyNotation("hello", null, "alpha"), PluginDependencyNotation.parsePluginDependencyString("hello:@alpha"))
        assertEquals(PluginDependencyNotation("hello", null, "alpha"), PluginDependencyNotation.parsePluginDependencyString("hello@alpha"))
        assertEquals(PluginDependencyNotation("hello", null, null), PluginDependencyNotation.parsePluginDependencyString("hello"))
        assertEquals(PluginDependencyNotation("hello", "1.23", null), PluginDependencyNotation.parsePluginDependencyString("hello:1.23"))
    }

    @Test
    fun `MAJOR_VERSION_PATTERN matching`() {
        val regex = MAJOR_VERSION_PATTERN.toRegex()
        assertFalse(regex.matches("EAP1-SNAPSHOT"))
        assertTrue(regex.matches("2024.1-EAP1-SNAPSHOT"))

        assertTrue(regex.matches("RIDER-2024.1-EAP-SNAPSHOT"))
        assertTrue(regex.matches("RIDER-2024.1-EAP9-SNAPSHOT"))
        assertTrue(regex.matches("RIDER-2024.1-RC1-SNAPSHOT"))

        assertTrue(regex.matches("GO-2024.1-EAP1-SNAPSHOT"))
    }

    @Test
    fun `Release types`() {
        assertEquals(releaseType("RIDER-2024.1-RC1-SNAPSHOT"), IntelliJPluginConstants.RELEASE_TYPE_SNAPSHOTS)
        assertEquals(releaseType("IU-2024.1-RC1-SNAPSHOT"), IntelliJPluginConstants.RELEASE_TYPE_NIGHTLY)
    }
}
