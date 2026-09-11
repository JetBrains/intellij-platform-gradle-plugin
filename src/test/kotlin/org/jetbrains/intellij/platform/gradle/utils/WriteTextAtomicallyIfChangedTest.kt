// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.utils

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WriteTextAtomicallyIfChangedTest {

    @Test
    fun `writes content and reports change`() {
        val directory = createTempDirectory("ivy-atomic-write")
        val target = directory.resolve("nested").resolve("descriptor.xml")

        assertTrue(target.writeTextAtomicallyIfChanged("hello"))
        assertEquals("hello", target.readText())
    }

    @Test
    fun `does not rewrite unchanged content`() {
        val directory = createTempDirectory("ivy-atomic-write")
        val target = directory.resolve("descriptor.xml")

        assertTrue(target.writeTextAtomicallyIfChanged("hello"))
        assertFalse(target.writeTextAtomicallyIfChanged("hello"))
        assertTrue(target.writeTextAtomicallyIfChanged("world"))
        assertEquals("world", target.readText())
    }

    @Test
    fun `leaves no temporary files behind`() {
        val directory = createTempDirectory("ivy-atomic-write")
        val target = directory.resolve("descriptor.xml")

        target.writeTextAtomicallyIfChanged("hello")

        val leftovers = directory.toFile().listFiles().orEmpty().map { it.name }.filter { it.endsWith(".tmp") }
        assertTrue(leftovers.isEmpty(), "Unexpected temporary files left behind: $leftovers")
    }

    /**
     * Simulates concurrent writers (serialized with a JVM-local lock, exactly as
     * [org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformDependenciesHelper] does) together with
     * concurrent readers. Readers must never observe a truncated/half-written descriptor and the final file must be
     * valid — this is the regression guarded against by the atomic temp-file + move implementation.
     */
    @Test
    fun `concurrent readers never observe a truncated file`() {
        val directory = createTempDirectory("ivy-atomic-write")
        val target = directory.resolve("version").resolve("descriptor.xml")

        // Two valid, distinct contents of very different lengths, so a non-atomic (truncate-then-write) writer would
        // expose an intermediate, partial state that neither of the two values matches.
        val short = "<ivy-module version=\"2.0\"/>"
        val long = "<ivy-module version=\"2.0\">" + "<artifact/>".repeat(20_000) + "</ivy-module>"
        val validContents = setOf(short, long)

        // The production code serializes writes within a single JVM with a ReentrantLock; a FileChannel lock cannot be
        // acquired twice for the same region from within the same process, so we mirror that here.
        val writeLock = ReentrantLock()
        val failure = AtomicReference<Throwable?>(null)
        val running = AtomicBoolean(true)
        val startLatch = CountDownLatch(1)

        val writers = (0 until 4).map { index ->
            Thread {
                startLatch.await()
                try {
                    repeat(200) { iteration ->
                        val content = if ((index + iteration) % 2 == 0) short else long
                        writeLock.withLock {
                            target.writeTextAtomicallyIfChanged(content)
                        }
                    }
                } catch (throwable: Throwable) {
                    failure.compareAndSet(null, throwable)
                }
            }
        }

        val readers = (0 until 4).map {
            Thread {
                startLatch.await()
                try {
                    while (running.get()) {
                        if (target.exists()) {
                            val content = target.readText()
                            assertTrue(
                                content in validContents,
                                "Reader observed a truncated or half-written descriptor of length ${content.length}.",
                            )
                        }
                    }
                } catch (throwable: Throwable) {
                    failure.compareAndSet(null, throwable)
                }
            }
        }

        (writers + readers).forEach(Thread::start)
        startLatch.countDown()
        writers.forEach(Thread::join)
        running.set(false)
        readers.forEach(Thread::join)

        assertNull(failure.get(), "A concurrent worker failed: ${failure.get()}")
        assertTrue(target.readText() in validContents, "The final descriptor content is invalid.")
        assertTrue(target.resolveSibling("${target.name}.lock").exists(), "The cross-process lock file is missing.")
    }
}
