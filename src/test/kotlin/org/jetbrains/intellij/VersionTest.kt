package org.jetbrains.intellij

import kotlin.test.Test
import kotlin.test.assertEquals
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
    }

    @Test
    fun `version comparison`() {
        assertTrue(Version.parse("2021.1.2") > Version.parse("2021.1.1"))
        assertTrue(Version.parse("2021.1.2") > Version.parse("2021.1"))
        assertTrue(Version.parse("2021.1.2") > Version.parse("2021"))
        assertTrue(Version.parse("2021.1.2") > Version.parse("2020"))
        assertTrue(Version.parse("2021.1.2") > Version.parse("2020"))
        assertEquals(Version.parse("2021.1.0-snapshot"), Version.parse("2021.1.0-SNAPSHOT"))
        assertTrue(Version.parse("2021.1.2-SNAPSHOT.2") > Version.parse("2021.1.2-SNAPSHOT.1"))
    }
}
