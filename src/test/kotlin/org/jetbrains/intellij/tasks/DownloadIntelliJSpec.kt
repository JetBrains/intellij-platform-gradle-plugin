package org.jetbrains.intellij.tasks

import org.gradle.api.plugins.BasePlugin
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadIntelliJSpec : IntelliJPluginSpecBase() {

    private val icCacheDir = File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/2021.2.4")
    private val icPomCacheDir = File(icCacheDir, "bef2a5b3f61bf389fbcabf2432529c75e9bfa3f6")
    private val icSourcesCacheDir = File(icCacheDir, "c7b0c46e54134935b76651e9a363273449bfb18c")
    private val icDistCacheDir = File(icCacheDir, "f6a20f715554259dbbbc5aabcba5e2c5f4492cc3")

    private val iuCacheDir = File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIU/2021.2.4")
    private val iuPomCacheDir = File(iuCacheDir, "ce57678957ace9d81c43781d764159807c225e10")
    private val iuDistCacheDir = File(iuCacheDir, "4493a5312869afef74b9cc9f0223b40794762ea4")

    override fun setUp() {
        super.setUp()
        // delete only dist and sources directories, as POM dir is not recreated
        deleteIfExists(icSourcesCacheDir)
        deleteIfExists(icDistCacheDir)
        deleteIfExists(iuDistCacheDir)
    }

    @Test
    fun `download idea dependencies without sources when downloadSources = false`() {
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertDirExistsAndContainsOnly(icCacheDir, icPomCacheDir.name, icDistCacheDir.name)
        assertDirExistsAndContainsOnly(icPomCacheDir, "ideaIC-2021.2.4.pom")
        assertDirExistsAndContainsOnly(icDistCacheDir, "ideaIC-2021.2.4", "ideaIC-2021.2.4.zip")
    }

    @Test
    fun `download idea with sources when downloadSources = true`() {
        buildFile.groovy(
            """
            intellij {
                downloadSources = true
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertDirExistsAndContainsOnly(icCacheDir, icPomCacheDir.name, icDistCacheDir.name, icSourcesCacheDir.name)
        assertDirExistsAndContainsOnly(icPomCacheDir, "ideaIC-2021.2.4.pom")
        assertDirExistsAndContainsOnly(icDistCacheDir, "ideaIC-2021.2.4", "ideaIC-2021.2.4.zip")
        assertDirExistsAndContainsOnly(icSourcesCacheDir, "ideaIC-2021.2.4-sources.jar")
    }

    @Test
    fun `download ultimate idea dependencies without sources when downloadSources = false`() {
        buildFile.groovy(
            """
            intellij {
                type = 'IU'
                version = '2021.2.4'
                downloadSources = false
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertDirExistsAndContainsOnly(iuCacheDir, iuPomCacheDir.name, iuDistCacheDir.name)
        assertDirExistsAndContainsOnly(iuPomCacheDir, "ideaIU-2021.2.4.pom")
        assertDirExistsAndContainsOnly(iuDistCacheDir, "ideaIU-2021.2.4", "ideaIU-2021.2.4.zip")
        assertFalse(icDistCacheDir.exists(), "Expected community dist cache directory to not exist")
        assertFalse(icSourcesCacheDir.exists(), "Expected community sources cache directory to not exist")
    }

    @Test
    fun `download ultimate idea dependencies and community sources without dist when downloadSources = true`() {
        buildFile.groovy(
            """
            intellij {
                type = 'IU'
                version = '2021.2.4'
                downloadSources = true
            }
        """
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertDirExistsAndContainsOnly(iuCacheDir, iuPomCacheDir.name, iuDistCacheDir.name)
        assertDirExistsAndContainsOnly(iuPomCacheDir, "ideaIU-2021.2.4.pom")
        assertDirExistsAndContainsOnly(iuDistCacheDir, "ideaIU-2021.2.4", "ideaIU-2021.2.4.zip")
        assertDirExistsAndContainsOnly(icSourcesCacheDir, "ideaIC-2021.2.4-sources.jar")
        assertFalse(icDistCacheDir.exists(), "Expected community dist cache directory to not exist")
    }

    private fun deleteIfExists(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
            if (dir.exists()) {
                throw IllegalStateException("'${dir.path}' directory should not exist")
            }
        }
    }

    private fun assertDirExistsAndContainsOnly(dir: File, vararg fileNames: String) {
        assertTrue(dir.exists(), "Expected directory '${dir.path}' to exist")
        assertEquals(fileNames.toSet(), dir.list()?.toSet(), "Unexpected '${dir.path}' directory contents")
    }

}
