// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductReleaseCatalogEntryTest {

    @Test
    fun `map JetBrains product releases to catalog entries`() {
        val releases = JetBrainsProductReleases(
            code = "IC",
            releases = listOf(
                JetBrainsProductReleases.Release(
                    type = "release",
                    version = "2024.3",
                    build = "243.12818.47",
                ).apply {
                    downloads = mapOf(
                        "linux" to JetBrainsProductReleases.Release.Download(
                            link = "https://download.jetbrains.com/idea/ideaIC-2024.3.tar.gz",
                            size = 123456L,
                            checksumLink = "https://download.jetbrains.com/idea/ideaIC-2024.3.tar.gz.sha256",
                        ),
                    )
                },
            ),
        )

        val entry = releases.toCatalogEntries(IntelliJPlatformType.IntellijIdeaCommunity).single()

        assertEquals(IntelliJPlatformType.IntellijIdeaCommunity, entry.type)
        assertEquals(null, entry.name)
        assertEquals(ProductRelease.Channel.RELEASE, entry.channel)
        assertEquals("2024.3", entry.version)
        assertEquals("243.12818.47", entry.build)
        assertEquals("2024.3", entry.platformVersion)
        assertEquals("243.12818.47", entry.platformBuild)
        assertEquals(
            ProductDownload(
                kind = "linux",
                link = "https://download.jetbrains.com/idea/ideaIC-2024.3.tar.gz",
                checksumLink = "https://download.jetbrains.com/idea/ideaIC-2024.3.tar.gz.sha256",
            ),
            entry.downloads.single(),
        )
    }

    @Test
    fun `map Android Studio releases to catalog entries`() {
        val content = checkNotNull(javaClass.classLoader.getResource("products-releases/android-studio-releases-list.json")).readText()
        val releases = decode<AndroidStudioReleases>(content)

        val entry = releases.toCatalogEntries().first { it.version == "2023.3.1.13" }

        assertEquals(IntelliJPlatformType.AndroidStudio, entry.type)
        assertEquals("Android Studio Jellyfish | 2023.3.1 Canary 13", entry.name)
        assertEquals(ProductRelease.Channel.CANARY, entry.channel)
        assertEquals("2023.3.1.13", entry.version)
        assertEquals("AI-233.14475.28.2331.11543046", entry.build)
        assertEquals("2023.3.4", entry.platformVersion)
        assertEquals("233.14475.28", entry.platformBuild)
        assertEquals(
            ProductDownload(
                kind = null,
                link = "https://redirector.gvt1.com/edgedl/android/studio/install/2023.3.1.13/android-studio-2023.3.1.13-cros.deb",
                checksum = "480fb0f8706458f11b45f75de24e97ad4e0d577ad5e6b74bb2820b1237a9268a",
            ),
            entry.downloads.first(),
        )
    }
}
