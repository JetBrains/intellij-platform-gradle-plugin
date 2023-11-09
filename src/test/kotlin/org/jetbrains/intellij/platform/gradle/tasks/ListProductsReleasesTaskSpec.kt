// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.io.path.readText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ListProductsReleasesTaskSpec : IntelliJPluginSpecBase() {

    private val ideaReleasesPath = resolveResourcePath("products-releases/idea-releases.xml")

    private val androidStudioReleasesPath = resolveResourcePath("products-releases/android-studio-products-releases.xml")

    private val outputFile
        get() = buildDirectory.resolve("${Tasks.LIST_PRODUCTS_RELEASES}.txt")

    @BeforeTest
    override fun setup() {
        super.setup()

        buildFile.kotlin(
            """
            tasks {
                listProductsReleases {
                    ideaProductReleasesUpdateFiles.from("$ideaReleasesPath")
                    androidStudioProductReleasesUpdateFiles.from("$androidStudioReleasesPath")
                }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `get IDEs list for the current platformType, sinceBuild and untilBuild`() {
        build(Tasks.LIST_PRODUCTS_RELEASES)

        assertEquals(
            """
            IC-2022.3.3
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list for the current platformType`() {
        buildFile.groovy(
            """
            tasks {
                listProductsReleases {
                    sinceVersion.set("231")
                }
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            IC-2023.3
            IC-2023.2.4
            IC-2023.1.5
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list for the current platformType and limited versions scope`() {
        buildFile.groovy(
            """
            tasks {
                listProductsReleases {
                    sinceVersion.set("2020.3")
                    untilVersion.set("2021.2.1")
                }
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            IC-2021.2.1
            IC-2021.1.3
            IC-2020.3.4
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list using sinceBuild and untilBuild`() {
        buildFile.groovy(
            """
            tasks {
                patchPluginXml {
                    sinceBuild.set("2020.3")
                    untilBuild.set("2021.2.1")
                }
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            IC-2021.2.1
            IC-2021.1.3
            IC-2020.3.4
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list using sinceBuild despite it is lower than intellij_version`() {
        buildFile(
            tasks =
            """
            patchPluginXml {
                sinceBuild.set("222")
                untilBuild.set("232.*")
            }
            """.trimIndent(),
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            IC-2023.2.4
            IC-2023.1.5
            IC-2022.3.3
            IC-2022.2.5
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list for the custom platformType and platformVersion defined in intellij`() {
        buildFile(
            dependencies =
            """
            intellijPlatform {
                pycharmCommunity("$intellijVersion")
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            PC-2022.3.3
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list without EAP releases`() {
        buildFile.groovy(
            """
            tasks {
                listProductsReleases {
                    sinceVersion.set("2023.1")
                    releaseChannels.set(EnumSet.of(ListProductsReleasesTask.Channel.RELEASE))
                }
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            IC-2023.2.4
            IC-2023.1.5
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list for the multiple platformTypes`() {
        buildFile.groovy(
            """
            tasks {
                listProductsReleases {
                    sinceVersion.set("2023.1")
                    types.addAll(listOf("IU", "PS", "PY").map { IntelliJPlatformType.fromCode(it) })
                }
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            IU-2023.3
            IU-2023.2.4
            IU-2023.1.5
            PS-2023.3
            PS-2023.2.3
            PS-2023.1.4
            PY-2023.3
            PY-2023.2.4
            PY-2023.1.4
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `productsReleasesUpdateFiles uses values from updatePaths`() {
        buildFile.groovy(
            """
            tasks {
                downloadIdeaProductReleasesXml {
                    enabled = false                
                }
                listProductsReleases {
                    sinceVersion.set("2023.1")
                    types.addAll(listOf("IU", "PS", "PY").map { IntelliJPlatformType.fromCode(it) })
                    ideaProductReleasesUpdateFiles.from("$ideaReleasesPath")
                }
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            IU-2023.3
            IU-2023.2.4
            IU-2023.1.5
            PS-2023.3
            PS-2023.2.3
            PS-2023.1.4
            PY-2023.3
            PY-2023.2.4
            PY-2023.1.4
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get Android Studio releases`() {
        buildFile.groovy(
            """
            tasks {
                listProductsReleases {
                    types.add(IntelliJPlatformType.AndroidStudio)
                }
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            AI-2022.3.1.20
            AI-2023.1.1.1
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get Android Studio releases for Release channel`() {
        buildFile.groovy(
            """
            tasks {
                listProductsReleases {
                    types.add(IntelliJPlatformType.AndroidStudio)
                    releaseChannels.set(EnumSet.of(ListProductsReleasesTask.Channel.RELEASE))
                }
            }
            """.trimIndent()
        )

        build(Tasks.LIST_PRODUCTS_RELEASES)
        assertEquals(
            """
            AI-2022.3.1.18
            """.trimIndent(),
            outputFile.readText(),
        )
    }
}
