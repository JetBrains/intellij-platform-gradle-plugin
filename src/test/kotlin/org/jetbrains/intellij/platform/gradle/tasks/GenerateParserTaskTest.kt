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
import kotlin.test.assertTrue

class GenerateParserTaskTest : GrammarKitPluginTestBase() {

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
    fun `reuse configuration cache`() {
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
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.GENERATE_PARSER}")?.outcome)
        }

        build(Tasks.GENERATE_PARSER) {
            assertContains(output, "Reusing configuration cache.")
            assertEquals(TaskOutcome.UP_TO_DATE, task(":${Tasks.GENERATE_PARSER}")?.outcome)
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
}
