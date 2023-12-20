// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.SearchableOptionsSpecBase
import kotlin.io.path.readText
import kotlin.test.Test

class BuildSearchableOptionsTaskSpec : SearchableOptionsSpecBase() {

    @Test
    fun `build searchable options produces XML`() {
        pluginXml.xml(getPluginXmlWithSearchableConfigurable())

        getTestSearchableConfigurableJava().java(getSearchableConfigurableCode())

        buildFile.kotlin(
            """
            intellijPlatform {
                buildSearchableOptions = true
            }
            """.trimIndent()
        )

        build(Tasks.BUILD_SEARCHABLE_OPTIONS) {
            assertContains("Searchable options index builder completed", output)
        }

        getSearchableOptionsXml("projectName-1.0.0").readText().let {
            assertContains("<configurable id=\"test.searchable.configurable\" configurable_name=\"Test Searchable Configurable\">", it)
            assertContains("hit=\"Label for Test Searchable Configurable\"", it)
        }
    }

    @Test
    fun `skip build searchable options if disabled via extension`() {
        buildFile.kotlin(
            """
            intellijPlatform {
                buildSearchableOptions = false
            }
            """.trimIndent()
        )
    }
}
