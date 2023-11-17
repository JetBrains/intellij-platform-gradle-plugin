// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.SearchableOptionsSpecBase
import kotlin.io.path.readText
import kotlin.test.Ignore
import kotlin.test.Test

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

        build(Tasks.BUILD_SEARCHABLE_OPTIONS) {
            assertContains("${Tasks.BUILD_SEARCHABLE_OPTIONS} SKIPPED", output)
        }
    }

    @Ignore
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

        build(Tasks.BUILD_SEARCHABLE_OPTIONS) {
            assertContains("Starting searchable options index builder", output)
            assertContains("Searchable options index builder completed", output)
        }

        getSearchableOptionsXml("projectName").readText().let {
            assertContains("<configurable id=\"test.searchable.configurable\" configurable_name=\"Test Searchable Configurable\">", it)
            assertContains("hit=\"Label for Test Searchable Configurable\"", it)
        }
    }
}
