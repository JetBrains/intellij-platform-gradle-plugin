// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.testfixtures.ProjectBuilder
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeDirectory
import org.jetbrains.intellij.platform.gradle.resolvers.path.resolveJavaRuntimeExecutable
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.exists
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ExtractorServiceTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `remove the nested Windows runtime root before resolving java executable`() {
        val runtime = "jbr_jcef-25.0.4.1-windows-x64-b635.75"
        val target = extract(
            "$runtime/bin/java.exe",
            "$runtime/lib/modules",
            "$runtime/release",
        )

        assertEquals(target.resolve("bin/java.exe"), target.resolveJavaRuntimeExecutable())
        assertEquals("$runtime/bin/java.exe", target.resolve("bin/java.exe").readText())
        assertTrue(target.resolve("lib/modules").exists())
        assertTrue(target.resolve("release").exists())
        assertFalse(target.resolve(runtime).exists())
    }

    @Test
    fun `remove the nested Linux runtime root`() {
        val runtime = "jbr_jcef-25.0.4.1-linux-x64-b635.75"
        val target = extract("$runtime/bin/java", "$runtime/lib/modules", "$runtime/release")

        assertEquals(target.resolve("bin/java"), target.resolveJavaRuntimeExecutable())
        assertTrue(target.resolve("lib/modules").exists())
        assertFalse(target.resolve(runtime).exists())
    }

    @Test
    fun `remove macOS runtime wrappers while preserving bundle metadata`() {
        val runtime = "jbr_jcef-25.0.4.1-osx-aarch64-b635.75"
        val target = extract(
            "$runtime/Contents/Home/bin/java",
            "$runtime/Contents/Home/lib/modules",
            "$runtime/Contents/Info.plist",
        )

        val home = assertNotNull(target.resolveJavaRuntimeDirectory())
        assertEquals(target.resolve("Home/bin/java"), home.resolveJavaRuntimeExecutable())
        assertTrue(target.resolve("Info.plist").exists())
        assertTrue(target.resolve("Home/lib/modules").exists())
        assertFalse(target.resolve(runtime).exists())
    }

    @Test
    fun `preserve an already flat runtime`() {
        val target = extract("bin/java.exe", "lib/modules", "release")

        assertEquals(target.resolve("bin/java.exe"), target.resolveJavaRuntimeExecutable())
        assertTrue(target.resolve("lib/modules").exists())
        assertTrue(target.resolve("release").exists())
    }

    @Test
    fun `preserve the bundled runtime when flattening an IDE`() {
        val target = extract(
            "idea/product-info.json",
            "idea/bin/idea.bat",
            "idea/lib/platform.jar",
            "idea/jbr/bin/java.exe",
            "idea/jbr/lib/modules",
        )

        assertTrue(target.resolve("product-info.json").exists())
        assertTrue(target.resolve("bin/idea.bat").exists())
        assertTrue(target.resolve("lib/platform.jar").exists())
        assertTrue(target.resolve("jbr/bin/java.exe").exists())
        assertTrue(target.resolve(Constants.TOOLBOX_IGNORE).exists())
    }

    @Test
    fun `preserve a plugin wrapper containing only lib`() {
        val target = extract("plugin/lib/plugin.jar")

        assertTrue(target.resolve("plugin/lib/plugin.jar").exists())
        assertFalse(target.resolve("lib").exists())
    }

    private fun extract(vararg entries: String): Path {
        val archive = temporaryFolder.newFile("runtime.tar.gz").toPath()
        GZIPOutputStream(archive.outputStream()).use { gzip ->
            entries.forEach { name ->
                val content = name.toByteArray()
                val header = ByteArray(512)
                fun write(offset: Int, length: Int, value: String) {
                    value.toByteArray().copyInto(header, offset, endIndex = minOf(value.length, length))
                }

                write(0, 100, name)
                write(100, 8, "0000755")
                write(108, 8, "0000000")
                write(116, 8, "0000000")
                write(124, 12, content.size.toString(8).padStart(11, '0'))
                write(136, 12, "00000000000")
                repeat(8) { header[148 + it] = ' '.code.toByte() }
                header[156] = '0'.code.toByte()
                write(257, 6, "ustar")
                write(263, 2, "00")

                val checksum = header.sumOf { it.toUByte().toInt() }
                write(148, 6, checksum.toString(8).padStart(6, '0'))
                header[154] = 0
                header[155] = ' '.code.toByte()

                gzip.write(header)
                gzip.write(content)
                gzip.write(ByteArray((512 - content.size % 512) % 512))
            }
            gzip.write(ByteArray(1024))
        }

        val project = ProjectBuilder.builder().withProjectDir(temporaryFolder.root).build()
        val service = project.gradle.sharedServices.registerIfAbsent("extractor", ExtractorService::class.java) {}.get()
        val target = temporaryFolder.newFolder("extracted").toPath()
        service.extract(archive, target)
        return target
    }
}