// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.performanceTest.parsers

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IdeaLogParserTest {

    @Test
    fun `simple parser test`() {
        IdeaLogParser("src/test/resources/performanceTest/idea-log/idea.log").getTestStatistic().let {
            assertEquals(1_573, it.totalTime)
            assertEquals(3, it.responsive)
            assertEquals(777, it.averageResponsive)
        }
    }

    @Test
    fun `total time absent test`() {
        IdeaLogParser("src/test/resources/performanceTest/idea-log/idea-no-total-time.log").getTestStatistic().let {
            assertNull(it.totalTime)
            assertEquals(3, it.responsive)
            assertEquals(999, it.averageResponsive)
        }
    }
}
