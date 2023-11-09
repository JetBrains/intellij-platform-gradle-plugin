package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadAndroidStudioProductReleasesXmlTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `download resource`() {
        val file = buildDirectory
            .resolve("tmp")
            .resolve(Tasks.DOWNLOAD_ANDROID_STUDIO_PRODUCT_RELEASES_XML)
            .resolve("android_studio_product_releases.xml")

        assertFalse(file.exists())

        build(Tasks.DOWNLOAD_ANDROID_STUDIO_PRODUCT_RELEASES_XML)

        assertTrue(file.exists())
    }
}
