// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Ignore
import kotlin.test.Test

class IntelliJPlatformDependenciesExtensionTest : IntelliJPluginTestBase() {

    @Test
    @Ignore("When using cache, this warning is not emitted.")
    fun `warn when using Rider with useInstaller true`() {
        gradleProperties write //language=properties
                """
                intellijPlatform.type=RD
                """.trimIndent()

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertContains("Using Rider as a target IntelliJ Platform with `useInstaller = true` is currently not supported, please set `useInstaller = false` instead.", output)
        }
    }

    @Test
    fun `do not warn when using Rider with useInstaller false`() {
        gradleProperties write //language=properties
                """
                intellijPlatform.type=RD
                intellijPlatform.useInstaller=false
                """.trimIndent()

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertNotContains("Using Rider as a target IntelliJ Platform with `useInstaller = true` is currently not supported, please set `useInstaller = false` instead.", output)
        }
    }
}
