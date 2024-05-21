// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertEquals

 class AndroidStudioDownloadLinkValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve the Android Studio link for the specific version and current OS`() {
        buildFile write //language=kotlin
                """
                tasks {
                    val androidStudioDownloadLink = providers.of(org.jetbrains.intellij.platform.gradle.providers.AndroidStudioDownloadLinkValueSource::class) {
                        parameters {
                            androidStudio = file("${resource("products-releases/android-studio-releases-list.xml")}")
                            androidStudioVersion = "2023.3.1.9"
                        }
                    }
                    
                    register("$randomTaskName") {
                        doLast {
                            println("Download Link: " + androidStudioDownloadLink.get())
                        }
                    }
                }
                """.trimIndent()

        val link = with(OperatingSystem.current()) {
            when {
                isMacOsX -> "https://redirector.gvt1.com/edgedl/android/studio/install/2023.3.1.9/android-studio-2023.3.1.9-mac_arm.dmg"
                isLinux -> "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.3.1.9/android-studio-2023.3.1.9-linux.tar.gz"
                isWindows -> "https://redirector.gvt1.com/edgedl/android/studio/ide-zips/2023.3.1.9/android-studio-2023.3.1.9-windows-exe.zip"
                else -> null
            }
        }

        build(randomTaskName) {
            assertLogValue("Download Link: ") {
                assertEquals(link, it)
            }
        }
    }
}
