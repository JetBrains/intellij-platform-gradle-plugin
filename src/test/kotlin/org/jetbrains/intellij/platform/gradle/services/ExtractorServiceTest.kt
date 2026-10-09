// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.testfixtures.ProjectBuilder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteExisting
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractorServiceTest {

    @Test
    fun `extracts once and reuses the completed directory`() {
        val directory = createTempDirectory("extractor-service")
        val archive = directory.resolve("archive.zip").also { it.writeZip() }
        val target = directory.resolve("target")
        val extractorService = extractorService()

        extractorService.extract(archive, target)
        archive.deleteExisting()
        extractorService.extract(archive, target)

        assertEquals("content", target.resolve("content.txt").readText())
        assertEquals(listOf("content.txt"), target.listDirectoryEntries().map { it.name })
        assertTrue(extractionCompleteMarker(target).exists())
    }

    @Test
    fun `replaces an unmarked partial extraction`() {
        val directory = createTempDirectory("extractor-service")
        val archive = directory.resolve("archive.zip").also { it.writeZip() }
        val target = directory.resolve("target").createDirectories()
        target.resolve("partial.txt").writeText("partial")

        extractorService().extract(archive, target)

        assertFalse(target.resolve("partial.txt").exists())
        assertEquals("content", target.resolve("content.txt").readText())
        assertTrue(extractionCompleteMarker(target).exists())
    }

    @Test
    fun `failed extraction can be retried`() {
        val parent = createTempDirectory("extractor-service")
        val archive = parent.resolve("invalid.zip").apply { writeText("not a zip") }
        val target = parent.resolve("target").createDirectories()
        target.resolve("partial.txt").writeText("old partial content")
        val extractorService = extractorService()

        assertFails { extractorService.extract(archive, target) }
        assertFalse(extractionCompleteMarker(target).exists())

        archive.writeZip()
        extractorService.extract(archive, target)

        assertEquals("content", target.resolve("content.txt").readText())
        assertTrue(extractionCompleteMarker(target).exists())
    }

    @Test
    fun `concurrent callers publish one extraction`() {
        val directory = createTempDirectory("extractor-service")
        val archive = directory.resolve("archive.zip").also { it.writeZip() }
        val target = directory.resolve("target")
        val extractorService = extractorService()
        val failure = AtomicReference<Throwable?>(null)
        val start = CountDownLatch(1)

        val callers = (0 until 4).map {
            Thread {
                start.await()
                runCatching {
                    extractorService.extract(archive, target)
                }.onFailure { failure.compareAndSet(null, it) }
            }
        }

        callers.forEach(Thread::start)
        start.countDown()
        callers.forEach(Thread::join)

        assertNull(failure.get(), "A concurrent extraction failed: ${failure.get()}")
        assertEquals("content", target.resolve("content.txt").readText())
        assertTrue(extractionCompleteMarker(target).exists())
    }

    private fun extractorService() =
        ProjectBuilder.builder().build().objects.newInstance(ExtractorService::class.java)

    private fun java.nio.file.Path.writeZip() = ZipOutputStream(outputStream()).use { output ->
        output.putNextEntry(ZipEntry("content.txt"))
        output.write("content".toByteArray())
        output.closeEntry()
    }
}
