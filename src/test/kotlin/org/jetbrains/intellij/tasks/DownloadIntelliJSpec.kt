package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.listFiles
import com.jetbrains.plugin.structure.base.utils.simpleName
import org.gradle.api.plugins.BasePlugin
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class DownloadIntelliJSpec : IntelliJPluginSpecBase() {

    @Test
    fun `download idea dependencies`() {
        val cacheDir = gradleHome.resolve("caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2020.1")

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertEquals(
            listOf(
                "cbeeb1f1aebd4c9ea8fb5ab990c5904a676fc41a",
                "116a3a8911c3a4bd49b2cb23f9576d13eaa721df",
            ),
            cacheDir.listFiles().map(Path::simpleName)
        )
        println("cacheDir='${cacheDir}'")
        assertEquals(
            listOf("ideaIC-2020.1.pom"),
            cacheDir.resolve("116a3a8911c3a4bd49b2cb23f9576d13eaa721df").listFiles().map(Path::simpleName)
        )
        assertEquals(
            listOf("ideaIC-2020.1", "ideaIC-2020.1.zip"),
            cacheDir.resolve("cbeeb1f1aebd4c9ea8fb5ab990c5904a676fc41a").listFiles().map(Path::simpleName)
        )
    }

    @Test
    fun `download sources if option is enabled`() {
        val cacheDir = gradleHome.resolve("caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2020.1")

        buildFile.groovy(
            """
            intellij {
                downloadSources = true
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertEquals(
            listOf(
                "cbeeb1f1aebd4c9ea8fb5ab990c5904a676fc41a",
                "116a3a8911c3a4bd49b2cb23f9576d13eaa721df",
                "6becac80419981b057df9cf0c62efcd94e6075a8",
            ),
            cacheDir.listFiles().map(Path::simpleName)
        )
        assertEquals(
            listOf("ideaIC-2020.1.pom"),
            cacheDir.resolve("116a3a8911c3a4bd49b2cb23f9576d13eaa721df").listFiles().map(Path::simpleName),
        )
        assertEquals(
            listOf("ideaIC-2020.1", "ideaIC-2020.1.zip"),
            cacheDir.resolve("cbeeb1f1aebd4c9ea8fb5ab990c5904a676fc41a").listFiles().map(Path::simpleName),
        )
        assertEquals(
            listOf("ideaIC-2020.1-sources.jar"),
            cacheDir.resolve("6becac80419981b057df9cf0c62efcd94e6075a8").listFiles().map(Path::simpleName),
        )
    }

    @Test
    fun `download ultimate idea dependencies`() {
        val cacheDir = gradleHome.resolve("caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIU/14.1.4")
        val ideaCommunityCacheDir = gradleHome.resolve("caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.4")

        buildFile.groovy(
            """
            intellij {
                version = 'IU-14.1.4'
                downloadSources = true
            }
        """
        )
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertEquals(
            listOf("f8eb5ad49abba6374eeec643cecf20f7268cbfee", "b8993c44c83fe4a39dbb6b72ab6d87a117769534"),
            cacheDir.listFiles().map(Path::simpleName),
        )
        assertEquals(
            listOf("ideaIU-14.1.4.pom"),
            cacheDir.resolve("b8993c44c83fe4a39dbb6b72ab6d87a117769534").listFiles().map(Path::simpleName),
        )
        assertEquals(
            listOf("ideaIU-14.1.4", "ideaIU-14.1.4.zip"),
            cacheDir.resolve("f8eb5ad49abba6374eeec643cecf20f7268cbfee").listFiles().map(Path::simpleName),
        )

        // do not download ideaIC dist
        assertEquals(
            listOf("f8eb5ad49abba6374eeec643cecf20f7268cbfee", "b8993c44c83fe4a39dbb6b72ab6d87a117769534"),
            cacheDir.listFiles().map(Path::simpleName)
        )
        assertEquals(
            listOf("ideaIC-14.1.4.pom"),
            ideaCommunityCacheDir.resolve("87ce88382f970b94fc641304e0a80af1d70bfba7").listFiles().map(Path::simpleName),
        )
        assertEquals(
            listOf("ideaIC-14.1.4-sources.jar"),
            ideaCommunityCacheDir.resolve("f5169c4a780da12ca4eec17553de9f6d43a49d52").listFiles().map(Path::simpleName),
        )
    }
}
