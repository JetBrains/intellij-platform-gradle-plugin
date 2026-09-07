// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentPluginVersionValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve the default plugin version when run from tests`() {
        buildFile write //language=kotlin
                """
                tasks {
                    val currentPluginVersion = providers.of(org.jetbrains.intellij.platform.gradle.providers.CurrentPluginVersionValueSource::class) {
                    }
                    
                    register("$randomTaskName") {
                        doLast {
                            println("Version: " + currentPluginVersion.get())
                        }
                    }
                }
                """.trimIndent()

        build(randomTaskName) {
            assertLogValue("Version: ") {
                assertEquals("0.0.0", it)
            }
        }
    }
}
