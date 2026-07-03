// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.gradleProperties
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test

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
                "> Task :${Tasks.PRINT_PRODUCTS_RELEASES}\nIU-2023.3.4\nIU-2023.2.6",
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
                "> Task :${Tasks.PRINT_PRODUCTS_RELEASES}\nIU-2025.1.6",
                output,
            )
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
}
