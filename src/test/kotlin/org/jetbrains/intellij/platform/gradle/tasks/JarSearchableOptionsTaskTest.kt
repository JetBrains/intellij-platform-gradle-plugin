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
}
