// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.SearchableOptionsSpecBase
import kotlin.test.Test

class BuildSearchableOptionsTaskSpec : SearchableOptionsSpecBase() {

    @Test
    fun `skip building searchable options using IDEA prior 2019_1`() {
        buildFile.groovy("""
            intellij {
                version = '14.1.4'
            } 
        """)

        val result = build(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
        assertContains("${IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME} SKIPPED", result.output)
    }

    @Test
    fun `build searchable options produces XML`() {
        pluginXml.xml(getPluginXmlWithSearchableConfigurable())

        buildFile.groovy("""
            intellij {
                version = '$intellijVersion'
            }
            buildSearchableOptions {
                enabled = true
            }
        """)

        getTestSearchableConfigurableJava().java(getSearchableConfigurableCode())

        val result = build(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
        assertContains("Starting searchable options index builder", result.output)
        assertContains("Searchable options index builder completed", result.output)

        val text = getSearchableOptionsXml("projectName").readText()
        assertContains("<configurable id=\"test.searchable.configurable\" configurable_name=\"Test Searchable Configurable\">", text)
        assertContains("hit=\"Label for Test Searchable Configurable\"", text)
    }

    @Test
    fun `reuse configuration cache`() {
        build(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, "--configuration-cache")
        assertContains("Reusing configuration cache.", result.output)
    }
}
