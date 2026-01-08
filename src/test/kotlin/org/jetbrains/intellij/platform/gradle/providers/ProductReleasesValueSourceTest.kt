// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.models.ProductRelease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProductReleasesValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `list RELEASE, EAP releases for IC, AS in 223-233 range`() {
        prepareTest(
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

        build(randomTaskName) {
            assertLogValue("Product releases: ") {
                val content = it.split(";")
                assertEquals(
                    listOf(
                        "IC-2023.2.6",
                        "IC-2023.1.6",
                        "IC-2022.3.3",
                        "AI-2023.2.1.23",
                        "AI-2023.1.1.26",
                        "AI-2022.3.1.18",
                    ),
                    content,
                )
            }
        }
    }

    @Test
    fun `list EAP releases for RR in 232-233 range`() {
        prepareTest(
            sinceBuild = "232",
            untilBuild = "233",
            types = listOf(
                IntelliJPlatformType.RustRover,
            ),
            channels = listOf(
                ProductRelease.Channel.EAP,
            ),
        )

        build(randomTaskName) {
            assertLogValue("Product releases: ") {
                val content = it.split(";")
                assertEquals(listOf("RR-232.9921.62"), content)
            }
        }
    }

    @Test
    fun `list no releases for 231-230 range`() {
        prepareTest(
            sinceBuild = "231",
            untilBuild = "230",
            types = listOf(
                IntelliJPlatformType.IntellijIdeaCommunity,
            ),
            channels = listOf(
                ProductRelease.Channel.EAP,
            ),
        )

        build(randomTaskName) {
            assertLogValue("Product releases: ") {
                assertTrue(it.isEmpty())
            }
        }
    }

    @Test
    fun `list releases for IC in 2024_1 range does not include IU`() {
        prepareTest(
            sinceBuild = "241",
            untilBuild = "241.14494.17",
            types = listOf(
                IntelliJPlatformType.IntellijIdeaCommunity,
            ),
            channels = listOf(
                ProductRelease.Channel.EAP,
            )
        )

        build(randomTaskName) {
            assertLogValue("Product releases: ") {
                val content = it.split(";")
                // Should only contain IC, not IU, even though both are in the same product/channel in XML
                assertEquals(listOf("IC-241.14494.17"), content)
            }
        }
    }

    private fun prepareTest(
        sinceBuild: String,
        untilBuild: String,
        types: List<IntelliJPlatformType>,
        channels: List<ProductRelease.Channel>,
    ) {
        buildFile write //language=kotlin
                """
                tasks {
                    val productReleases = providers.of(org.jetbrains.intellij.platform.gradle.providers.ProductReleasesValueSource::class) {
                        parameters {
                            jetbrainsIdesUrl = "${resourceUrl("products-releases/idea-releases-list.xml")}"
                            androidStudioUrl = "${resourceUrl("products-releases/android-studio-releases-list.xml")}"
                
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
                
                    register("$randomTaskName") {
                        doLast {
                            println("Product releases: " + productReleases.get().joinToString(";"))
                        }
                    }
                }
                """.trimIndent()
    }
}
