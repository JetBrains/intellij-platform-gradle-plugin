// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.extensions.parseIdeNotation
import org.jetbrains.intellij.platform.gradle.utils.Version
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class VersionTest {

    @Test
    fun `version parsing`() {
        assertEquals(Version.parse("2021.1.1"), Version(2021, 1, 1))
        assertEquals(Version.parse("2021.1"), Version(2021, 1, 0, "2021.1"))
        assertEquals(Version.parse("2021"), Version(2021, 0, 0, "2021"))
        assertEquals(Version.parse("foo"), Version(0, 0, 0, "foo"))
        assertEquals(Version.parse("203.SNAPSHOT"), Version(203, 0, 0, "203.SNAPSHOT"))
        assertEquals(Version.parse("212.4535-EAP-CANDIDATE-SNAPSHOT"), Version(212, 4535, 0, "212.4535-EAP-CANDIDATE-SNAPSHOT"))
        assertEquals(Version.parse("212.4535.15-EAP-SNAPSHOT"), Version(212, 4535, 15, "212.4535.15-EAP-SNAPSHOT"))
        assertEquals(Version.parse("211-EAP-SNAPSHOT"), Version(211, 0, 0, "211-EAP-SNAPSHOT"))
        assertEquals(Version.parse("LATEST-EAP-SNAPSHOT"), Version(0, 0, 0, "LATEST-EAP-SNAPSHOT"))
        assertEquals(Version.parse("GOLAND-212.4535.15-EAP-SNAPSHOT"), Version(212, 4535, 15, "GOLAND-212.4535.15-EAP-SNAPSHOT"))
        assertEquals(Version.parse("GOLAND-212.4416-EAP-CANDIDATE-SNAPSHOT"), Version(212, 4416, 0, "GOLAND-212.4416-EAP-CANDIDATE-SNAPSHOT"))
        assertEquals(Version.parse("GOLAND-212-EAP-SNAPSHOT"), Version(212, 0, 0, "GOLAND-212-EAP-SNAPSHOT"))
        assertEquals(Version.parse("2021.1 Beta 4"), Version(2021, 1, 0, "2021.1 Beta 4"))
    }

    @Test
    fun `version comparison`() {
        assertTrue(Version.parse("2021.1.2") > Version.parse("2021.1.1"))
        assertTrue(Version.parse("2021.1.2") > Version.parse("2021.1"))
        assertTrue(Version.parse("2021.1.2") > Version.parse("2021"))
        assertTrue(Version.parse("2021.1.2") > Version.parse("2020"))
        assertEquals(Version.parse("2021.1.0-snapshot"), Version.parse("2021.1.0-SNAPSHOT"))
        assertTrue(Version.parse("2021.1.2-SNAPSHOT.2") > Version.parse("2021.1.2-SNAPSHOT.1"))
        assertTrue(Version.parse("2021.1.2") > Version.parse("2021.1.2-SNAPSHOT.1"))
    }

    @Test
    fun `parse IDE notation`() {
        assertEquals("IC-2025.2".parseIdeNotation(), IntelliJPlatformType.IntellijIdeaCommunity to "2025.2")
        assertFailsWith<IllegalArgumentException> { "2025.2".parseIdeNotation() }
        assertFailsWith<IllegalArgumentException> { "2025.3".parseIdeNotation() }
        assertEquals("PS-2025.3".parseIdeNotation(), IntelliJPlatformType.PhpStorm to "2025.3")
        assertEquals("IU-2025.3".parseIdeNotation(), IntelliJPlatformType.IntellijIdea to "2025.3")
    }
}
