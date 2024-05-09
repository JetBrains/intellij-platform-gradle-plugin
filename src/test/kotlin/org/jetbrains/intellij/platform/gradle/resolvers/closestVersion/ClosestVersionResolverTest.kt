// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.closestVersion

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import java.net.URL
import kotlin.test.*

class ClosestVersionResolverTest : IntelliJPluginTestBase() {

    private val defaultUrls = listOf(resourceUrl("resolvers/closestVersion.xml").run {
        assertNotNull(this)
    })

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

    @Test
    fun `ignore unresolvable URLs`() {
        val version = "0.1.8".toVersion()
        val urls = defaultUrls + URL("file:///foo")
        val resolvedVersion = createResolver(version, urls).resolve()

        assertEquals(version, resolvedVersion)
    }

    @Test
    fun `fail when no version is available for matching`() {
        val version = "0.1.8".toVersion()

        val exception = assertFailsWith<GradleException> {
            createResolver(version, emptyList()).resolve()
        }
        assertEquals(
            """
            Cannot resolve the test version closest to: $version
            Please ensure there are necessary repositories present in the project repositories section where the `foo:bar` artifact is published, i.e., by adding the `defaultRepositories()` entry.
            See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-repositories-extension.html
            """.trimIndent(),
            exception.message,
        )
    }

    private fun createResolver(version: Version, urls: List<URL> = defaultUrls) = object : ClosestVersionResolver(
        coordinates = Coordinates("foo", "bar"),
        urls = urls,
    ) {

        override val subject = "test"

        override fun resolve() = inMaven(version)
    }
}
