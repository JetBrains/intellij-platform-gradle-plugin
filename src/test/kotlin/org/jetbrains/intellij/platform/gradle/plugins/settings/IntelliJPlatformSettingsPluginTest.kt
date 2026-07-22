// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.settings

import org.jetbrains.intellij.platform.gradle.*
import kotlin.test.BeforeTest
import kotlin.test.Test

class IntelliJPlatformSettingsPluginTest : IntelliJPlatformTestBase() {

    @BeforeTest
    override fun setup() {
        super.setup()

        settingsFile overwrite //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.settings")
                }

                rootProject.name = "projectName"
                """.trimIndent()

        buildFile overwrite //language=kotlin
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm")
                }

                repositories {
                    mavenCentral()
                }
                """.trimIndent()
    }

    @Test
    fun `disable the default Kotlin stdlib dependency`() {
        build("dependencies", args = listOf("--configuration", "runtimeClasspath")) {
            assertNotContains("org.jetbrains.kotlin:kotlin-stdlib", output)
        }
    }

    @Test
    fun `respect an explicit Kotlin stdlib dependency property`() {
        gradleProperties overwrite "$KOTLIN_STDLIB_DEFAULT_DEPENDENCY=true"

        build("dependencies", args = listOf("--configuration", "runtimeClasspath")) {
            assertContains("org.jetbrains.kotlin:kotlin-stdlib", output)
        }
    }

    private companion object {
        const val KOTLIN_STDLIB_DEFAULT_DEPENDENCY = "kotlin.stdlib.default.dependency"
    }
}
