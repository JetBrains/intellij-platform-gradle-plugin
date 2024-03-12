// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertContains

class ProductReleasesValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `list product releases`() {
        buildFile.kotlin(
            """
            tasks {
                val outputFile = file("output.txt")
                val productReleases = providers.of(org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource::class) {
                    parameters {
                        jetbrainsIdes = file("${resource("products-releases/idea-releases-list.xml")}")
                        androidStudio = file("${resource("products-releases/android-studio-releases-list.xml")}")
            
                        sinceBuild = "223"
                        untilBuild = "233"
            
                        types.addAll(
                            IntelliJPlatformType.IntellijIdeaCommunity,
                            IntelliJPlatformType.AndroidStudio,
                        )
                        channels.addAll(
                            ProductRelease.Channel.RELEASE,
                            ProductRelease.Channel.EAP,
                        )
                    }
                }
            
                val generateProductReleasesList by registering {
                    doLast {
                        val content = productReleases.get().joinToString(System.lineSeparator())
                        outputFile.writeText(content)
                    }
                }
            }
            """.trimIndent()
        )

        build("generateProductReleasesList")

        val outputFile = dir.resolve("output.txt")
        val content = outputFile.readLines()

        assertContains(content, "IC-2023.2.6")
        assertContains(content, "IC-2023.1.6")
        assertContains(content, "IC-223.8836.26")
        assertContains(content, "AI-2023.2.1.23")
        assertContains(content, "AI-2023.1.1.26")
        assertContains(content, "AI-2022.3.1.18")
    }
}
