// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.artifacts.transform

import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class ExtractorTransformerRuntimeTest : IntelliJPlatformTestBase() {

    @Test
    fun `keep the Windows JVM library path below MAX_PATH in nightly TestKit caches`() {
        val runtime = "jbr_jcef-25.0.4.1-windows-x64-b635.75"
        val archive = dir.resolve("jbr-$runtime.zip")
        ZipOutputStream(archive.outputStream()).use { zip ->
            listOf("bin/java.exe", "bin/server/jvm.dll", "lib/modules", "release").forEach { entry ->
                val path = "$runtime/$entry"
                zip.putNextEntry(ZipEntry(path))
                zip.write(path.toByteArray())
                zip.closeEntry()
            }
        }

        buildFile write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform")
                }

                dependencies {
                    add("jetbrainsRuntimeDependency", files("${archive.fileName}"))
                }

                val runtime = configurations.named("jetbrainsRuntime")
                tasks.register("resolveRuntime") {
                    inputs.files(runtime)
                    doLast {
                        val directory = runtime.get().singleFile
                        check(directory.resolve("bin/java.exe").isFile)
                        check(directory.resolve("bin/server/jvm.dll").readText() == "$runtime/bin/server/jvm.dll")
                        check(directory.resolve("lib/modules").isFile)
                        check(directory.resolve("release").isFile)
                        println("RUNTIME_DIRECTORY=" + directory.name)
                    }
                }
                """.trimIndent()

        build("resolveRuntime") {
            val directory = output.lineSequence().single { it.startsWith("RUNTIME_DIRECTORY=") }.substringAfter('=')
            val windowsTransformPath = "D:/a/intellij-platform-gradle-plugin/intellij-platform-gradle-plugin/" +
                    ".gradle/testGradleHome/.testKit/worker-1/caches/9.10.0-20261102002359+0000/" +
                    "transforms/${"0".repeat(32)}/transformed"
            val libraryPath = "$windowsTransformPath/$directory/bin/server/jvm.dll"

            assertTrue(
                libraryPath.length < 260,
                "Windows JVM library path exceeds MAX_PATH: ${libraryPath.length} characters: $libraryPath",
            )
        }
    }
}