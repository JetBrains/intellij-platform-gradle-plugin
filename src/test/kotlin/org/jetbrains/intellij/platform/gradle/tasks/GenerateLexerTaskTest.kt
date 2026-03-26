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
import kotlin.test.assertTrue

class GenerateLexerTaskTest : GrammarKitPluginTestBase() {

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
    fun `reuse configuration cache`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetOutputDir = layout.projectDirectory.dir("gen/org/jetbrains/grammarkit/lexer")
                }
                """.trimIndent()

        build(Tasks.GENERATE_LEXER) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }

        build(Tasks.GENERATE_LEXER) {
            assertContains(output, "Reusing configuration cache.")
            assertEquals(TaskOutcome.UP_TO_DATE, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }
    }

    @Test
    fun `supports build cache`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetOutputDir = layout.buildDirectory.dir("gen/org/jetbrains/grammarkit/lexer")
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
    fun `support java srcDir`() {
        buildFile write //language=kotlin
                """
                tasks.named("generateLexer", GenerateLexerTask::class.java) {
                    sourceFile = file("${resource("grammarkit/generateLexer/Example.flex")}")
                    targetOutputDir = layout.projectDirectory.dir("gen/org/jetbrains/grammarkit/lexer")
                }
                
                sourceSets.main {
                    java.srcDir(tasks.named("generateLexer"))
                }
                """.trimIndent()

        buildAndFail(Tasks.External.COMPILE_JAVA) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_LEXER}")?.outcome)
        }
    }
}
