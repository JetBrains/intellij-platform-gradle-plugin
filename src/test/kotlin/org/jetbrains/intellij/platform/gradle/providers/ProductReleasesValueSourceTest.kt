// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals

private const val TASK_NAME = "generateProductReleasesList"

class ProductReleasesValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `list RELEASE, EAP releases for IC, AS in 223-233 range`() {
        prepareGradleTask(
            sinceBuild = "223",
            untilBuild = "233",
            types = listOf(
                IntelliJPlatformType.IntellijIdeaCommunity,
                IntelliJPlatformType.AndroidStudio,
            ),
            channels = listOf(
                ProductRelease.Channel.RELEASE,
                ProductRelease.Channel.EAP,
            )
        )

        build(TASK_NAME)

        assertEquals(
            listOf(
                "IC-2023.2.6",
                "IC-2023.1.6",
                "IC-223.8836.26",
                "AI-2023.2.1.23",
                "AI-2023.1.1.26",
                "AI-2022.3.1.18",
            ),
            dir.resolve("output.txt").readLines(),
        )
    }

    @Test
    fun `list EAP releases for RR in 232-233 range`() {
        prepareGradleTask(
            sinceBuild = "232",
            untilBuild = "233",
            types = listOf(
                IntelliJPlatformType.RustRover,
            ),
            channels = listOf(
                ProductRelease.Channel.EAP,
            )
        )

        build(TASK_NAME)

        assertEquals(
            listOf("RR-232.9921.62"),
            dir.resolve("output.txt").readLines(),
        )
    }

    @Test
    fun `list no releases for 231-230 range`() {
        prepareGradleTask(
            sinceBuild = "231",
            untilBuild = "230",
            types = listOf(
                IntelliJPlatformType.IntellijIdeaCommunity,
            ),
            channels = listOf(
                ProductRelease.Channel.EAP,
            )
        )

        build(TASK_NAME)

        assert(dir.resolve("output.txt").readLines().isEmpty())
    }

    private fun prepareGradleTask(sinceBuild: String, untilBuild: String, types: List<IntelliJPlatformType>, channels: List<ProductRelease.Channel>) {
        buildFile.kotlin(
            """
            tasks {
                val outputFile = file("output.txt")
                val productReleases = providers.of(org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource::class) {
                    parameters {
                        jetbrainsIdes = file("${resource("products-releases/idea-releases-list.xml")}")
                        androidStudio = file("${resource("products-releases/android-studio-releases-list.xml")}")
            
                        sinceBuild = "$sinceBuild"
                        untilBuild = "$untilBuild"
            
                        types.addAll(
                            ${types.joinToString(", ") { "IntelliJPlatformType.fromCode(\"${it.code}\")" }}
                        )
                        channels.addAll(
                            ${channels.joinToString(", ") { "ProductRelease.Channel.valueOf(\"${it.name}\")" }}
                        )
                    }
                }
            
                val $TASK_NAME by registering {
                    doLast {
                        val content = productReleases.get().joinToString(System.lineSeparator())
                        outputFile.writeText(content)
                    }
                }
            }
            """.trimIndent()
        )
    }
}
