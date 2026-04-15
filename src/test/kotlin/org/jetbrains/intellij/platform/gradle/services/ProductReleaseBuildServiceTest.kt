// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.services.BuildServiceParameters
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.providers.ProductReleaseBuildValueSource
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductReleaseBuildServiceTest {

    @Test
    fun `reuse loaded product release builds for multiple versions from the same source`() {
        val service = object : ProductReleaseBuildService() {
            override fun getParameters() = BuildServiceParameters.None::class.java
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        }
        val content =
            """
            [
              {
                "code": "IC",
                "releases": [
                  { "type": "release", "version": "2024.3", "build": "243.12818.47" },
                  { "type": "release", "version": "2024.3.1", "build": "243.21565.193" }
                ]
              }
            ]
            """.trimIndent()
        var loads = 0
        val objects = ProjectBuilder.builder().build().objects

        val firstParameters = objects.newInstance(ProductReleaseBuildValueSource.Parameters::class.java).apply {
            productsReleasesCdnBuildsUrl.set("https://example/{type}.json")
            version.set("2024.3")
            type.set(IntelliJPlatformType.IntellijIdeaCommunity)
        }
        val secondParameters = objects.newInstance(ProductReleaseBuildValueSource.Parameters::class.java).apply {
            productsReleasesCdnBuildsUrl.set("https://example/{type}.json")
            version.set("2024.3.1")
            type.set(IntelliJPlatformType.IntellijIdeaCommunity)
        }

        val firstResult = service.resolve(firstParameters) { url ->
            loads++
            assertEquals("https://example/IC.json", url)
            content
        }
        val secondResult = service.resolve(secondParameters) { url ->
            loads++
            assertEquals("https://example/IC.json", url)
            content
        }

        assertEquals("243.12818.47", firstResult)
        assertEquals("243.21565.193", secondResult)
        assertEquals(1, loads)
    }
}
