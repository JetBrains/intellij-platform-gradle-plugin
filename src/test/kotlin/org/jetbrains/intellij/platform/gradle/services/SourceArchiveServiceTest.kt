// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.nio.file.Files
import java.nio.file.Path
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.*

class SourceArchiveServiceTest {
    @get:Rule
    val temporary = TemporaryFolder()
    private val base get() = temporary.root.toPath()
    private val cache get() = base.resolve("cache with spaces")
    private fun archive(name: String = "sources.jar", vararg paths: String): Path = base.resolve(name).also { file ->
        ZipOutputStream(file.outputStream()).use { zip ->
            paths.forEach { zip.putNextEntry(ZipEntry(it)); zip.write("content:$it".toByteArray()); zip.closeEntry() }
        }
    }

    private fun relativeNames(root: Path): Set<String> = Files.walk(root).use { paths ->
        paths.map { root.relativize(it).invariantSeparatorsPathString }.toList().toSet()
    }

    @Test
    fun `partition placement is portable bounded and independent of archive order`() {
        val names = arrayOf("pkg/codeInsight/A.kt", "pkg/codeInsight/nested/B.kt", "pkg/codeinsight/C.kt", "A.java", "a.java", "é/X.kt", "e\u0301/Y.kt", "LICENSE")
        val service = SourceArchiveService()
        val first = service.extractRoots(archive("first.jar", *names), cache)
        val second = service.extractRoots(archive("second.jar", *names.reversedArray()), cache)
        assertEquals(first.map(::relativeNames), second.map(::relativeNames))
        assertTrue(first.any { "pkg/codeInsight/A.kt" in relativeNames(it) && "pkg/codeInsight/nested/B.kt" in relativeNames(it) })
        first.forEach { root ->
            val keys = mutableMapOf<String, String>()
            relativeNames(root).forEach { name ->
                val key = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFC).lowercase(java.util.Locale.ROOT)
                assertTrue(keys.putIfAbsent(key, name).let { it == null || it == name })
            }
        }
        val variants = (0..8).map { bits -> (0..3).joinToString("") { if (bits and (1 shl it) == 0) "a" else "A" } + ".kt" }
        assertFails { service.extractRoots(archive("too-many.jar", *variants.toTypedArray()), cache) }
        val damaged = first.last().resolve("damage.txt")
        damaged.writeText("damaged")
        assertEquals(first, service.extractRoots(base.resolve("first.jar"), cache))
        assertFalse(damaged.exists())
    }

    @Test
    fun `preserves roots licenses and reuses identical content without rewriting`() {
        val jar = archive(paths = arrayOf("main/org/example/A.java", "other/B.kt", "LICENSE"))
        val service = SourceArchiveService()
        val root = service.extract(jar, cache)
        assertEquals("content:main/org/example/A.java", root.resolve("main/org/example/A.java").readText())
        assertTrue(root.resolve("other/B.kt").isRegularFile())
        assertTrue(root.resolve("LICENSE").isRegularFile())
        val time = root.resolve("LICENSE").getLastModifiedTime()
        val copy = jar.copyTo(base.resolve("same.zip"))
        assertEquals(root, service.extract(copy, cache))
        assertEquals(time, root.resolve("LICENSE").getLastModifiedTime())
        jar.deleteExisting()
        assertFails { service.extract(jar, cache) }
    }

    @Test
    fun `content changes invalidate roots and damaged cache regenerates`() {
        val service = SourceArchiveService()
        val jar = archive(paths = arrayOf("A.java"))
        val first = service.extract(jar, cache)
        first.resolve("A.java").writeText("damaged")
        assertEquals(first, service.extract(jar, cache))
        assertEquals("content:A.java", first.resolve("A.java").readText())
        first.parent.resolve("complete").writeText("incomplete")
        assertEquals(first, service.extract(jar, cache))
        first.resolve("A.java").deleteExisting()
        assertEquals(first, service.extract(jar, cache))
        assertTrue(first.resolve("A.java").exists())
        archive(paths = arrayOf("B.kt"))
        assertNotEquals(first, service.extract(jar, cache))
        assertTrue(first.resolve("A.java").exists())
    }

    @Test
    fun `unsafe and platform colliding paths never publish roots`() {
        val cases = listOf(
            arrayOf("../escape"), arrayOf("/absolute"), arrayOf("C:/drive"), arrayOf("a\\b"),
            arrayOf("a/./b"), arrayOf("a//b"), arrayOf("a", "a/b"), arrayOf("a", "a/"),
            arrayOf("CON.java"), arrayOf("a. "),
        )
        cases.forEachIndexed { index, paths ->
            assertFails("${paths.toList()}") { SourceArchiveService().extract(archive("bad$index.zip", *paths), cache) }
        }
        assertFalse(base.resolve("escape").exists())
        assertTrue(cache.resolve("extracted/2").listDirectoryEntries().all { it.extension == "lock" })
    }

    @Test
    fun `links and poisoned completion markers are not followed`() {
        val jar = archive(paths = arrayOf("A.java"))
        val service = SourceArchiveService()
        val root = service.extract(jar, cache)
        val outside = base.resolve("outside").also { it.writeText("untouched") }
        root.resolve("A.java").deleteExisting()
        Files.createSymbolicLink(root.resolve("A.java"), outside)
        assertEquals(root, service.extract(jar, cache))
        assertFalse(Files.isSymbolicLink(root.resolve("A.java")))
        root.parent.resolve("complete").deleteExisting()
        Files.createSymbolicLink(root.parent.resolve("complete"), outside)
        service.extract(jar, cache)
        assertEquals("untouched", outside.readText())
        val linkedCache = base.resolve("linked")
        Files.createSymbolicLink(linkedCache, cache)
        assertFails { service.extract(jar, linkedCache) }
    }

    @Test
    fun `archive symlinks resource limits and corrupt archives fail atomically`() {
        val jar = archive(paths = arrayOf("A.java", "B.kt"))
        assertFails { SourceArchiveService(maxEntries = 1).extract(jar, cache) }
        assertFails { SourceArchiveService(maxBytes = 1).extract(jar, cache) }
        val directoryData = archive("directory-data.zip", "directory/")
        assertFails { SourceArchiveService(maxBytes = 1).extract(directoryData, cache) }
        val bytes = jar.readBytes()
        val central =
            bytes.indices.first { it + 3 < bytes.size && bytes[it] == 0x50.toByte() && bytes[it + 1] == 0x4b.toByte() && bytes[it + 2] == 1.toByte() && bytes[it + 3] == 2.toByte() }
        bytes[central + 41] = 0xa0.toByte()
        jar.writeBytes(bytes)
        assertFails { SourceArchiveService().extract(jar, cache) }
        jar.writeText("not a zip")
        assertFails { SourceArchiveService().extract(jar, cache) }
        assertTrue(cache.resolve("extracted/2").listDirectoryEntries().all { it.extension == "lock" })
    }

    @Test
    fun `duplicate entries keep first content and bad checksums preserve valid roots`() {
        val service = SourceArchiveService()
        val jar = archive(paths = arrayOf("A.java", "B.java"))
        val root = service.extract(jar, cache)
        val valid = jar.readBytes()
        val duplicate = valid.toString(Charsets.ISO_8859_1).replace("B.java", "A.java").toByteArray(Charsets.ISO_8859_1)
        jar.writeBytes(duplicate)
        val notes = mutableListOf<String>()
        val duplicateRoot = service.extract(jar, cache) { notes.add(it) }
        assertEquals("content:A.java", duplicateRoot.resolve("A.java").readText())
        assertTrue(notes.any { it.startsWith("sources.extraction.duplicateEntries") })
        notes.clear()
        service.extract(jar, cache) { notes.add(it) }
        assertEquals(listOf("sources.extraction.duplicateEntries:1"), notes)
        val corrupt = valid.copyOf()
        val central =
            corrupt.indices.first { it + 3 < corrupt.size && corrupt[it] == 0x50.toByte() && corrupt[it + 1] == 0x4b.toByte() && corrupt[it + 2] == 1.toByte() && corrupt[it + 3] == 2.toByte() }
        corrupt[central + 16] = (corrupt[central + 16].toInt() xor 1).toByte()
        jar.writeBytes(corrupt)
        assertFails { service.extract(jar, cache) }
        assertEquals("content:A.java", root.resolve("A.java").readText())
        jar.writeBytes(valid)
        assertEquals(root, service.extract(jar, cache))
    }

    @Test
    fun `ZIP64 central directory is supported with bounded counts`() {
        val jar = archive(paths = arrayOf("A.java", "B.java", "a.java", "pkg/codeInsight/A.kt", "pkg/codeinsight/B.kt"))
        val bytes =
            jar.readBytes().toString(Charsets.ISO_8859_1).replace("B.java", "A.java").toByteArray(Charsets.ISO_8859_1)
        val end = bytes.size - 22
        val eocd = ByteBuffer.wrap(bytes, end, 22).slice().order(ByteOrder.LITTLE_ENDIAN)
        val size = eocd.getInt(12).toLong()
        val offset = eocd.getInt(16).toLong()
        val zip64 = ByteBuffer.allocate(76).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(0x06064b50).putLong(44).putShort(45).putShort(45).putInt(0).putInt(0)
            .putLong(5).putLong(5).putLong(size).putLong(offset)
            .putInt(0x07064b50).putInt(0).putLong(end.toLong()).putInt(1).array()
        eocd.putShort(4, (-1).toShort()).putShort(6, (-1).toShort())
        eocd.putShort(8, (-1).toShort()).putShort(10, (-1).toShort())
        jar.writeBytes(bytes.copyOfRange(0, end) + zip64 + bytes.copyOfRange(end, bytes.size))
        val notes = mutableListOf<String>()
        val roots = SourceArchiveService().extractRoots(jar, cache) { notes.add(it) }
        val root = roots.first()
        assertEquals("content:A.java", root.resolve("A.java").readText())
        assertEquals(listOf("sources.extraction.caseCollisionRoots:2", "sources.extraction.duplicateEntries:1"), notes)
        listOf("A.java", "a.java", "pkg/codeInsight/A.kt", "pkg/codeinsight/B.kt").forEach { name ->
            assertEquals(1, roots.count { name in relativeNames(it) })
        }
        notes.clear()
        assertEquals(roots, SourceArchiveService().extractRoots(jar, cache) { notes.add(it) })
        assertEquals(listOf("sources.extraction.caseCollisionRoots:2", "sources.extraction.duplicateEntries:1"), notes)
        val valid = jar.readBytes()
        listOf(end + 16, end + 20, end + 60).forEach { position ->
            val multiDisk = valid.copyOf()
            multiDisk[position] = 1
            jar.writeBytes(multiDisk)
            assertFails { SourceArchiveService().extract(jar, cache) }
            assertEquals("content:A.java", root.resolve("A.java").readText())
        }
        jar.writeBytes(valid)
    }

    @Test
    fun `concurrent writers share one completed root`() {
        val jar = archive(paths = arrayOf("A.java", "LICENSE"))
        val executor = Executors.newFixedThreadPool(8)
        try {
            val roots = executor.invokeAll(
                (1..16).map { Callable { SourceArchiveService().extract(jar, cache) } },
                20,
                TimeUnit.SECONDS,
            )
                .map { it.get() }
            assertEquals(1, roots.distinct().size)
            assertEquals(1, cache.resolve("extracted/2").listDirectoryEntries().count { it.isDirectory() })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `orphan temporary directories are removed only for the locked content key`() {
        val jar = archive(paths = arrayOf("A.java"))
        val service = SourceArchiveService()
        val root = service.extract(jar, cache)
        val orphan = root.parent.parent.resolve("${root.parent.fileName}.extract-orphan").createDirectory()
        orphan.resolve("large-partial-file").writeText("partial")
        val outside = base.resolve("outside").also { it.writeText("keep") }
        Files.createSymbolicLink(orphan.resolve("link"), outside)
        val unrelated = root.parent.parent.resolve("other.extract-orphan").createDirectory()
        service.extract(jar, cache)
        assertFalse(orphan.exists())
        assertTrue(unrelated.exists())
        assertEquals("keep", outside.readText())
    }

    @Test
    fun `busy cache lock has a bounded wait and preserves completed roots`() {
        val jar = archive(paths = arrayOf("A.java"))
        val root = SourceArchiveService().extract(jar, cache)
        val lock = root.parent.parent.resolve("${root.parent.fileName}.lock")
        java.nio.channels.FileChannel.open(lock, java.nio.file.StandardOpenOption.WRITE).use { channel ->
            channel.lock().use {
                val started = System.nanoTime()
                assertFailsWith<IllegalStateException> {
                    SourceArchiveService(lockTimeoutMillis = 100).extract(
                        jar,
                        cache,
                    )
                }
                assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 5000)
            }
        }
        assertEquals("content:A.java", root.resolve("A.java").readText())
    }

    @Test
    fun `cached real IDEA sources smoke test respects collision policy when present`() {
        val artifacts =
            Path.of(System.getProperty("user.home"), ".gradle/caches/modules-2/files-2.1/com.jetbrains.intellij.idea")
        org.junit.Assume.assumeTrue(artifacts.isDirectory())
        val archive = Files.walk(artifacts, 5).use { files ->
            files.filter {
                it.fileName.toString().startsWith("idea-") && it.fileName.toString().endsWith("-sources.jar")
            }
                .sorted(Comparator.reverseOrder()).findFirst().orElse(null)
        }
        org.junit.Assume.assumeNotNull(archive)
        val colliders = mutableSetOf<String>()
        java.util.zip.ZipFile(archive!!.toFile()).use { zip ->
            val prefixes = mutableMapOf<String, String>()
            zip.entries().asSequence().forEach { entry ->
                val parts = entry.name.removeSuffix("/").split('/')
                parts.indices.forEach { index ->
                    val prefix = parts.take(index + 1).joinToString("/")
                    val key = java.text.Normalizer.normalize(prefix, java.text.Normalizer.Form.NFC)
                        .lowercase(java.util.Locale.ROOT)
                    prefixes.putIfAbsent(key, prefix)?.let { previous ->
                        if (previous != prefix) { colliders.add(previous); colliders.add(prefix) }
                    }
                }
            }
        }
        val roots = SourceArchiveService().extractRoots(archive, cache)
        assertTrue(roots.any { root -> Files.walk(root).use { files -> files.anyMatch { it.extension in listOf("java", "kt") } } })
        val extractedNames = roots.flatMap { relativeNames(it) }.toSet()
        colliders.forEach { name -> assertTrue(name in extractedNames, name) }
    }

    @Test
    fun `separate JVM writers coordinate publication`() {
        val jar = archive(paths = arrayOf("A.java", "LICENSE"))
        val classpath = listOf(SourceArchiveProcess::class.java, SourceArchiveService::class.java, Unit::class.java)
            .map { Path.of(it.protectionDomain.codeSource.location.toURI()).toString() }.distinct()
            .joinToString(java.io.File.pathSeparator)
        val processes = (1..3).map { index ->
            ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/java").toString(), "-cp", classpath,
                SourceArchiveProcess::class.java.name, jar.toString(), cache.toString(),
            )
                .redirectErrorStream(true).redirectOutput(base.resolve("process-$index.log").toFile()).start()
        }
        try {
            processes.forEachIndexed { index, process ->
                assertTrue(process.waitFor(20, TimeUnit.SECONDS))
                assertEquals(0, process.exitValue(), base.resolve("process-${index + 1}.log").readText())
            }
            assertEquals(1, cache.resolve("extracted/2").listDirectoryEntries().count { it.isDirectory() })
        } finally {
            processes.filter { it.isAlive }.forEach { it.destroyForcibly() }
        }
    }
}

internal object SourceArchiveProcess {
    @JvmStatic
    fun main(args: Array<String>) {
        SourceArchiveService().extract(Path.of(args[0]), Path.of(args[1]))
    }
}
