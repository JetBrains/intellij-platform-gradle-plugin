// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.GrammarKitPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GenerateLexerTaskTest : GrammarKitPluginTestBase() {
    private val defaultTargetRootOutputDir
        get() = dir.resolve("build/generated/sources/grammarkit-lexer/java/main")

    @Test
    fun `run lexer`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetOutputDir = layout.projectDirectory.dir("gen/org/jetbrains/grammarkit/lexer")
                }
                """.trimIndent()

        build(Tasks.GENERATE_LEXER) {
            val generatedLexer = adjustPath(dir.resolve("gen/org/jetbrains/grammarkit/lexer/GeneratedLexer.java").toRealPath().toString())
            assertContains(output, "> Task :${Tasks.GENERATE_LEXER}")
            assertContains(
                adjustPath(output),
                "Writing code to \"$generatedLexer\"",
            )
        }
    }

    @Test
    fun `use default target root output dir for lexer`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                }
                """.trimIndent()

        build(Tasks.GENERATE_LEXER) {
            val generatedLexer = adjustPath(defaultTargetRootOutputDir.resolve("GeneratedLexer.java").toRealPath().toString())
            assertContains(output, "> Task :${Tasks.GENERATE_LEXER}")
            assertContains(adjustPath(output), "Writing code to \"$generatedLexer\"")
        }
    }

    @Test
    fun `create lexer in package subdirectory inferred from source file`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/ExampleWithPackage.flex")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                }
                """.trimIndent()

        build(Tasks.GENERATE_LEXER) {
            val generatedLexer = adjustPath(dir.resolve("gen/org/jetbrains/grammarkit/lexer/GeneratedLexer.java").toRealPath().toString())
            assertContains(adjustPath(output), "Writing code to \"$generatedLexer\"")
        }
    }

    @Test
    fun `supports build cache`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetOutputDir = layout.buildDirectory.dir("gen/org/jetbrains/grammarkit/lexer")
                    pathToClass = "GeneratedLexer.java"
                }
                """.trimIndent()

        build(Tasks.GENERATE_LEXER, args = listOf("--build-cache")) {
            assertTrue(task(":${Tasks.GENERATE_LEXER}")?.outcome in setOf(TaskOutcome.SUCCESS, TaskOutcome.FROM_CACHE))
        }

        build(Tasks.External.CLEAN, args = listOf("--build-cache"))

        build(Tasks.GENERATE_LEXER, args = listOf("--build-cache")) {
            assertEquals(TaskOutcome.FROM_CACHE, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }
    }

    @Test
    fun `purge stale files when lexer class path is omitted`() {
        val staleFile = dir.resolve("gen/StaleLexer.java")

        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                }
                """.trimIndent()

        build(Tasks.GENERATE_LEXER)
        staleFile write "class StaleLexer {}"
        assertTrue(staleFile.toFile().exists())

        build(Tasks.GENERATE_LEXER, args = listOf("--rerun-tasks")) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }

        assertFalse(staleFile.toFile().exists())
    }

    @Test
    fun `purge lexer class by default when lexer class path is set`() {
        val generatedLexer = dir.resolve("gen/GeneratedLexer.java")

        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToClass = "GeneratedLexer.java"
                }
                """.trimIndent()

        build(Tasks.GENERATE_LEXER)
        generatedLexer write "class StaleLexer {}"
        assertContains(generatedLexer.toFile().readText(), "StaleLexer")

        build(Tasks.GENERATE_LEXER, args = listOf("--rerun-tasks")) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }

        assertFalse("StaleLexer" in generatedLexer.toFile().readText())
    }

    @Test
    fun `do not purge existing parser output when lexer shares target root output dir`() {
        val generatedRoot = dir.resolve("gen")

        buildFile write //language=kotlin
                """
                tasks.named("generateParser", GenerateParserTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToParser = "generated/GeneratedParser.java"
                    pathToPsiRoot = "generated/psi"
                }
                
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/ExampleWithPackage.flex")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToClass = "org/jetbrains/grammarkit/lexer/GeneratedLexer.java"
                }
                """.trimIndent()

        build(Tasks.GENERATE_PARSER) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_PARSER}")?.outcome)
        }

        build(Tasks.GENERATE_LEXER) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }

        val generatedPaths = collectPaths(generatedRoot)
        assertTrue(generatedPaths.any { it.endsWith("Parser.java") })
        assertContains(generatedPaths, "org/jetbrains/grammarkit/lexer/GeneratedLexer.java")
    }

    @Test
    fun `do not purge custom parser output when custom lexer shares target root output dir`() {
        val generatedRoot = dir.resolve("gen")

        buildFile write //language=kotlin
                """
                tasks.register<GenerateParserTask>("generateCustomParser") {
                    sourceFile = file("${resource("grammarkit/generateParser/Example.bnf")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToParser = "generated/GeneratedParser.java"
                    pathToPsiRoot = "generated/psi"
                }
                
                tasks.register<GenerateLexerTask>("generateCustomLexer") {
                    sourceFile = file("${resource("grammarkit/generateLexer/ExampleWithPackage.flex")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                    pathToClass = "org/jetbrains/grammarkit/lexer/GeneratedLexer.java"
                }
                """.trimIndent()

        build("generateCustomParser") {
            assertEquals(TaskOutcome.SUCCESS, task(":generateCustomParser")?.outcome)
        }

        build("generateCustomLexer") {
            assertEquals(TaskOutcome.SUCCESS, task(":generateCustomLexer")?.outcome)
        }

        val generatedPaths = collectPaths(generatedRoot)
        assertTrue(generatedPaths.any { it.endsWith("Parser.java") })
        assertContains(generatedPaths, "org/jetbrains/grammarkit/lexer/GeneratedLexer.java")
    }

    @Test
    fun `do not purge stale files by default when using deprecated target output dir`() {
        val staleFile = dir.resolve("gen/StaleLexer.java")

        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetOutputDir = layout.projectDirectory.dir("gen")
                }
                """.trimIndent()

        build(Tasks.GENERATE_LEXER)
        staleFile write "class StaleLexer {}"
        assertTrue(staleFile.toFile().exists())

        build(Tasks.GENERATE_LEXER, args = listOf("--rerun-tasks")) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }

        assertTrue(staleFile.toFile().exists())
    }

    @Test
    fun `support java srcDir`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                }
                
                sourceSets.main {
                    java.srcDir(tasks.named("generateLexer"))
                }
                """.trimIndent()

        buildAndFail(Tasks.External.COMPILE_JAVA) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }
    }

    @Test
    fun `reuses configuration cache`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetRootOutputDir = layout.projectDirectory.dir("gen")
                }
                """.trimIndent()

        buildWithConfigurationCache(Tasks.GENERATE_LEXER) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }

        buildWithConfigurationCache(Tasks.GENERATE_LEXER) {
            assertConfigurationCacheReused()
            assertEquals(TaskOutcome.UP_TO_DATE, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }
    }
}
