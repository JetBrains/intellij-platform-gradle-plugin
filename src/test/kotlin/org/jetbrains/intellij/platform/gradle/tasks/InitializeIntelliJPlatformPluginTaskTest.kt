// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.CACHE_DIRECTORY
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.resolveLatestVersion
import java.time.LocalDate
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertFalse

class InitializeIntelliJPlatformPluginTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `report outdated plugin`() {
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.buildFeature.selfUpdateCheck = true
                """.trimIndent()

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            val lockFile = dir.resolve(CACHE_DIRECTORY).resolve("self-update.lock")
            assertExists(lockFile)
            lockFile containsText LocalDate.now().toString()

            val latestVersion = Coordinates("org.jetbrains.intellij.platform", "intellij-platform-gradle-plugin").resolveLatestVersion(Locations.MAVEN_GRADLE_PLUGIN_PORTAL_REPOSITORY)

            assertContains("${Plugin.NAME} is outdated: 0.0.0. Update `${Plugin.ID}` to: $latestVersion", output)
        }
    }

    @Test
    fun `skip version check when offline`() {
        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN, "--offline") {
            assertNotContains("${Plugin.NAME} is outdated: 0.0.0.", output)

//            val type = IntelliJPlatformType.fromCode(intellijPlatformType)
//            val groupId = type.maven?.groupId
//            val artifactId = type.maven?.artifactId
//
//            val dependency = "${groupId}:${artifactId}:$intellijPlatformVersion"
//            assertContains("No cached version of $dependency available for offline mode.", output)
        }
    }

    @Test
    fun `skip version check is disabled with BuildFeature`() {
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.buildFeature.selfUpdateCheck=false
                """.trimIndent()

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertNotContains("${Plugin.NAME} is outdated: 0.0.0.", output)
        }
    }

    @Test
    fun `skip version check is disabled with existing lock file`() {
        val file = dir.resolve("lockFile").createFile()

        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN} {
                        selfUpdateLock = file("${file.name}")
                    }
                }
                """.trimIndent()

        assertExists(file)

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            assertNotContains("${Plugin.NAME} is outdated: 0.0.0.", output)
        }
    }

    @Test
    fun `creates coroutines-javaagent file`() {
        val file = dir.resolve("coroutines-javaagent.jar")

        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN} {
                        coroutinesJavaAgent = file("${file.name}")
                    }
                }
                """.trimIndent()

        assertFalse(file.exists())

        build(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

        assertExists(file)
    }
}
