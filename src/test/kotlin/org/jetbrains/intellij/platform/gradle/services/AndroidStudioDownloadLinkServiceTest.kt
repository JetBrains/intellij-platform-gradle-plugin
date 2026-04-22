// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.api.services.BuildServiceParameters
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.newInstance
import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource
import kotlin.test.Test
import kotlin.test.assertEquals

class AndroidStudioDownloadLinkServiceTest {

    @Test
    fun `reuse loaded android studio releases for multiple versions from the same source`() {
        val service = object : AndroidStudioDownloadLinkService() {
            override fun getParameters() = BuildServiceParameters.None::class.java
                .getDeclaredConstructor()
                .apply { isAccessible = true }
                .newInstance()
        }
        val content = checkNotNull(javaClass.classLoader.getResource("products-releases/android-studio-releases-list.xml")).readText()
        var loads = 0
        val objects = ProjectBuilder.builder().build().objects

        val firstParameters = objects.newInstance<AndroidStudioDownloadLinkValueSource.Parameters>().apply {
            androidStudioUrl.set("androidStudio")
            androidStudioVersion.set("2023.3.1.13")
        }
        val secondParameters = objects.newInstance<AndroidStudioDownloadLinkValueSource.Parameters>().apply {
            androidStudioUrl.set("androidStudio")
            androidStudioVersion.set("2023.3.1.12")
        }

        val firstResult = service.resolve(firstParameters) { url ->
            loads++
            assertEquals("androidStudio", url)
            content
        }
        val secondResult = service.resolve(secondParameters) { url ->
            loads++
            assertEquals("androidStudio", url)
            content
        }

        val expectedFirst = with(OperatingSystem.current()) {
            when {
                isMacOsX -> "https://redirector.gvt1.com/edgedl/android/studio/install/2023.3.1.13/android-studio-2023.3.1.13-mac_arm.dmg"
                isLinux -> "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.3.1.13/android-studio-2023.3.1.13-linux.tar.gz"
                isWindows -> "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.3.1.13/android-studio-2023.3.1.13-windows-exe.zip"
                else -> null
            }
        }
        val expectedSecond = with(OperatingSystem.current()) {
            when {
                isMacOsX -> "https://redirector.gvt1.com/edgedl/android/studio/install/2023.3.1.12/android-studio-2023.3.1.12-mac_arm.dmg"
                isLinux -> "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.3.1.12/android-studio-2023.3.1.12-linux.tar.gz"
                isWindows -> "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.3.1.12/android-studio-2023.3.1.12-windows-exe.zip"
                else -> null
            }
        }

        assertEquals(expectedFirst, firstResult)
        assertEquals(expectedSecond, secondResult)
        assertEquals(1, loads)
    }
}
