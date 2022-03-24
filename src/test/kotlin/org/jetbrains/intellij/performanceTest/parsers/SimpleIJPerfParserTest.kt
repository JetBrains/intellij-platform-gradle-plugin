package org.jetbrains.intellij.performanceTest.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimpleIJPerfParserTest {

    @Test
    fun `simple parser test`() {
        SimpleIJPerformanceParser("src/test/resources/performanceTest/test-scripts/test.ijperf").parse().let {
            assertEquals(60_000, it.assertionTimeout)
            assertEquals("project1", it.projectName)
            assertEquals(
                """
                    %startProfile test_alloc event=alloc
                    %openFile wi63254/alias.php
                    %stopProfile traces=999,flamegraph
                    %exitApp
                    
                """.trimIndent(), it.scriptContent
            )
        }
    }

    @Test
    fun `assertTimeout is null`() {
        SimpleIJPerformanceParser("src/test/resources/performanceTest/test-scripts/assertTimeoutAbsent.ijperf").parse().let {
            assertNull(it.assertionTimeout)
            assertEquals("project1", it.projectName)
            assertEquals(
                """
                    %startProfile test_alloc event=alloc
                    %openFile wi63254/alias.php
                    %stopProfile traces=999,flamegraph
                    %exitApp
                    
                """.trimIndent(), it.scriptContent
            )
        }
    }
}
