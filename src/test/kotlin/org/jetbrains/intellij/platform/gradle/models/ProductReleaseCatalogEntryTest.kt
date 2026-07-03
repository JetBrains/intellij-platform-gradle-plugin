// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.models

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType
import org.jetbrains.intellij.platform.gradle.services.toProductReleases
import org.jetbrains.intellij.platform.gradle.utils.toVersion
import kotlin.test.Test
import kotlin.test.assertEquals

class ProductReleaseCatalogEntryTest {

    @Test
    fun `resolve Android Studio artifact with named release classifier`() {
        assertEquals(
            ProductRelease.Download.Artifact(
                downloadLinkVersion = "2026.1.3.2",
                classifier = "quail3-canary2-mac_arm",
                extension = "dmg",
            ),
            artifact(
                IntelliJPlatformType.AndroidStudio,
                "https://edgedl.me.gvt1.com/android/studio/ide-zips/2026.1.3.2/android-studio-quail3-canary2-mac_arm.dmg",
            ),
        )
    }

    @Test
    fun `resolve installer artifacts from download links`() {
        assertEquals(
            ProductRelease.Download.Artifact("2026.1.3", "aarch64", "dmg"),
            artifact(
                IntelliJPlatformType.IntellijIdea,
                "https://download.jetbrains.com/idea/idea-2026.1.3-aarch64.dmg",
            ),
        )
        assertEquals(
            ProductRelease.Download.Artifact("2026.1.3", "win", "zip"),
            artifact(
                IntelliJPlatformType.IntellijIdea,
                "https://download.jetbrains.com/idea/idea-2026.1.3.win.zip",
            ),
        )
        assertEquals(
            ProductRelease.Download.Artifact("2026.2-EAP8-262.8377.34.Checked", "win", "zip"),
            artifact(
                IntelliJPlatformType.IntellijIdea,
                "https://download.jetbrains.com/rider/JetBrains.Rider-2026.2-EAP8-262.8377.34.Checked.win.zip",
            ),
        )
        assertEquals(
            ProductRelease.Download.Artifact("261.25134.95", "jbr.win", "zip"),
            artifact(
                IntelliJPlatformType.IntellijIdea,
                "https://download.jetbrains.com/idea/code-with-me/JetBrainsClient-261.25134.95.jbr.win.zip",
            ),
        )
    }

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

        val entry = releases.toProductReleases().single()

        assertEquals(IntelliJPlatformType.IntellijIdeaCommunity, entry.type)
        assertEquals("...", entry.name)
        assertEquals(ProductRelease.Channel.RELEASE, entry.channel)
        assertEquals("2024.3".toVersion(), entry.version)
        assertEquals("243.12818.47".toVersion(), entry.build)
        assertEquals("2024.3".toVersion(), entry.platformVersion)
        assertEquals("243.12818.47".toVersion(), entry.platformBuild)
        assertEquals(
            ProductRelease.Download(
                kind = "linux",
                link = "https://download.jetbrains.com/idea/ideaIC-2024.3.tar.gz",
                checksumLink = "https://download.jetbrains.com/idea/ideaIC-2024.3.tar.gz.sha256",
            ),
            entry.downloads.single(),
        )
    }

    @Test
    fun `map JetBrains products catalog codes to platform codes`() {
        fun release(version: String, build: String) = JetBrainsProductReleases.Release(
            type = "release",
            version = version,
            build = build,
        )

        val entries = listOf(
            JetBrainsProductReleases(
                code = "IIC",
                releases = listOf(release("2024.3", "243.12818.47")),
            ),
            JetBrainsProductReleases(
                code = "IIU",
                releases = listOf(release("2026.1", "261.25134.95")),
            ),
            JetBrainsProductReleases(
                code = "PCC",
                releases = listOf(release("2024.3", "243.21565.199")),
            ),
            JetBrainsProductReleases(
                code = "PCP",
                releases = listOf(release("2026.1", "261.25134.203")),
            ),
        ).flatMap { it.toProductReleases() }

        assertEquals(
            listOf("IC", "IU", "PC", "PY"),
            entries.map { it.type.code },
        )
    }

    @Test
    fun `map Android Studio releases to catalog entries`() {
        val content =
            checkNotNull(javaClass.classLoader.getResource("products-releases/android-studio-releases-list.json")).readText()
        val releases = decode<AndroidStudioReleases>(content)

        val entry = releases.toProductReleases().first { it.version == "2023.3.1.13".toVersion() }

        assertEquals(IntelliJPlatformType.AndroidStudio, entry.type)
        assertEquals("Android Studio Jellyfish | 2023.3.1 Canary 13", entry.name)
        assertEquals(ProductRelease.Channel.CANARY, entry.channel)
        assertEquals("2023.3.1.13".toVersion(), entry.version)
        assertEquals("AI-233.14475.28.2331.11543046".toVersion(), entry.build)
        assertEquals("2023.3.4".toVersion(), entry.platformVersion)
        assertEquals("233.14475.28".toVersion(), entry.platformBuild)
        assertEquals(
            ProductRelease.Download(
                kind = null,
                link = "https://redirector.gvt1.com/edgedl/android/studio/install/2023.3.1.13/android-studio-2023.3.1.13-cros.deb",
                checksum = "480fb0f8706458f11b45f75de24e97ad4e0d577ad5e6b74bb2820b1237a9268a",
            ),
            entry.downloads.first(),
        )
    }

    private fun artifact(type: IntelliJPlatformType, link: String) = ProductRelease(
        type = type,
        name = type.name,
        channel = ProductRelease.Channel.RELEASE,
        version = "2026.1.3.2".toVersion(),
        build = "261".toVersion(),
        downloads = listOf(
            ProductRelease.Download(
                kind = null,
                link = link,
            ),
        ),
    ).resolveDownloadArtifact()
}
