// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.gradle.api.tasks.TaskDependency
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CommonParametersBuildDependenciesTest : IntelliJPlatformTestBase() {

    @Test
    fun `build dependencies expose only the public Gradle return type`() {
        val methods = IntelliJPlatformTestingExtension.CommonParameters::class.java.declaredMethods
            .filter { it.name == "getBuildDependencies" }

        assertEquals(setOf(TaskDependency::class.java), methods.map { it.returnType }.toSet())
        assertFalse(methods.single().isSynthetic)
    }

    @Test
    fun `build dependencies stay lazy and wire custom execution tasks with configuration cache`() {
        buildFile write """
            import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformTestingExtension.CommonParameters

            plugins { id("org.jetbrains.intellij.platform") }

            val realized = mutableSetOf<String>()
            val parameters = listOf<CommonParameters<*>>(
                intellijPlatformTesting.runIde.register("customRun") {
                    version = provider { error("Deferred SDK must not be evaluated") }
                }.get(),
                intellijPlatformTesting.testIde.register("customTest") {
                    version = provider { error("Deferred SDK must not be evaluated") }
                }.get(),
                intellijPlatformTesting.testIdeUi.register("customUiTest") {
                    version = provider { error("Deferred SDK must not be evaluated") }
                }.get(),
                intellijPlatformTesting.testIdePerformance.register("customPerformanceTest") {
                    version = provider { error("Deferred SDK must not be evaluated") }
                }.get(),
            )
            parameters.forEach { parameter ->
                val executionName = parameter.name
                parameter.task { realized += executionName }
                val dependencies = parameter.getBuildDependencies()
                check(executionName !in realized) { "Obtaining dependencies realized " + executionName }
                val execution = dependencies.getDependencies(null).single()
                check(execution.name == executionName)
                check(executionName in realized)
                val consumer = tasks.register(executionName + "Consumer") { dependsOn(parameter) }.get()
                check(consumer.taskDependencies.getDependencies(consumer) == setOf(execution))
            }
            check(realized == parameters.map { it.name }.toSet())
            println("BUILD_DEPENDENCIES_OK")
        """.trimIndent()

        buildWithConfigurationCache("help", "--offline") { assertContains("BUILD_DEPENDENCIES_OK", output) }
        buildWithConfigurationCache("help", "--offline") { assertConfigurationCacheReused() }
    }
}
