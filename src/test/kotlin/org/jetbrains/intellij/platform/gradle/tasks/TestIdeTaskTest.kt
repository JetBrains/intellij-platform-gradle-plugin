// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test

class TestIdeTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `test task sets idea home path to target IntelliJ Platform`() {
        buildFile write //language=kotlin
                """
                tasks.register("verifyTestIdeaHomePath") {
                    doLast {
                        val arguments = tasks.named<org.gradle.api.tasks.testing.Test>("test")
                            .get()
                            .jvmArgumentProviders
                            .flatMap { it.asArguments() }
                                
                        val ideaHomePath = arguments
                            .single { it.startsWith("-Didea.home.path=") }
                            .substringAfter("=")
                            .let(::file)
                    
                        check(ideaHomePath.isDirectory) { "idea.home.path does not point to a directory: ${'$'}ideaHomePath" }
                        check(ideaHomePath.resolve("product-info.json").isFile || ideaHomePath.resolve("Resources/product-info.json").isFile) {
                            "idea.home.path does not point to an IntelliJ Platform home: ${'$'}ideaHomePath"
                        }
                
                        println("IDEA_HOME_PATH=${'$'}ideaHomePath")
                    }
                }
                """.trimIndent()

        build("verifyTestIdeaHomePath") {
            assertContains("IDEA_HOME_PATH=", output)
        }
    }
}
