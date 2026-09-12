// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createDirectory
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalPathApi::class)
class ReusableProjectDirectoryPrunerTest {

    @Test
    fun `retains only the project Gradle cache`() {
        val projectDirectory = createTempDirectory("reusable-project")
        projectDirectory.resolve(".gradle").createDirectory()
        projectDirectory.resolve("build").createDirectory().resolve("output.txt").writeText("output")
        projectDirectory.resolve("settings.gradle.kts").writeText("")

        pruneReusableProjectDirectory(projectDirectory, failOnFailure = true, retryDelayMs = 0)

        assertTrue(projectDirectory.resolve(".gradle").exists())
        assertFalse(projectDirectory.resolve("build").exists())
        assertFalse(projectDirectory.resolve("settings.gradle.kts").exists())
    }

    @Test
    fun `strict pruning retries before succeeding`() {
        val projectDirectory = createTempDirectory("reusable-project")
        projectDirectory.resolve("build").createDirectory()
        val attempts = AtomicInteger()

        pruneReusableProjectDirectory(
            projectDirectory = projectDirectory,
            failOnFailure = true,
            maxAttempts = 3,
            retryDelayMs = 0,
        ) { entry ->
            if (attempts.incrementAndGet() < 3) {
                throw IOException("locked")
            }
            entry.deleteRecursively()
        }

        assertEquals(3, attempts.get())
        assertFalse(projectDirectory.resolve("build").exists())
    }

    @Test
    fun `strict pruning fails instead of reusing stale content`() {
        val projectDirectory = createTempDirectory("reusable-project")
        projectDirectory.resolve("build").createDirectory()

        assertFailsWith<IOException> {
            pruneReusableProjectDirectory(
                projectDirectory = projectDirectory,
                failOnFailure = true,
                maxAttempts = 2,
                retryDelayMs = 0,
            ) { throw IOException("locked") }
        }

        assertTrue(projectDirectory.resolve("build").exists())
    }

    @Test
    fun `best effort pruning tolerates a persistently locked entry`() {
        val projectDirectory = createTempDirectory("reusable-project")
        projectDirectory.resolve("build").createDirectory()
        val attempts = AtomicInteger()

        pruneReusableProjectDirectory(
            projectDirectory = projectDirectory,
            failOnFailure = false,
            maxAttempts = 2,
            retryDelayMs = 0,
        ) {
            attempts.incrementAndGet()
            throw IOException("locked")
        }

        assertEquals(2, attempts.get())
        assertTrue(projectDirectory.resolve("build").exists())
    }
}
