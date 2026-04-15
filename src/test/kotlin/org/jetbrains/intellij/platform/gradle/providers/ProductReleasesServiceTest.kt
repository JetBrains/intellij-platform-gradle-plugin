// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.api.services.BuildServiceParameters
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import org.jetbrains.intellij.platform.gradle.services.ProductReleasesService
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductReleasesServiceTest {

    @Test
    fun `reuse loaded releases across filters for the same sources`() {
        val service = object : ProductReleasesService() {
            override fun getParameters() = BuildServiceParameters.None::class.java
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        }
        val contents = mapOf(
            "jetbrains" to checkNotNull(javaClass.classLoader.getResource("products-releases/idea-releases-list.xml")).readText(),
            "androidStudio" to checkNotNull(javaClass.classLoader.getResource("products-releases/android-studio-releases-list.xml")).readText(),
        )
        var loads = 0
        val objects = ProjectBuilder.builder().build().objects

        val firstParameters = objects.newInstance(ProductReleasesValueSource.Parameters::class.java).apply {
            jetbrainsIdesUrl.set("jetbrains")
            androidStudioUrl.set("androidStudio")
            sinceBuild.set("223")
            untilBuild.set("233")
            types.set(
                listOf(
                    IntelliJPlatformType.IntellijIdeaCommunity,
                    IntelliJPlatformType.AndroidStudio,
                )
            )
            channels.set(
                listOf(
                    ProductRelease.Channel.RELEASE,
                    ProductRelease.Channel.EAP,
                )
            )
        }
        val secondParameters = objects.newInstance(ProductReleasesValueSource.Parameters::class.java).apply {
            jetbrainsIdesUrl.set("jetbrains")
            androidStudioUrl.set("androidStudio")
            sinceBuild.set("223")
            untilBuild.set("233")
            types.set(listOf(IntelliJPlatformType.IntellijIdeaCommunity))
            channels.set(listOf(ProductRelease.Channel.RELEASE))
        }

        val firstResult = service.resolve(firstParameters) { url ->
            loads++
            contents.getValue(url)
        }
        val secondResult = service.resolve(secondParameters) { url ->
            loads++
            contents.getValue(url)
        }

        assertEquals(
            listOf(
                "IC-2023.2.6",
                "IC-2023.1.6",
                "IC-2022.3.3",
                "AI-2023.2.1.23",
                "AI-2023.1.1.26",
                "AI-2022.3.1.18",
            ),
            firstResult,
        )
        assertEquals(
            listOf(
                "IC-2023.2.6",
                "IC-2023.1.6",
                "IC-2022.3.3",
            ),
            secondResult,
        )
        assertEquals(2, loads)
    }
}
