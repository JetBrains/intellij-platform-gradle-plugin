// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.services

import org.gradle.testfixtures.ProjectBuilder
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.outputStream
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExtractorServiceTest {

    @Test
    fun `extracts an archive through Gradle services and publishes the completed directory`() {
        val directory = createTempDirectory("extractor-service")
        val archive = directory.resolve("archive.zip")
        ZipOutputStream(archive.outputStream()).use { output ->
            output.putNextEntry(ZipEntry("content.txt"))
            output.write("content".toByteArray())
            output.closeEntry()
        }
        val extractorService = ProjectBuilder.builder().build().objects.newInstance(ExtractorService::class.java)
        val target = directory.resolve("target")

        extractorService.extract(archive, target)

        assertEquals("content", target.resolve("content.txt").readText())
        assertTrue(extractionCompleteMarker(target).exists())
    }

    @Test
    fun `reuses only a completed extraction`() {
        val target = createTempDirectory("extractor-service").resolve("target")
        val invocations = AtomicInteger()

        assertTrue(extractAtomically(target) {
            invocations.incrementAndGet()
            it.resolve("content.txt").writeText("complete")
        })
        assertFalse(extractAtomically(target) {
            invocations.incrementAndGet()
            it.resolve("content.txt").writeText("replaced")
        })

        assertEquals(1, invocations.get())
        assertEquals("complete", target.resolve("content.txt").readText())
        assertTrue(extractionCompleteMarker(target).exists())
    }

    @Test
    fun `replaces an unmarked partial extraction`() {
        val target = createTempDirectory("extractor-service").resolve("target").createDirectories()
        target.resolve("partial.txt").writeText("partial")

        assertTrue(extractAtomically(target) {
            it.resolve("complete.txt").writeText("complete")
        })

        assertFalse(target.resolve("partial.txt").exists())
        assertEquals("complete", target.resolve("complete.txt").readText())
        assertTrue(extractionCompleteMarker(target).exists())
    }

    @Test
    fun `failed extraction is never published`() {
        val parent = createTempDirectory("extractor-service")
        val target = parent.resolve("target").createDirectories()
        target.resolve("partial.txt").writeText("old partial content")

        assertFailsWith<IOException> {
            extractAtomically(target) {
                it.resolve("new-partial.txt").writeText("new partial content")
                throw IOException("interrupted")
            }
        }

        assertEquals("old partial content", target.resolve("partial.txt").readText())
        assertFalse(extractionCompleteMarker(target).exists())
        assertTrue(parent.listDirectoryEntries().none { it.fileName.toString().startsWith(".target.tmp-") })
    }

    @Test
    fun `concurrent callers publish one extraction`() {
        val target = createTempDirectory("extractor-service").resolve("target")
        val invocations = AtomicInteger()
        val failure = AtomicReference<Throwable?>(null)
        val start = CountDownLatch(1)

        val callers = (0 until 4).map {
            Thread {
                start.await()
                runCatching {
                    extractAtomically(target) { temporaryDirectory ->
                        invocations.incrementAndGet()
                        Thread.sleep(100)
                        temporaryDirectory.resolve("content.txt").writeText("complete")
                    }
                }.onFailure { failure.compareAndSet(null, it) }
            }
        }

        callers.forEach(Thread::start)
        start.countDown()
        callers.forEach(Thread::join)

        assertNull(failure.get(), "A concurrent extraction failed: ${failure.get()}")
        assertEquals(1, invocations.get())
        assertEquals("complete", target.resolve("content.txt").readText())
    }
}
