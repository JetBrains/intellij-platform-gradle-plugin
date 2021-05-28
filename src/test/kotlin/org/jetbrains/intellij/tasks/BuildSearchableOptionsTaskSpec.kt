package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.SearchableOptionsSpecBase
import kotlin.test.Test
import kotlin.test.assertTrue

class BuildSearchableOptionsTaskSpec : SearchableOptionsSpecBase() {

    @Test
    fun `skip building searchable options using IDEA prior 2019_1`() {
        buildFile.groovy("""
            intellij {
                version = '14.1.4'
            } 
        """)

        val result = build(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)

        assertTrue(result.output.contains("${IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME} SKIPPED"))
    }

    @Test
    fun `build searchable options produces XML`() {
        pluginXml.xml(getPluginXmlWithSearchableConfigurable())

        buildFile.groovy("""
            intellij {
                version = '$intellijVersion'
            }
        """)

        getTestSearchableConfigurableJava().java(getSearchableConfigurableCode())

        val result = build(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
        assertTrue(result.output.contains("Starting searchable options index builder"))
        assertTrue(result.output.contains("Searchable options index builder completed"))

        val text = getSearchableOptionsXml("projectName").readText()
        assertTrue(text.contains("<configurable id=\"test.searchable.configurable\" configurable_name=\"Test Searchable Configurable\">"))
        assertTrue(text.contains("hit=\"Label for Test Searchable Configurable\""))
    }

    @Test
    fun `reuse configuration cache`() {
        build(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, "--configuration-cache")
        val result = build(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
