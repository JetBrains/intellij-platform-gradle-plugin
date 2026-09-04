// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test
import kotlin.test.assertTrue

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

    @Test
    fun `bundled plugins classpath is enabled by default and can be disabled`() {
        buildFile write //language=kotlin
                """
                tasks.register("printBundledPluginsClasspath") {
                    doLast {
                        val testTask = tasks.named<org.gradle.api.tasks.testing.Test>("test").get()
                        val ideaHomePath = testTask.jvmArgumentProviders
                            .flatMap { it.asArguments() }
                            .single { it.startsWith("-Didea.home.path=") }
                            .substringAfter("=")
                            .let(::file)
                        val pluginsDirectories = listOf(
                            ideaHomePath.resolve("plugins"),
                            ideaHomePath.resolve("Contents/plugins"),
                        ).filter { it.isDirectory }

                        testTask.classpath.files
                            .filter { classpathEntry ->
                                pluginsDirectories.any { classpathEntry.toPath().startsWith(it.toPath()) }
                            }
                            .map { it.canonicalPath }
                            .sorted()
                            .forEach { println("BUNDLED_PLUGIN_CLASSPATH=${'$'}it") }
                    }
                }
                """.trimIndent()

        val defaultClasspath = build("printBundledPluginsClasspath").output.bundledPluginsClasspathEntries()
        val disabledClasspath = build(
            "printBundledPluginsClasspath",
            projectProperties = mapOf(GradleProperties.TestIdeBundledPluginsClasspathEnabled.toString() to false),
        ).output.bundledPluginsClasspathEntries()

        assertTrue(
            defaultClasspath.containsAll(disabledClasspath),
            "The default bundled plugins classpath should preserve the explicitly disabled test classpath",
        )
        assertTrue(
            defaultClasspath.size > disabledClasspath.size,
            "The default bundled plugins classpath should contain entries from bundled plugins",
        )
    }

    private fun String.bundledPluginsClasspathEntries() = lineSequence()
        .filter { it.startsWith("BUNDLED_PLUGIN_CLASSPATH=") }
        .map { it.substringAfter('=') }
        .toSet()
}
