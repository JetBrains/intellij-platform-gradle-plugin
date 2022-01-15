package org.jetbrains.intellij.tasks

import org.gradle.testkit.runner.BuildResult
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ListProductsReleasesTaskSpec : IntelliJPluginSpecBase() {

    @BeforeTest
    override fun setUp() {
        super.setUp()

        val resources = listOf(
            "products-releases/idea-releases.xml",
            "products-releases/android-studio-releases.xml",
        ).joinToString { "'${javaClass.classLoader.getResource(it)?.path}'" }

        buildFile.groovy("""
            listProductsReleases {
                updatePaths = [${resources}]
            }
        """)
    }

    @Test
    fun `get IDEs list for the current platformType, sinceBuild and untilBuild`() {
        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("IC-2020.1.4"),
            result.taskOutput()
        )
    }

    @Test
    fun `get IDEs list for the current platformType`() {
        buildFile.groovy("""
            listProductsReleases {
                sinceVersion = "201"
            }
        """)

        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("IC-2021.2.2", "IC-2021.1.3", "IC-2020.3.4", "IC-2020.2.4", "IC-2020.1.4"),
            result.taskOutput()
        )
    }

    @Test
    fun `get IDEs list for the current platformType and limited versions scope`() {
        buildFile.groovy("""
            listProductsReleases {
                sinceVersion = "2020.3"
                untilVersion = "2021.2.1"
            }
        """)

        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("IC-2021.2.1", "IC-2021.1.3", "IC-2020.3.4"),
            result.taskOutput()
        )
    }

    @Test
    fun `get IDEs list using sinceBuild and untilBuild`() {
        buildFile.groovy("""
            patchPluginXml {
                sinceBuild = "203"
                untilBuild = "212.*"
            }
        """)

        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("IC-2021.2.2", "IC-2021.1.3", "IC-2020.3.4"),
            result.taskOutput()
        )
    }

    @Test
    fun `get IDEs list using sinceBuild despite it is lower than intellij_version`() {
        buildFile.groovy("""
            intellij {
                version = "2021.1"
            }
            patchPluginXml {
                sinceBuild = "203"
                untilBuild = "212.*"
            }
        """)

        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("IC-2021.2.2", "IC-2021.1.3", "IC-2020.3.4"),
            result.taskOutput()
        )
    }

    @Test
    fun `get IDEs list for the custom platformType and platformVersion defined in intellij`() {
        buildFile.groovy("""
            intellij {
                type = "PY"
                version = "2021.1"
            }
        """)

        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("PY-2021.1.3"),
            result.taskOutput()
        )
    }

    @Test
    fun `get IDEs list without EAP releases`() {
        buildFile.groovy("""
            import org.jetbrains.intellij.tasks.ListProductsReleasesTask.Channel

            listProductsReleases {
                sinceVersion = "2021.1"
                releaseChannels = EnumSet.of(Channel.RELEASE)
            }
        """)

        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("IC-2021.2.1", "IC-2021.1.3"),
            result.taskOutput()
        )
    }

    @Test
    fun `get IDEs list for the multiple platformTypes`() {
        buildFile.groovy("""
            listProductsReleases {
                sinceVersion = "2021.1"
                types = ["IU", "PS", "PY"]
            }
        """)

        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("IU-2021.2.2", "IU-2021.1.3", "PS-2021.2.2", "PS-2021.1.4", "PY-2021.2.2", "PY-2021.1.3"),
            result.taskOutput()
        )
    }

    @Test
    fun `get Android Studio releases`() {
        buildFile.groovy("""
            listProductsReleases {
                sinceVersion = "2021.1"
                types = ["AI"]
            }
        """)

        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

        assertEquals(
            listOf("AI-2021.2.1", "AI-2021.1.1"),
            result.taskOutput()
        )
    }

    @Test
    fun `reuse configuration cache`() {
        build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }

    private fun BuildResult.taskOutput() = output.lines().run {
        val from = indexOf("> Task :${IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME}") + 1
        val to = indexOfFirst { it.startsWith("BUILD SUCCESSFUL") }
        subList(from, to)
    }
}
