// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.performanceTest.parsers

import kotlin.test.*

class IdeaLogParserTest {

    @Test
    fun `simple parser test`() {

        val parsed = IdeaLogParser("src/test/resources/performanceTest/idea-log/idea.log").getTestStatistic()
        assertEquals(1_573, parsed.totalTime)
        assertEquals(3, parsed.responsive)
        assertEquals(777, parsed.averageResponsive)
    }

    @Test
    fun `total time absent test`() {
        val parsed = IdeaLogParser("src/test/resources/performanceTest/idea-log/idea-no-total-time.log").getTestStatistic()
        assertNull(parsed.totalTime)
        assertEquals(3, parsed.responsive)
        assertEquals(999, parsed.averageResponsive)
    }

}

