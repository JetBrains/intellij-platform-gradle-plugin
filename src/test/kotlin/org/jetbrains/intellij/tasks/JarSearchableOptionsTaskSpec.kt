// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME
import org.jetbrains.intellij.SearchableOptionsSpecBase
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("ComplexRedundantLet")
class JarSearchableOptionsTaskSpec : SearchableOptionsSpecBase() {

    @Test
    fun `skip jarring searchable options using IDEA prior 2019_1`() {
        buildFile.groovy(
            """
            intellij {
                version = '14.1.4'
            }
            """
        )

        build(JAR_SEARCHABLE_OPTIONS_TASK_NAME).let {
            assertContains("$JAR_SEARCHABLE_OPTIONS_TASK_NAME SKIPPED", it.output)
        }
    }

    @Test
    fun `jar searchable options produces archive`() {
        pluginXml.xml(getPluginXmlWithSearchableConfigurable())
        buildFile.groovy(
            """
            intellij {
                version = '$intellijVersion'
            }
            buildSearchableOptions {
                enabled = true
            }
            """
        )
        getTestSearchableConfigurableJava().java(getSearchableConfigurableCode())

        build(JAR_SEARCHABLE_OPTIONS_TASK_NAME)

        File(buildDirectory, "libsSearchableOptions").let {
            assertTrue(it.exists())
            assertEquals(setOf("/lib/searchableOptions.jar"), collectPaths(it))
        }
    }

    @Test
    fun `reuse configuration cache`() {
        pluginXml.xml(getPluginXmlWithSearchableConfigurable())
        buildFile.groovy(
            """
            intellij {
                version = '$intellijVersion'
            }
            """
        )
        getTestSearchableConfigurableJava().java(getSearchableConfigurableCode())

        build(JAR_SEARCHABLE_OPTIONS_TASK_NAME, "--configuration-cache")
        build(JAR_SEARCHABLE_OPTIONS_TASK_NAME, "--configuration-cache").let {
            assertContains("Reusing configuration cache.", it.output)
        }
    }
}
