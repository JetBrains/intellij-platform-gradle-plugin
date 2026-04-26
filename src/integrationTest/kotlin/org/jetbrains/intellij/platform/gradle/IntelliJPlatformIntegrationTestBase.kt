// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

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
            prepareReusableProjectDirectory()
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
        if (!reuseProjectState) {
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

    private fun prepareReusableProjectDirectory() {
        dir.listDirectoryEntries().forEach {
            if (it.name != ".gradle") {
                it.deleteRecursively()
            }
        }
    }
}
