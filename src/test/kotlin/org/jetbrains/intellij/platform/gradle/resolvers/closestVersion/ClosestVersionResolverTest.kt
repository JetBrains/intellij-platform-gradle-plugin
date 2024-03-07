// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.closestVersion

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class ClosestVersionResolverTest : IntelliJPluginTestBase() {

    private val url = resourceUrl("resolvers/closestVersion.xml").run {
        assertNotNull(this)
    }

    @Test
    fun `match exact version`() {
        val version = "0.1.8".toVersion()
        val resolvedVersion = createResolver(version).resolve()

        assertEquals(version, resolvedVersion)
    }

    @Test
    fun `match closest version`() {
        val version = "0.1.22".toVersion()
        val resolvedVersion = createResolver(version).resolve()

        assertNotEquals(version, resolvedVersion)
        assertEquals("0.1.21".toVersion(), resolvedVersion)
    }

    private fun createResolver(version: Version) = object : ClosestVersionResolver("test", url) {
        override fun resolve() = inMaven(version)
    }
}
