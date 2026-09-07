// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY_PRODUCT_RELEASES
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.assertNotContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.gradleProperties
import org.jetbrains.intellij.platform.gradle.overwrite
import org.jetbrains.intellij.platform.gradle.settingsFile
import org.jetbrains.intellij.platform.gradle.write
import java.time.LocalDate
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

class PrintProductsReleasesTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `print product releases`() {
        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.PRINT_PRODUCTS_RELEASES} {
                        types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea)
                        channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                        sinceBuild = "223"
                        untilBuild = "233.*"
                    }
                }
                """.trimIndent()

        build(
            Tasks.PRINT_PRODUCTS_RELEASES,
            projectProperties = mapOf(
                GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to resourceUrl("products-releases/jetbrains-product-releases-IC.json").toString().replace("IC.json", "{type}.json"),
            ),
        ) {
            assertContains(
                """
                > Task :${Tasks.PRINT_PRODUCTS_RELEASES}
                IU-2023.3.8
                IU-2023.2.8
                IU-2023.1.7
                IU-2022.3.3
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `print product releases for IntelliJ IDEA with default filters`() {
        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.PRINT_PRODUCTS_RELEASES} {
                        types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea)
                    }
                }
                """.trimIndent()

        build(
            Tasks.PRINT_PRODUCTS_RELEASES,
            projectProperties = mapOf(
                GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to resourceUrl("products-releases/jetbrains-product-releases-IC.json").toString().replace("IC.json", "{type}.json"),
            ),
        ) {
            assertContains(
                """
                > Task :${Tasks.PRINT_PRODUCTS_RELEASES}
                IU-262.8665.81
                IU-2026.1.4
                IU-2025.3.6
                IU-2025.2.6.2
                IU-2025.1.7.1
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `community product releases include unified IntelliJ IDEA versions`() {
        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.PRINT_PRODUCTS_RELEASES} {
                        types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdeaCommunity)
                        channels = listOf(
                            org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE,
                            org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.EAP,
                            org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RC,
                        )
                        sinceBuild = "241"
                    }
                }
                """.trimIndent()

        build(
            Tasks.PRINT_PRODUCTS_RELEASES,
            projectProperties = mapOf(
                GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to resourceUrl("products-releases/jetbrains-product-releases-IC.json").toString().replace("IC.json", "{type}.json"),
            ),
        ) {
            assertContains("IC-2025.2.6.2", output)
            assertContains("IU-2025.3.6", output)
            assertContains("IU-2026.1.4", output)
            assertContains("IU-262.8665.81", output)
            assertNotContains("IU-2025.2.6.2", output)
        }
    }

    @Test
    fun `reuses configuration cache`() {
        gradleProperties write //language=properties
                """
                ${GradleProperties.ProductsReleasesCdnBuildsUrl}=${resourceUrl("products-releases/jetbrains-product-releases-IC.json").toString().replace("IC.json", "{type}.json")}
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.PRINT_PRODUCTS_RELEASES} {
                        types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea)
                        channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                    }
                }
                """.trimIndent()

        buildWithConfigurationCache(Tasks.PRINT_PRODUCTS_RELEASES)

        buildWithConfigurationCache(Tasks.PRINT_PRODUCTS_RELEASES) {
            assertConfigurationCacheReused()
            assertContains("> Task :${Tasks.PRINT_PRODUCTS_RELEASES}", output)
        }
    }

    @Test
    fun `product release listings are cached between task runs and subprojects`() {
        settingsFile write //language=kotlin
                """
                include("first", "second")
                """.trimIndent()

        val listingContent = checkNotNull(javaClass.classLoader.getResource("products-releases/jetbrains-product-releases-IU.json")).readText()
        val listingFile = dir.resolve("jetbrains-product-releases-IU.json")
        listingFile overwrite listingContent

        gradleProperties write //language=properties
                """
                ${GradleProperties.ProductsReleasesCdnBuildsUrl}=${listingFile.toUri().toString().replace("IU.json", "{type}.json")}
                """.trimIndent()

        listOf("first", "second").forEach { subproject ->
            dir.resolve("$subproject/build.gradle.kts") write //language=kotlin
                    """
                    plugins {
                        id("org.jetbrains.intellij.platform")
                    }
                    
                    tasks {
                        ${Tasks.PRINT_PRODUCTS_RELEASES} {
                            types = listOf(org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea)
                            channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                            sinceBuild = "223"
                            untilBuild = "233.*"
                        }
                    }
                    """.trimIndent()
        }

        build(":first:${Tasks.PRINT_PRODUCTS_RELEASES}") {
            assertContains(
                """
                > Task :first:${Tasks.PRINT_PRODUCTS_RELEASES}
                IU-2023.3.8
                IU-2023.2.8
                IU-2023.1.7
                IU-2022.3.3
                """.trimIndent(),
                output,
            )
        }

        val cacheDirectory = dir.resolve(CACHE_DIRECTORY).resolve(CACHE_DIRECTORY_PRODUCT_RELEASES)
        val cacheFiles = cacheDirectory.listDirectoryEntries("*.json")
        val lockFiles = cacheDirectory.listDirectoryEntries("*.lock")

        assertEquals(1, cacheFiles.size)
        assertEquals(1, lockFiles.size)
        assertEquals(LocalDate.now().toString(), lockFiles.single().readText().trim())

        listingFile overwrite //language=json
                """not-json"""

        build(":second:${Tasks.PRINT_PRODUCTS_RELEASES}") {
            assertContains(
                """
                > Task :second:${Tasks.PRINT_PRODUCTS_RELEASES}
                IU-2023.3.8
                IU-2023.2.8
                IU-2023.1.7
                IU-2022.3.3
                """.trimIndent(),
                output,
            )
        }

        assertEquals(1, cacheDirectory.listDirectoryEntries("*.json").size)
        assertEquals(1, cacheDirectory.listDirectoryEntries("*.lock").size)

        cacheFiles.single() overwrite //language=json
                """not-json"""
        lockFiles.single() overwrite LocalDate.now().minusDays(1).toString()
        listingFile overwrite listingContent

        build(":second:${Tasks.PRINT_PRODUCTS_RELEASES}") {
            assertContains(
                """
                > Task :second:${Tasks.PRINT_PRODUCTS_RELEASES}
                IU-2023.3.8
                IU-2023.2.8
                IU-2023.1.7
                IU-2022.3.3
                """.trimIndent(),
                output,
            )
        }

        assertEquals(LocalDate.now().toString(), lockFiles.single().readText().trim())
    }
}
