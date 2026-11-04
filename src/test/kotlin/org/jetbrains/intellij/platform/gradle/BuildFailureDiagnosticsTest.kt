// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.UnexpectedBuildFailure
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import kotlin.io.path.*
import kotlin.test.*

class BuildFailureDiagnosticsTest : IntelliJPlatformTestBase() {

    private val diagnosticsRoot
        get() = Path("build", "diagnostics", javaClass.simpleName).createDirectories()

    private fun preserveFailure(throwable: Throwable) = printBuildFailureDiagnostics(
        throwable = throwable,
        gradleVersion = gradleVersion,
        tasks = listOf("test"),
        projectProperties = emptyMap(),
        systemProperties = emptyMap(),
        args = emptyList(),
        buildResult = null,
    )

    private fun <T> captureStandardError(block: () -> T): Pair<T, String> {
        val console = ByteArrayOutputStream()
        val originalError = System.err
        val result = try {
            PrintStream(console, true, Charsets.UTF_8).use {
                System.setErr(it)
                block()
            }
        } finally {
            System.setErr(originalError)
        }
        return result to console.toString(Charsets.UTF_8)
    }

    private fun createFailingBuild() {
        settingsFile write "rootProject.name = \"failure-diagnostics\""
        buildFile write //language=kotlin
            """
                tasks.register("test") {
                    doLast {
                        repeat(300) { println("nested build output line ${'$'}it") }
                        throw GradleException("fixture failed\n" + (0 until 30).joinToString("\n") {
                            "failure detail ${'$'}it: " + "x".repeat(1000)
                        })
                    }
                }
                """.trimIndent()
        dir.resolve("frontend.properties").writeText("complete fixture configuration\n" + "setting=value\n".repeat(300))
    }

    @Test
    fun `keep console diagnostics concise while preserving full nested output`() {
        val existingSnapshots = diagnosticsRoot.listDirectoryEntries().toSet()
        createFailingBuild()

        val (failure, output) = captureStandardError {
            assertFailsWith<UnexpectedBuildFailure> { build("test") }
        }
        val result = failure.buildResult

        assertTrue(output.length < 4000, "Expected concise console diagnostics, got ${output.length} characters")
        assertTrue(output.lineSequence().all { it.length <= 240 }, output)
        assertTrue(output.lineSequence().count() <= 19, output)
        assertTrue(output.contains("fixture failed"), output)
        assertTrue(output.contains("Gradle version: $gradleVersion"), output)
        assertTrue(output.contains("Tasks: test"), output)
        assertTrue(output.contains("TestKit failure diagnostics saved to:"), output)
        assertFalse(output.contains("nested build output line 299"))
        assertFalse(output.contains("failure detail 29:"))
        assertFalse(output.contains("complete fixture configuration"))

        val snapshots = diagnosticsRoot.listDirectoryEntries().toSet() - existingSnapshots
        assertEquals(1, snapshots.size)
        val diagnostics = snapshots.single().resolve("diagnostics.txt").readText()
        assertTrue(diagnostics.contains(result.output))
        assertTrue(diagnostics.contains("nested build output line 299"))
        assertTrue(diagnostics.contains("failure detail 29: " + "x".repeat(1000)))
        assertTrue(diagnostics.contains(dir.resolve("frontend.properties").readText().prependIndent("      ")))
    }

    @Test
    fun `preserve diagnostics when no fixture reports were generated`() {
        val existingSnapshots = diagnosticsRoot.listDirectoryEntries().toSet()
        val failure =
            AssertionError("\nfixture failed: ${"x".repeat(1000)}\n" + "complete assertion detail\n".repeat(300))

        val (_, output) = captureStandardError { preserveFailure(failure) }
        assertTrue(output.contains("fixture failed"), output)
        assertTrue(output.lineSequence().all { it.length <= 240 }, output)
        assertFalse(output.contains("complete assertion detail"))

        val snapshots = diagnosticsRoot.listDirectoryEntries().toSet() - existingSnapshots
        assertEquals(1, snapshots.size)
        assertTrue(snapshots.single().resolve("diagnostics.txt").readText().contains(failure.stackTraceToString()))
    }

    @OptIn(ExperimentalPathApi::class)
    @Test
    fun `preserve separate report snapshots before project cleanup`() {
        val existingSnapshots = diagnosticsRoot.listDirectoryEntries().toSet()
        val buildDirectory = dir.resolve("build")
        fun readReports() = listOf("test-results", "reports/tests").flatMap { path ->
            buildDirectory.resolve(path).walk().filter { it.isRegularFile() }.map { file ->
                file.relativeTo(buildDirectory) to file.readBytes()
            }.toList()
        }

        settingsFile write "rootProject.name = \"failure-reports\""
        buildFile write //language=kotlin
            """
                plugins { java }
                repositories { mavenCentral() }
                dependencies { testImplementation("junit:junit:4.13.2") }
                """.trimIndent()
        val testFile = dir.resolve("src/test/java/FixtureTest.java")
        testFile.parent.createDirectories()
        testFile.writeText(
            //language=java
            """
            public class FixtureTest {
                @org.junit.Test
                public void fails() {
                    org.junit.Assert.fail("first fixture failure");
                }
            }
            """.trimIndent(),
        )

        val (firstFailure) = captureStandardError {
            assertFailsWith<UnexpectedBuildFailure> { build("test") }
        }
        val xmlReport = dir.resolve("build/test-results/test/TEST-FixtureTest.xml")
        val htmlReport = dir.resolve("build/reports/tests/test/index.html")
        assertTrue(xmlReport.readText().contains("first fixture failure"))
        assertTrue(htmlReport.exists())
        val firstReports = readReports()
        val firstSnapshots = diagnosticsRoot.listDirectoryEntries().toSet() - existingSnapshots
        assertEquals(1, firstSnapshots.size)
        val firstSnapshot = firstSnapshots.single()

        testFile.writeText(testFile.readText().replace("first fixture failure", "second fixture failure"))
        val (secondFailure) = captureStandardError {
            assertFailsWith<UnexpectedBuildFailure> { build("test") }
        }
        assertTrue(xmlReport.readText().contains("second fixture failure"))
        assertTrue(htmlReport.exists())
        val secondReports = readReports()
        val secondSnapshots = diagnosticsRoot.listDirectoryEntries().toSet() - existingSnapshots - firstSnapshots
        assertEquals(1, secondSnapshots.size)
        val secondSnapshot = secondSnapshots.single()

        buildDirectory.deleteRecursively()
        firstReports.forEach { (path, content) ->
            assertContentEquals(content, firstSnapshot.resolve(path).readBytes(), path.toString())
        }
        secondReports.forEach { (path, content) ->
            assertContentEquals(content, secondSnapshot.resolve(path).readBytes(), path.toString())
        }
        assertTrue(firstSnapshot.resolve("diagnostics.txt").readText().contains(firstFailure.buildResult.output))
        assertTrue(secondSnapshot.resolve("diagnostics.txt").readText().contains(secondFailure.buildResult.output))
    }

    @Test
    fun `summarize the assertion that failed after an expected build failure`() {
        createFailingBuild()
        val failure = AssertionError("\npost-build verification failed\n" + "complete assertion detail\n".repeat(300))

        val (_, output) = captureStandardError {
            assertSame(
                failure,
                assertFailsWith<AssertionError> {
                    buildAndFail("test") { throw failure }
                },
            )
        }

        assertTrue(output.contains("post-build verification failed"), output)
        assertFalse(output.contains("fixture failed"), output)
        assertFalse(output.contains("complete assertion detail"), output)
    }

    @Test
    fun `fall back to complete console diagnostics when snapshots cannot be preserved`() {
        createFailingBuild()
        val fixture = PreservationFailureFixture().apply { dir = this@BuildFailureDiagnosticsTest.dir }
        val blockedRoot = Path("build", "diagnostics", fixture.javaClass.simpleName)
        blockedRoot.parent.createDirectories()
        blockedRoot.createFile()
        try {
            val (failure, output) = captureStandardError {
                assertFailsWith<UnexpectedBuildFailure> { fixture.runBuild() }
            }

            assertTrue(output.contains("Could not preserve TestKit failure diagnostics:"), output)
            assertFalse(output.contains("TestKit failure diagnostics saved to:"))
            assertTrue(output.contains(failure.buildResult.output))
            assertTrue(output.contains("nested build output line 299"))
            assertTrue(output.contains(dir.resolve("frontend.properties").readText().prependIndent("      ")))
        } finally {
            blockedRoot.deleteExisting()
        }
    }

    private class PreservationFailureFixture : IntelliJPlatformTestBase() {
        fun runBuild() = build("test")
    }
}
