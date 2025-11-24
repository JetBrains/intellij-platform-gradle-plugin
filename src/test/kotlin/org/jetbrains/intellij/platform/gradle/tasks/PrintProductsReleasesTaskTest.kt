// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.models.JetBrainsIdesReleases
import org.jetbrains.intellij.platform.gradle.models.decode
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import org.jetbrains.intellij.platform.gradle.write
import java.net.URI
import kotlin.test.Test

class PrintProductsReleasesTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `print product releases`() {
        // Fetch actual releases from the remote resource
        val content = URI(Locations.PRODUCTS_RELEASES_JETBRAINS_IDES).toURL().readText()
        val releases = decode<JetBrainsIdesReleases>(content)

        // Get all IC release versions grouped by minor version, keeping only the highest patch for each
        val icReleases = releases.products
            .first { intellijPlatformType in it.codes }
            .channels
            .first { it.status == "release" }
            .builds
            .map { it.version.toVersion() }
            .filter { it >= intellijPlatformVersion.toVersion() }
            .groupBy { "${it.major}.${it.minor}" }
            .map { (_, versions) -> versions.maxBy { it.patch } }

        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.PRINT_PRODUCTS_RELEASES} {
                        channels = listOf(org.jetbrains.intellij.platform.gradle.models.ProductRelease.Channel.RELEASE)
                    }
                }
                """.trimIndent()

        build(Tasks.PRINT_PRODUCTS_RELEASES) {
            assertContains(
                "> Task :${Tasks.PRINT_PRODUCTS_RELEASES}\n" + icReleases.joinToString("\n") { "$intellijPlatformType-$it" },
                output,
            )
        }
    }
}
