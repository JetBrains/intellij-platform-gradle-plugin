// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.plugins.BasePlugin
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadIntelliJSpec : IntelliJPluginSpecBase() {

    private val icCacheDir = File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC")
    private val iuCacheDir = File(gradleHome, "caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIU")

    /*
      Use unique IDEs versions which are not used in other tests to be sure that they are actually downloaded
      and don't exist because other tests downloaded them. Using a single version and deleting directories before tests
      causes issues on Windows GitHub Actions runners (file is locked by another process).
     */

    @Test
    fun `download idea dependencies without sources when downloadSources = false`() {
        val testedVersion = "2019.3.1"
        val icVersionCacheDir = File(icCacheDir, testedVersion)
        val icPomCacheDir = File(icVersionCacheDir, "6c69524caec42364796bb99471fa4057c4daf567")
        val icDistCacheDir = File(icVersionCacheDir, "52292e4f8a0ccb3ceb08bd81fd57b88923ac8e99")
        val iuVersionCacheDir = File(iuCacheDir, testedVersion)
        deleteIfExists(icDistCacheDir)

        buildFile.groovy(
            """
            intellij {
                type = 'IC'
                version = '$testedVersion'
                downloadSources = false
            }"""
        )
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertDirExistsAndContainsOnly(icVersionCacheDir, icPomCacheDir.name, icDistCacheDir.name)
        assertDirExistsAndContainsOnly(icPomCacheDir, "ideaIC-2019.3.1.pom")
        assertDirExistsAndContainsOnly(icDistCacheDir, "ideaIC-2019.3.1", "ideaIC-2019.3.1.zip")
        assertFalse(iuVersionCacheDir.exists(), "Expected Ultimate cache directory to not exist")
    }

    @Test
    fun `download idea with sources when downloadSources = true`() {
        val testedVersion = "2019.3.2"
        val icVersionCacheDir = File(icCacheDir, testedVersion)
        val icPomCacheDir = File(icVersionCacheDir, "a436b9e0ec0f51f21317eb3d9d839112f9c32223")
        val icDistCacheDir = File(icVersionCacheDir, "b911671d7501d9fd63cea3eee8287d8ceb4113c6")
        val icSourcesCacheDir = File(icVersionCacheDir, "db705bd2f3911c2e35b53c9084ed96c3c0b7e900")
        val iuVersionCacheDir = File(iuCacheDir, testedVersion)
        deleteIfExists(icDistCacheDir)
        deleteIfExists(icSourcesCacheDir)

        buildFile.groovy(
            """
            intellij {
                type = 'IC'
                version = '$testedVersion'
                downloadSources = true
            }"""
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertDirExistsAndContainsOnly(icVersionCacheDir, icPomCacheDir.name, icDistCacheDir.name, icSourcesCacheDir.name)
        assertDirExistsAndContainsOnly(icPomCacheDir, "ideaIC-2019.3.2.pom")
        assertDirExistsAndContainsOnly(icDistCacheDir, "ideaIC-2019.3.2", "ideaIC-2019.3.2.zip")
        assertDirExistsAndContainsOnly(icSourcesCacheDir, "ideaIC-2019.3.2-sources.jar")
        assertFalse(iuVersionCacheDir.exists(), "Expected Ultimate cache directory to not exist")
    }

    @Test
    fun `download ultimate idea dependencies without sources when downloadSources = false`() {
        val testedVersion = "2019.3.3"
        val iuVersionCacheDir = File(iuCacheDir, testedVersion)
        val iuPomCacheDir = File(iuVersionCacheDir, "9d81b0a2416de8815bbe266ffc4065a2e4a2fda0")
        val iuDistCacheDir = File(iuVersionCacheDir, "9bf72a4b290a00d569759db11454eb3347026fc7")
        val icVersionCacheDir = File(icCacheDir, testedVersion)
        deleteIfExists(iuDistCacheDir)

        buildFile.groovy(
            """
            intellij {
                type = 'IU'
                version = '$testedVersion'
                downloadSources = false
            }"""
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertDirExistsAndContainsOnly(iuVersionCacheDir, iuPomCacheDir.name, iuDistCacheDir.name)
        assertDirExistsAndContainsOnly(iuPomCacheDir, "ideaIU-2019.3.3.pom")
        assertDirExistsAndContainsOnly(iuDistCacheDir, "ideaIU-2019.3.3", "ideaIU-2019.3.3.zip")
        assertFalse(icVersionCacheDir.exists(), "Expected Community cache directory to not exist")
    }

    @Test
    fun `download ultimate idea dependencies and community sources without dist when downloadSources = true`() {
        val testedVersion = "2019.3.4"
        val iuVersionCacheDir = File(iuCacheDir, testedVersion)
        val iuPomCacheDir = File(iuVersionCacheDir, "a5798bbf342eda50bbedfac9522f3d6ca18e5801")
        val iuDistCacheDir = File(iuVersionCacheDir, "af9f7c58d349e438353801a36e629a1672b92fc")
        val icVersionCacheDir = File(icCacheDir, testedVersion)
        val icPomCacheDir = File(icVersionCacheDir, "55441dcc36ee231de051150455a395f775dac552")
        val icSourcesCacheDir = File(icVersionCacheDir, "11facb5d520efd78d3145de5fa01449179465e5")
        deleteIfExists(iuDistCacheDir)
        deleteIfExists(icSourcesCacheDir)

        buildFile.groovy(
            """
            intellij {
                type = 'IU'
                version = '$testedVersion'
                downloadSources = true
            }"""
        )

        build(BasePlugin.ASSEMBLE_TASK_NAME)

        assertDirExistsAndContainsOnly(iuVersionCacheDir, iuPomCacheDir.name, iuDistCacheDir.name)
        assertDirExistsAndContainsOnly(iuPomCacheDir, "ideaIU-2019.3.4.pom")
        assertDirExistsAndContainsOnly(iuDistCacheDir, "ideaIU-2019.3.4", "ideaIU-2019.3.4.zip")
        assertDirExistsAndContainsOnly(icVersionCacheDir, icPomCacheDir.name, icSourcesCacheDir.name)
        assertDirExistsAndContainsOnly(icPomCacheDir, "ideaIC-2019.3.4.pom")
        assertDirExistsAndContainsOnly(icSourcesCacheDir, "ideaIC-2019.3.4-sources.jar")
    }

    private fun deleteIfExists(dir: File) {
        if (dir.exists()) {
            dir.deleteRecursively()
        }
    }

    private fun assertDirExistsAndContainsOnly(dir: File, vararg fileNames: String) {
        assertTrue(dir.exists(), "Expected directory '${dir.path}' to exist")
        assertEquals(fileNames.toSet(), dir.list()?.toSet(), "Unexpected '${dir.path}' directory contents")
    }
}
