// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.junit.Assume.assumeTrue
import org.junit.FixMethodOrder
import org.junit.runners.MethodSorters
import java.nio.file.LinkOption
import kotlin.io.path.*
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DmgExtractorCiTest : IntelliJPlatformTestBase() {

    @BeforeTest
    fun printEnvironment() {
        assumeTrue(System.getProperty("os.name").startsWith("Mac"))
        System.err.println("[DMG-CI] Architecture: ${System.getProperty("os.arch")}")
        System.err.println("[DMG-CI] Java: ${System.getProperty("java.version")}; nested Gradle: $gradleVersion")
        listOf(
            listOf("/usr/bin/sw_vers"),
            listOf("/bin/sh", "-c", "command -v rsync"),
            listOf("rsync", "--version"),
        ).forEach { command ->
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            System.err.println("[DMG-CI] ${command.joinToString(" ")}\n$output")
            assertEquals(0, process.waitFor(), "Environment command failed: $command")
        }
    }

    @Test
    fun `extract application bundle from a disk image`() {
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

        buildFresh("extractDmg")

        val extracted = buildDirectory.resolve("extracted")
        val extractedResources = extracted.resolve("Fixture.app/Contents/Resources")
        assertEquals("fixture content", extractedResources.resolve("fixture.txt").readText())
        assertEquals("fixture content", extractedResources.resolve("current.txt").readText())
        assertTrue(extractedResources.resolve("current.txt").isSymbolicLink())
        assertFalse(extracted.resolve("Applications").exists(LinkOption.NOFOLLOW_LINKS))
        assertFalse(extracted.resolve(".metadata").exists())
        System.err.println("[DMG-CI] Tiny real-DMG extraction passed")
    }

    @Test
    fun `resolve IntelliJ IDEA Community installer`() {
        settingsFile write "rootProject.name = \"dmg-idea-installer\""
        buildFile write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform")
                }

                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }

                dependencies {
                    intellijPlatform {
                        create("IC", "2024.1.2")
                    }
                }

                val platform = configurations.named("intellijPlatformDependency")
                tasks.register("resolveIde") {
                    inputs.files(platform)
                    doLast {
                        val extracted = platform.get().files.single()
                        check(extracted.isDirectory) { "Expected extracted IDE directory: " + extracted }
                        check(extracted.listFiles().orEmpty().isNotEmpty()) { "Extracted IDE directory is empty" }
                        println("RESOLVED_IDE=" + extracted.absolutePath)
                    }
                }
                """.trimIndent()

        buildFresh("resolveIde")
        System.err.println("[DMG-CI] Real ideaIC 2024.1.2 installer extraction passed")
    }

    private fun buildFresh(task: String) {
        val home = dir.resolve("gradle-user-home")
        assertFalse(home.exists(), "Diagnostic Gradle home must be fresh")
        System.err.println("[DMG-CI] Fresh nested Gradle home: $home")
        build(
            task,
            args = listOf("--gradle-user-home", home.pathString, "--info", "--no-scan", "--console=plain"),
        ) {
            assertEquals(TaskOutcome.SUCCESS, task(":$task")?.outcome)
            output.lineSequence()
                .filter {
                    it.contains("rsync") || it.contains("hdiutil") || it.contains("Extracting DMG") ||
                            it.contains("ideaIC-") || it.startsWith("RESOLVED_IDE=") || it.startsWith("BUILD SUCCESSFUL")
                }
                .forEach(System.err::println)
        }
    }
}