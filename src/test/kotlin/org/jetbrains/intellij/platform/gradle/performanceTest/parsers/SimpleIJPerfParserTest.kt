// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.performanceTest.parsers

import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SimpleIJPerfParserTest {

    @Test
    fun `simple parser test`() {
        val path = Path("src/test/resources/performanceTest/test-scripts/test.ijperf")
        SimpleIJPerformanceParser(path).parse().let {
            assertEquals(60_000, it.assertionTimeout)
            assertEquals("project1", it.projectName)
            assertEquals(
                """
                %startProfile test_alloc event=alloc
                %openFile wi63254/alias.php
                %stopProfile traces=999,flamegraph
                %exitApp
                
                """.trimIndent(),
                it.scriptContent
            )
        }
    }

    @Test
    fun `assertTimeout is null`() {
        val path = Path("src/test/resources/performanceTest/test-scripts/assertTimeoutAbsent.ijperf")
        SimpleIJPerformanceParser(path).parse().let {
            assertNull(it.assertionTimeout)
            assertEquals("project1", it.projectName)
            assertEquals(
                """
                %startProfile test_alloc event=alloc
                %openFile wi63254/alias.php
                %stopProfile traces=999,flamegraph
                %exitApp
                
                """.trimIndent(),
                it.scriptContent
            )
        }
    }
}
