// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.*
import kotlin.test.AfterTest
import kotlin.test.BeforeTest

@OptIn(ExperimentalPathApi::class)
open class IntelliJPlatformIntegrationTestBase(
    private val resourceName: String? = null,
    protected val useCache: Boolean = false,
) : IntelliJPlatformTestBase() {
    protected open val reuseProjectState = true
    protected val sandboxDirectory: Path
        get() = intellijPlatformCacheDir.resolve(Sandbox.CONTAINER)

    protected open val defaultProjectProperties: Map<String, Any> = mapOf(
        "intellijPlatform.version" to intellijPlatformVersion,
        "intellijPlatform.type" to intellijPlatformType,
        GradleProperties.SelfUpdateCheck.toString() to false,
        GradleProperties.IntellijPlatformCache.toString() to intellijPlatformCacheDir.invariantSeparatorsPathString,
    )

    @BeforeTest
    override fun setup() {
        super.setup()

        if (reuseProjectState) {
            val initialDir = dir
            dir = reusableProjectDirectory()
            if (initialDir != dir) {
                initialDir.deleteRecursively()
            }
            pruneReusableProjectDirectory(dir, failOnFailure = true)
        }

        if (resourceName != null) {
            use(resourceName)
        }

        if (useCache) {
            buildFile.useCache()
        }
    }

    @AfterTest
    override fun tearDown() {
        if (reuseProjectState) {
            // The reused per-class project directory (gradleHome/.integrationTestProjects/<class>) is intentionally
            // kept across a class's test methods for speed, but it is never deleted by super.tearDown(), so every
            // integration test class otherwise leaves its build outputs behind for the whole suite run. Prune the
            // transient content here (keeping the project-local `.gradle` cache for reuse) so these directories stop
            // accumulating across the suite. The deletion is tolerant and retrying: a file still locked by a
            // background Gradle process on Windows must not fail the test.
            pruneReusableProjectDirectory(dir, failOnFailure = false)
        } else {
            super.tearDown()
        }
    }

    protected fun use(resourceName: String) {
        val resourcePath = Path("src", "integrationTest", "resources", resourceName)

        if (resourcePath.notExists()) {
            throw IllegalArgumentException("Integration tests resource '$resourceName' not found in: $resourcePath")
        }

        resourcePath.copyToRecursively(
            target = dir,
            followLinks = true,
            overwrite = true,
        )
    }

    infix fun String.matchesRegex(regex: String) {
        matchesRegex(regex.toRegex())
    }

    infix fun String.matchesRegex(regex: Regex) {
        assert(regex.containsMatchIn(this)) { "expect '$this' matches '$regex'" }
    }

    infix fun Path.containsFile(path: String) {
        assert(resolve(path).exists()) { "expect '$this' contains file '$path'" }
    }

    infix fun Path.notContainsFile(path: String) {
        assert(resolve(path).notExists()) { "expect '$this' not contains file '$path'" }
    }

    infix fun Path.containsFileInArchive(path: String) {
        FileSystems.newFileSystem(this, null as ClassLoader?).use { fs ->
            assert(fs.getPath(path).exists()) { "expect archive '$this' contains file '$path'" }
        }
    }

    infix fun Path.readEntry(path: String) = ZipFile(pathString).use { zip ->
        val entry = zip.getEntry(path)
        zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }

    /**
     * Configures caching for IntelliJ Platform integration tests within the provided file path context.
     *
     * Usage of this method is intended to prepare the test environment for scenarios explicitly covering IDE cache
     * behavior. Most integration tests should rely on the shared TestKit Gradle home instead, which already reuses
     * downloaded and transformed IntelliJ Platform artifacts without paying the extra local-IDE indirection cost.
     *
     * The changes applied by this method are written directly to the file represented by the receiver [Path].
     */
    protected fun Path.useCache() {
        this write //language=kotlin
                """
                intellijPlatform {
                    caching {
                        ides {
                            enabled = true
                            path = File("${idesCacheDir.invariantSeparatorsPathString}")
                        }
                    }
                }
                """.trimIndent()
    }

    private fun reusableProjectDirectory() =
        gradleHome
            .resolve(".integrationTestProjects")
            .resolve(
                listOfNotNull(
                    this::class.qualifiedName,
                    resourceName,
                ).joinToString("_")
                    .replace(Regex("[^A-Za-z0-9._-]"), "_")
            )
            .createDirectories()

}

/**
 * Removes transient content from a reusable integration-test project while retaining its project-local `.gradle`
 * cache. Setup uses strict mode because stale files would contaminate the next test; teardown uses best-effort mode so
 * a briefly locked Windows file does not turn a successful test into a cleanup failure.
 */
@OptIn(ExperimentalPathApi::class)
internal fun pruneReusableProjectDirectory(
    projectDirectory: Path,
    failOnFailure: Boolean,
    maxAttempts: Int = if (System.getProperty("os.name").startsWith("Windows")) 20 else 5,
    retryDelayMs: Long = if (System.getProperty("os.name").startsWith("Windows")) 250L else 100L,
    deleteEntry: (Path) -> Unit = { it.deleteRecursively() },
) {
    if (projectDirectory.notExists()) {
        return
    }

    projectDirectory.listDirectoryEntries()
        .filter { it.name != ".gradle" }
        .forEach { entry ->
            repeat(maxAttempts) { attempt ->
                try {
                    deleteEntry(entry)
                    return@forEach
                } catch (exception: IOException) {
                    if (attempt == maxAttempts - 1) {
                        if (failOnFailure) {
                            throw exception
                        }
                        System.err.println("Failed to prune '$entry' after $maxAttempts attempts: ${exception.message}")
                        return@forEach
                    }
                    if (retryDelayMs > 0) {
                        Thread.sleep(retryDelayMs)
                    }
                }
            }
        }
}
