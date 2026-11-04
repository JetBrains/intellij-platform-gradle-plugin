// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.junit.Assume.assumeTrue
import java.nio.file.LinkOption
import kotlin.io.path.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DmgExtractorIntegrationTest : IntelliJPlatformIntegrationTestBase() {

    @Test
    fun `extract application bundle from a disk image`() {
        assumeTrue(System.getProperty("os.name").startsWith("Mac"))

        val source = dir.resolve("image").createDirectories()
        val resources = source.resolve("Fixture.app/Contents/Resources").createDirectories()
        resources.resolve("fixture.txt").writeText("fixture content")
        resources.resolve("current.txt").createSymbolicLinkPointingTo(Path("fixture.txt"))
        source.resolve("Applications").createSymbolicLinkPointingTo(Path("/Applications"))
        source.resolve(".metadata").writeText("installer metadata")

        settingsFile write "rootProject.name = \"dmg-extractor\""
        buildFile write //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.providers.DmgExtractorValueSource

                plugins {
                    id("org.jetbrains.intellij.platform.base")
                }

                tasks.register<Exec>("createDmg") {
                    commandLine(
                        "hdiutil", "create", "-quiet", "-ov", "-fs", "HFS+", "-size", "8m",
                        "-srcfolder", layout.projectDirectory.dir("image"),
                        layout.projectDirectory.file("fixture.dmg"),
                    )
                }

                val extracted = providers.of(DmgExtractorValueSource::class) {
                    parameters.path = layout.projectDirectory.file("fixture.dmg")
                    parameters.target = layout.buildDirectory.dir("extracted")
                }

                tasks.register("extractDmg") {
                    dependsOn("createDmg")
                    doLast {
                        extracted.get()
                    }
                }
                """.trimIndent()

        build("extractDmg")

        val extracted = buildDirectory.resolve("extracted")
        val extractedResources = extracted.resolve("Fixture.app/Contents/Resources")
        assertEquals("fixture content", extractedResources.resolve("fixture.txt").readText())
        assertEquals("fixture content", extractedResources.resolve("current.txt").readText())
        assertTrue(extractedResources.resolve("current.txt").isSymbolicLink())
        assertFalse(extracted.resolve("Applications").exists(LinkOption.NOFOLLOW_LINKS))
        assertFalse(extracted.resolve(".metadata").exists())
    }
}
