// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import java.nio.file.FileSystems
import java.nio.file.Path
import java.util.zip.ZipFile
import kotlin.io.path.*
import kotlin.test.BeforeTest

@OptIn(ExperimentalPathApi::class)
open class IntelliJPlatformIntegrationTestBase(
    private val resourceName: String? = null,
) : IntelliJPlatformTestBase() {

    protected open val defaultProjectProperties: Map<String, Any> = mapOf(
        "intellijPlatform.version" to intellijPlatformVersion,
        "intellijPlatform.type" to intellijPlatformType,
    )

    @BeforeTest
    override fun setup() {
        super.setup()

        if (resourceName != null) {
            use(resourceName)
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
        val fs = FileSystems.newFileSystem(this, null as ClassLoader?)
        assert(fs.getPath(path).exists()) { "expect archive '$this' contains file '$path'" }
    }

    infix fun Path.readEntry(path: String) = ZipFile(pathString).use { zip ->
        val entry = zip.getEntry(path)
        zip.getInputStream(entry).bufferedReader().use { it.readText() }
    }
}
