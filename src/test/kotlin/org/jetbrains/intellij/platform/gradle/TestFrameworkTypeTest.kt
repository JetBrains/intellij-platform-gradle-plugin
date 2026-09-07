// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.utils.Version
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TestFrameworkTypeTest {

    @Test
    fun `Starter coordinates exclude product dependencies before build 262`() {
        val coordinates = TestFrameworkType.Starter.coordinatesFor(Version.parse("261.999"))

        assertContains(coordinates, Coordinates("com.jetbrains.intellij.tools", "ide-starter-squashed"))
        assertContains(coordinates, Coordinates("com.jetbrains.intellij.tools", "ide-starter-junit5"))
        assertContains(coordinates, Coordinates("com.jetbrains.intellij.tools", "ide-starter-driver"))
        assertContains(coordinates, Coordinates("com.jetbrains.intellij.driver", "driver-client"))
        assertFalse(coordinates.any { it.isIdeStarterProduct() })
        assertTrue(coordinates.size < TestFrameworkType.Starter.coordinates.size)
    }

    @Test
    fun `Starter coordinates include product dependencies since build 262`() {
        val coordinates = TestFrameworkType.Starter.coordinatesFor(Version.parse("262"))

        assertContains(coordinates, Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-idea-ultimate"))
        assertContains(coordinates, Coordinates("com.jetbrains.intellij.tools", "ide-starter-product-gateway"))
        assertEquals(coordinates, TestFrameworkType.Starter.coordinates.toList())
    }

    @Test
    fun `Plugin test framework types use published coordinates`() {
        val coordinates = mapOf(
            TestFrameworkType.Plugin.IJent to Coordinates("com.jetbrains.intellij.platform", "ijent-test-framework"),
            TestFrameworkType.Plugin.MLCompletion to Coordinates("com.jetbrains.intellij.ml", "ml-llm-completion-test-framework"),
            TestFrameworkType.Plugin.MLCompletionCloud to Coordinates("com.jetbrains.intellij.ml", "ml-llm-completion-cloud-test-framework"),
            TestFrameworkType.Plugin.MLNextEdits to Coordinates("com.jetbrains.intellij.ml", "ml-llm-next-edits-test-framework"),
            TestFrameworkType.Plugin.NavBar to Coordinates("com.jetbrains.intellij.platform", "navbar-test-framework"),
            TestFrameworkType.Plugin.Python to Coordinates("com.jetbrains.intellij.python", "python-community-test-framework"),
            TestFrameworkType.Plugin.RDClient to Coordinates("com.jetbrains.intellij.rd", "rd-client-test-framework"),
            TestFrameworkType.Plugin.Rider to Coordinates("com.jetbrains.intellij.rider", "rider-test-framework"),
            TestFrameworkType.Plugin.Statistics to Coordinates("com.jetbrains.intellij.platform", "statistics-test-framework"),
        )

        coordinates.forEach { (type, coordinate) ->
            assertEquals(listOf(coordinate), type.coordinates.toList())
        }
    }

    private companion object {
        fun Coordinates.isIdeStarterProduct() = artifactId.startsWith("ide-starter-product-")
    }
}
