// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GrammarKitPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateParserTaskTest : GrammarKitPluginTestBase() {
    private val defaultTargetRootOutputDir
        get() = dir.resolve("build/generated/sources/grammarkit-parser/java/main")

    @Test
    fun `run parser`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToParser = "/org/jetbrains/grammarkit/IgnoreParser.java"
                    pathToPsiRoot = "/org/jetbrains/grammarkit/psi"
                }
                """.trimIndent()

        build(Tasks.GENERATE_PARSER) {
            val generatedParserRoot = adjustPath(dir.resolve("gen").toRealPath().toString())
            assertContains(output, "> Task :${Tasks.GENERATE_PARSER}")
            assertContains(
                adjustPath(output),
                "Example.bnf parser generated to $generatedParserRoot",
            )
        }
    }

    @Test
    fun `use default target root output dir for parser`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                }
                """.trimIndent()

        build(Tasks.GENERATE_PARSER) {
            val generatedParserRoot = adjustPath(defaultTargetRootOutputDir.toRealPath().toString())
            assertContains(output, "> Task :${Tasks.GENERATE_PARSER}")
            assertContains(adjustPath(output), "Example.bnf parser generated to $generatedParserRoot")
            assertTrue(collectPaths(defaultTargetRootOutputDir).isNotEmpty())
        }
    }

    @Test
    fun `supports build cache`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.buildDirectory.dir("gen")
                    pathToParser = "/org/jetbrains/grammarkit/IgnoreParser.java"
                    pathToPsiRoot = "/org/jetbrains/grammarkit/psi"
                }
                """.trimIndent()

        build(Tasks.GENERATE_PARSER, args = listOf("--build-cache")) {
            assertTrue(task(":${Tasks.GENERATE_PARSER}")?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
        }

        build(Tasks.External.CLEAN, args = listOf("--build-cache"))

        build(Tasks.GENERATE_PARSER, args = listOf("--build-cache")) {
            assertEquals(TaskOutcome.FROM_CACHE, task(":${Tasks.GENERATE_PARSER}")?.outcome)
        }
    }

    @Test
    fun `purge stale files by default when deprecated parser paths are omitted`() {
        val staleFile = dir.resolve("gen/StaleParser.java")

        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                }
                """.trimIndent()

        build(Tasks.GENERATE_PARSER)
        staleFile write "class StaleParser {}"
        assertTrue(staleFile.toFile().exists())

        build(Tasks.GENERATE_PARSER, args = listOf("--rerun-tasks")) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_PARSER}")?.outcome)
        }

        assertFalse(staleFile.toFile().exists())
    }

    @Test
    fun `do not purge stale files by default when deprecated parser paths are set`() {
        val staleFile = dir.resolve("gen/StaleParser.java")

        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToParser = "/org/jetbrains/grammarkit/IgnoreParser.java"
                    pathToPsiRoot = "/org/jetbrains/grammarkit/psi"
                }
                """.trimIndent()

        build(Tasks.GENERATE_PARSER)
        staleFile write "class StaleParser {}"
        assertTrue(staleFile.toFile().exists())

        build(Tasks.GENERATE_PARSER, args = listOf("--rerun-tasks")) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_PARSER}")?.outcome)
        }

        assertTrue(staleFile.toFile().exists())
    }

    @Test
    fun `purge only deprecated parser outputs when explicitly enabled`() {
        val staleParserFile = dir.resolve("gen/stale/GeneratedParser.java")
        val stalePsiFile = dir.resolve("gen/stale/psi/StalePsi.java")
        val keepFile = dir.resolve("gen/keep/Keep.java")

        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToParser = "stale/GeneratedParser.java"
                    pathToPsiRoot = "stale/psi"
                    purgeOldFiles = true
                }
                """.trimIndent()

        build(Tasks.GENERATE_PARSER)
        staleParserFile write "class GeneratedParser {}"
        stalePsiFile write "class StalePsi {}"
        keepFile write "class Keep {}"
        assertTrue(staleParserFile.toFile().exists())
        assertTrue(stalePsiFile.toFile().exists())
        assertTrue(keepFile.toFile().exists())

        build(Tasks.GENERATE_PARSER, args = listOf("--rerun-tasks")) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_PARSER}")?.outcome)
        }

        assertFalse(staleParserFile.toFile().exists())
        assertFalse(stalePsiFile.toFile().exists())
        assertTrue(keepFile.toFile().exists())
    }

    @Test
    fun `support java srcDir`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToParser = "/org/jetbrains/grammarkit/IgnoreParser.java"
                    pathToPsiRoot = "/org/jetbrains/grammarkit/psi"
                }
                
                sourceSets.main {
                    java.srcDir(tasks.named("generateParser"))
                }
                """.trimIndent()

        build(Tasks.External.COMPILE_JAVA) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_PARSER}")?.outcome)
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.External.COMPILE_JAVA}")?.outcome)
        }
    }

    @Test
    fun `reuses configuration cache`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToParser = "/org/jetbrains/grammarkit/IgnoreParser.java"
                    pathToPsiRoot = "/org/jetbrains/grammarkit/psi"
                }
                """.trimIndent()

        buildWithConfigurationCache(Tasks.GENERATE_PARSER) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_PARSER}")?.outcome)
        }

        buildWithConfigurationCache(Tasks.GENERATE_PARSER) {
            assertConfigurationCacheReused()
            assertEquals(TaskOutcome.UP_TO_DATE, task(":${Tasks.GENERATE_PARSER}")?.outcome)
        }
    }
}
