package org.jetbrains.intellij.performanceTest.parsers

import kotlin.test.*

class SimpleIJPerfParserTest {

    @Test
    fun `simple parser test`() {

        val parsed = SimpleIJPerfParser("src/test/resources/performanceTest/test-scripts/test.ijperf").parse()
        assertEquals(60_000, parsed.assertionTimeout)
        assertEquals("project1", parsed.projectName)
        assertEquals(
            """%startProfile test_alloc event=alloc
%openFile wi63254/alias.php
%stopProfile traces=999,flamegraph
%exitApp
""", parsed.scriptContent
        )
    }

    @Test
    fun `assertTimeout is null`() {
        val parsed =
            SimpleIJPerfParser("src/test/resources/performanceTest/test-scripts/assertTimeoutAbsent.ijperf").parse()
        assertNull(parsed.assertionTimeout)
        assertEquals("project1", parsed.projectName)
        assertEquals(
            """%startProfile test_alloc event=alloc
%openFile wi63254/alias.php
%stopProfile traces=999,flamegraph
%exitApp
""", parsed.scriptContent
        )
    }

}
