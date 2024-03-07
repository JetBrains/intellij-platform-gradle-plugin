// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.latestVersion

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class LatestVersionResolverTest : IntelliJPluginTestBase() {

    private val url = resourceUrl("resolvers/latestVersion.xml").run {
        assertNotNull(this)
    }

    @Test
    fun `match latest version`() {
        val resolvedVersion = createResolver().resolve()

        assertEquals("0.1.24".toVersion(), resolvedVersion)
    }

    private fun createResolver() = object : LatestVersionResolver("test", url) {
        override fun resolve() = fromMaven()
    }
}
