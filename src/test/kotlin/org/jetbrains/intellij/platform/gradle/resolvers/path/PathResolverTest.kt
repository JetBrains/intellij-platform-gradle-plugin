// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PathResolverTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve path with the second prediction`() {
        val resolvedPath = createResolver(
            "first" to { null },
            "second" to { dir }
        ).resolve()

        assertEquals(dir, resolvedPath)
    }

    @Test
    fun `don't resolve path with the second prediction`() {
        val resolvedPath = kotlin.runCatching {
            createResolver(
                "first" to { dir },
                "second" to { throw Exception("never called") }
            ).resolve()
        }.getOrNull()
        assertEquals(dir, resolvedPath)
    }

    @Test
    fun `fail on first prediction`() {
        val exception = assertFailsWith<Exception> {
            createResolver(
                "first" to { throw Exception("called") },
                "second" to { dir }
            ).resolve()
        }
        assertEquals("called", exception.message)
    }

    @Test
    fun `fail as cannot be resolved with any prediction`() {
        val exception = assertFailsWith<IllegalArgumentException> {
            createResolver(
                "first" to { null },
                "second" to { null }
            ).resolve()
        }
        assertEquals("Cannot resolve 'test'", exception.message)
    }

    private fun createResolver(vararg elements: Pair<String, () -> Path?>) = object : PathResolver() {

        override val subject = "test"

        override val predictions = elements.asSequence()
    }
}
