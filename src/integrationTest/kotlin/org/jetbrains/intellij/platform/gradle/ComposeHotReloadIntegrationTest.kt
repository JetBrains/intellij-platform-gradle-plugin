// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Ignore
import kotlin.test.Test

class ComposeHotReloadIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "compose-hot-reload",
) {
    @Test
    @Ignore("${Tasks.RUN_IDE} task never finishes")
    fun `run IDE with agent enabled`() {
        buildFile write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
                    id("org.jetbrains.kotlin.plugin.compose") version "$kotlinPluginVersion"
                    id("org.jetbrains.intellij.platform")
                }
                
                kotlin {
                    jvmToolchain(21)
                }
                
                repositories {
                    mavenCentral()
                
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                    }
                }
                """.trimIndent()

        buildAndFail(
            Tasks.RUN_IDE,
            args = listOf("--compose-hot-reload"),
            projectProperties = defaultProjectProperties,
        ) {
            assertContains("com.intellij.idea.Main", output)
            assertContains("Timeout has been exceeded", output)
        }
    }
}
