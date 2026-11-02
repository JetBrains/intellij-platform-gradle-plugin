// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuildFailureDiagnosticsTest : IntelliJPlatformTestBase() {

    private val diagnosticsRoot
        get() = Path("build", "diagnostics", javaClass.simpleName).createDirectories()

    private fun preserveFailure() = printBuildFailureDiagnostics(
        throwable = AssertionError("fixture failed"),
        gradleVersion = gradleVersion,
        tasks = listOf("test"),
        projectProperties = emptyMap(),
        systemProperties = emptyMap(),
        args = emptyList(),
        buildResult = null,
    )

    @Test
    fun `preserve diagnostics when no fixture reports were generated`() {
        val existingSnapshots = diagnosticsRoot.listDirectoryEntries().toSet()

        preserveFailure()

        val snapshots = diagnosticsRoot.listDirectoryEntries().toSet() - existingSnapshots
        assertEquals(1, snapshots.size)
        assertTrue(snapshots.single().resolve("diagnostics.txt").readText().contains("fixture failed"))
    }

    @Test
    fun `preserve separate report snapshots before project cleanup`() {
        val existingSnapshots = diagnosticsRoot.listDirectoryEntries().toSet()
        val xmlReport = dir.resolve("build/test-results/test/TEST-Fixture.xml")
        val htmlReport = dir.resolve("build/reports/tests/test/index.html")
        xmlReport.parent.createDirectories()
        htmlReport.parent.createDirectories()
        xmlReport.writeText("first XML report")
        htmlReport.writeText("first HTML report")

        preserveFailure()
        val firstSnapshots = diagnosticsRoot.listDirectoryEntries().toSet() - existingSnapshots
        assertEquals(1, firstSnapshots.size)
        val firstSnapshot = firstSnapshots.single()

        xmlReport.writeText("second XML report")
        htmlReport.writeText("second HTML report")
        preserveFailure()
        val secondSnapshots = diagnosticsRoot.listDirectoryEntries().toSet() - existingSnapshots - firstSnapshots
        assertEquals(1, secondSnapshots.size)

        xmlReport.deleteExisting()
        htmlReport.deleteExisting()
        assertEquals("first XML report", firstSnapshot.resolve("test-results/test/TEST-Fixture.xml").readText())
        assertEquals("first HTML report", firstSnapshot.resolve("reports/tests/test/index.html").readText())
        assertEquals("second XML report", secondSnapshots.single().resolve("test-results/test/TEST-Fixture.xml").readText())
        assertTrue(firstSnapshot.resolve("diagnostics.txt").readText().contains("fixture failed"))
    }
}