// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertEquals

class JarSearchableOptionsTaskTest : SearchableOptionsTestBase() {

    @Test
    fun `jar searchable options produces archive`() {
        pluginXml write getPluginXmlWithSearchableConfigurable()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = true
                }
                """.trimIndent()

        getTestSearchableConfigurableJava() write getSearchableConfigurableCode()

        build(Tasks.JAR_SEARCHABLE_OPTIONS)

        buildDirectory.resolve("libs").let {
            assertExists(it)
            assertEquals(setOf("projectName-1.0.0-searchableOptions.jar", "projectName-1.0.0-base.jar", "projectName-1.0.0.jar"), collectPaths(it))
        }
    }

    @Test
    fun `jar searchable options produces archive if enabled via property and explicitly configured`() {
        pluginXml write getPluginXmlWithSearchableConfigurable()

        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.buildSearchableOptions = true
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = providers.gradleProperty("org.jetbrains.intellij.platform.buildSearchableOptions").map {
                        it.toBoolean()
                    }
                }
                """.trimIndent()

        getTestSearchableConfigurableJava() write getSearchableConfigurableCode()

        build(Tasks.JAR_SEARCHABLE_OPTIONS)

        buildDirectory.resolve("libs").let {
            assertExists(it)
            assertEquals(setOf("projectName-1.0.0-searchableOptions.jar", "projectName-1.0.0-base.jar", "projectName-1.0.0.jar"), collectPaths(it))
        }
    }

    @Test
    fun `jar searchable options disabled via property and explicitly configured`() {
        pluginXml write getPluginXmlWithSearchableConfigurable()

        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.buildSearchableOptions = false
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = providers.gradleProperty("org.jetbrains.intellij.platform.buildSearchableOptions").map {
                        it.toBoolean()
                    }
                }
                """.trimIndent()

        getTestSearchableConfigurableJava() write getSearchableConfigurableCode()

        build(Tasks.JAR_SEARCHABLE_OPTIONS)

        buildDirectory.resolve("libs").let {
            assertExists(it)
            assertEquals(setOf("projectName-1.0.0-base.jar", "projectName-1.0.0.jar"), collectPaths(it))
        }
    }
}
