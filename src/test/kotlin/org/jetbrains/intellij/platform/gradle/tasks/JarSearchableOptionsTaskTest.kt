// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.SearchableOptionsTestBase
import kotlin.test.Test
import kotlin.test.assertEquals

class JarSearchableOptionsTaskTest : SearchableOptionsTestBase() {

    @Test
    fun `jar searchable options produces archive`() {
        pluginXml.xml(getPluginXmlWithSearchableConfigurable())
        buildFile.kotlin(
            """
            intellijPlatform {
                buildSearchableOptions = true
            }
            """.trimIndent()
        )
        getTestSearchableConfigurableJava().java(getSearchableConfigurableCode())

        build(Tasks.JAR_SEARCHABLE_OPTIONS)

        buildDirectory.resolve("libsSearchableOptions").let {
            assertExists(it)
            assertEquals(setOf("lib/searchableOptions-1.0.0.jar"), collectPaths(it))
        }
    }
}
