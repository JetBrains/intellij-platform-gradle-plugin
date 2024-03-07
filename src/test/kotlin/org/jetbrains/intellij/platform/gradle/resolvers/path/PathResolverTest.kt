// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.latestVersion

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.resolvers.path.PathResolver
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class PathResolverTest : IntelliJPluginTestBase() {

    private val url = resourceUrl("resolvers/latestVersion.xml").run {
        assertNotNull(this)
    }

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
        assertFailsWith<Exception>("called") {
            createResolver(
                "first" to { throw Exception("called") },
                "second" to { dir }
            ).resolve()
        }
    }

    @Test
    fun `fail as cannot be resolved with any prediction`() {
        assertFailsWith<GradleException>("Cannot resolve 'test'") {
            createResolver(
                "first" to { null },
                "second" to { null }
            ).resolve()
        }
    }

    private fun createResolver(vararg elements: Pair<String, () -> Path?>) = object : PathResolver("test") {
        override val predictions: Sequence<Pair<String, () -> Path?>>
            get() = elements.asSequence()
    }
}
