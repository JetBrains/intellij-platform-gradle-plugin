// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME
import org.jetbrains.intellij.SearchableOptionsSpecBase
import kotlin.test.Test

@Suppress("ComplexRedundantLet")
class BuildSearchableOptionsTaskSpec : SearchableOptionsSpecBase() {

    @Test
    fun `skip building searchable options using IDEA prior 2019_1`() {
        buildFile.groovy(
            """
            intellij {
                version = '14.1.4'
            }
            """.trimIndent()
        )

        build(BUILD_SEARCHABLE_OPTIONS_TASK_NAME).let {
            assertContains("$BUILD_SEARCHABLE_OPTIONS_TASK_NAME SKIPPED", it.output)
        }
    }

    @Test
    fun `build searchable options produces XML`() {
        pluginXml.xml(getPluginXmlWithSearchableConfigurable())

        buildFile.groovy(
            """
            intellij {
                version = '$intellijVersion'
            }
            buildSearchableOptions {
                enabled = true
            }
            """.trimIndent()
        )

        getTestSearchableConfigurableJava().java(getSearchableConfigurableCode())

        build(BUILD_SEARCHABLE_OPTIONS_TASK_NAME).let {
            assertContains("Starting searchable options index builder", it.output)
            assertContains("Searchable options index builder completed", it.output)
        }

        getSearchableOptionsXml("projectName").readText().let {
            assertContains("<configurable id=\"test.searchable.configurable\" configurable_name=\"Test Searchable Configurable\">", it)
            assertContains("hit=\"Label for Test Searchable Configurable\"", it)
        }
    }

    @Test
    fun `reuse configuration cache`() {
        build(BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
        build(BUILD_SEARCHABLE_OPTIONS_TASK_NAME).let {
            assertContains("Reusing configuration cache.", it.output)
        }
    }
}
