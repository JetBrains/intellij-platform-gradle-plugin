// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ListProductsReleasesTaskSpec : IntelliJPluginSpecBase() {

    private val ideaReleasesPath = resolveResourcePath("products-releases/idea-releases.xml")

    private val androidStudioReleasesPath = resolveResourcePath("products-releases/android-studio-products-releases.xml")

    private val outputFile
        get() = buildDirectory.resolve("$LIST_PRODUCTS_RELEASES_TASK_NAME.txt")

    @BeforeTest
    override fun setup() {
        super.setup()

        buildFile.groovy(
            """
            intellij {
                version = "2020.1"
            }
            listProductsReleases {
                ideaProductReleasesUpdateFiles.setFrom(['$ideaReleasesPath'])
                androidStudioProductReleasesUpdateFiles.setFrom(['$androidStudioReleasesPath'])
            }
            """.trimIndent()
        )
    }

    @Test
    fun `get IDEs list for the current platformType, sinceBuild and untilBuild`() {
        build(LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            """
            IC-2020.1.4
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list for the current platformType`() {
        buildFile.groovy(
            """
            listProductsReleases {
                sinceVersion = "201"
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            IC-2021.2.2
            IC-2021.1.3
            IC-2020.3.4
            IC-2020.2.4
            IC-2020.1.4
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list for the current platformType and limited versions scope`() {
        buildFile.groovy(
            """
            listProductsReleases {
                sinceVersion = "2020.3"
                untilVersion = "2021.2.1"
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
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
            patchPluginXml {
                sinceBuild = "203"
                untilBuild = "212.*"
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            IC-2021.2.2
            IC-2021.1.3
            IC-2020.3.4
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list using sinceBuild despite it is lower than intellij_version`() {
        buildFile.groovy(
            """
            intellij {
                version = "2021.1"
            }
            patchPluginXml {
                sinceBuild = "203"
                untilBuild = "212.*"
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            IC-2021.2.2
            IC-2021.1.3
            IC-2020.3.4
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list for the custom platformType and platformVersion defined in intellij`() {
        buildFile.groovy(
            """
            intellij {
                type = "PY"
                version = "2021.1"
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            PY-2021.1.3
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list without EAP releases`() {
        buildFile.groovy(
            """
            import org.jetbrains.intellij.platform.gradle.tasks.ListProductsReleasesTask.Channel
            
            listProductsReleases {
                sinceVersion = "2021.1"
                releaseChannels = EnumSet.of(Channel.RELEASE)
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            IC-2021.2.1
            IC-2021.1.3
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get IDEs list for the multiple platformTypes`() {
        buildFile.groovy(
            """
            listProductsReleases {
                sinceVersion = "2021.1"
                types = ["IU", "PS", "PY"]
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            IU-2021.2.2
            IU-2021.1.3
            PS-2021.2.2
            PS-2021.1.4
            PY-2021.2.2
            PY-2021.1.3
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `productsReleasesUpdateFiles uses values from updatePaths`() {
        buildFile.groovy(
            """
            
            // disable the download task, so it doesn't contribute
            tasks.downloadIdeaProductReleasesXml.configure { enabled = false }
            
            listProductsReleases {
                sinceVersion = "2021.1"
                types = ["IU", "PS", "PY"]
                
                ideaProductReleasesUpdateFiles.setFrom(['$ideaReleasesPath'])
                
                // no values set for productsReleasesUpdateFiles
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            IU-2021.2.2
            IU-2021.1.3
            PS-2021.2.2
            PS-2021.1.4
            PY-2021.2.2
            PY-2021.1.3
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get Android Studio releases`() {
        buildFile.groovy(
            """
            listProductsReleases {
                sinceVersion = "2021.1"
                types = ["AI"]
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            AI-2021.3.1.7
            AI-2021.2.1.11
            AI-2021.1.1.22
            """.trimIndent(),
            outputFile.readText(),
        )
    }

    @Test
    fun `get Android Studio releases for Release channel`() {
        buildFile.groovy(
            """
            import org.jetbrains.intellij.platform.gradle.tasks.ListProductsReleasesTask.Channel
            
            listProductsReleases {
                sinceVersion = "2021.1"
                types = ["AI"]
                releaseChannels = EnumSet.of(Channel.RELEASE)
            }
            """.trimIndent()
        )

        build(LIST_PRODUCTS_RELEASES_TASK_NAME)
        assertEquals(
            """
            AI-2021.1.1.20
            """.trimIndent(),
            outputFile.readText(),
        )
    }
}
