// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DumpProductsReleasesTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `dump product releases`() {
        val listingsDirectory = dir.resolve("product-releases").createDirectories()
        val fallbackListing = resourceUrl("products-releases/jetbrains-product-releases-IC.json").readText()

        IntelliJPlatformType.entries
            .map { it.code }
            .distinct()
            .forEach { code ->
                val listing = javaClass.classLoader
                    .getResource("products-releases/jetbrains-product-releases-$code.json")
                    ?.readText()
                    ?: fallbackListing
                listingsDirectory.resolve("$code.json").writeText(listing)
            }

        build(
            Tasks.DUMP_PRODUCTS_RELEASES,
            projectProperties = mapOf(
                GradleProperties.ProductsReleasesCdnBuildsUrl.toString() to
                        listingsDirectory.resolve("IC.json").toUri().toString().replace("IC.json", "{type}.json"),
                GradleProperties.ProductsReleasesAndroidStudioUrl.toString() to
                        resourceUrl("products-releases/android-studio-releases-list.json").toString(),
            ),
        )

        val outputFile = dir.resolve("build/tmp/${Tasks.DUMP_PRODUCTS_RELEASES}/product-releases.txt")
        assertTrue(outputFile.exists())

        val releases = outputFile.readLines()
        assertContains(releases, "IC\t2023.3\tRELEASE")
        assertContains(releases, "IU\t2023.3\tRELEASE")
        assertTrue(releases.any { it.startsWith("IC\t") && it.endsWith("\tEAP") })
        assertEquals(releases.distinct().sorted(), releases)
    }
}
