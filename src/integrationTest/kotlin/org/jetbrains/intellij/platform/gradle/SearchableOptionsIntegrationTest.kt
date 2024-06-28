// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.exists
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SearchableOptionsIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "searchable-options",
) {

    @Test
    fun `test manifest file`() {
        build(Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties) {
            val searchableOptionsJar = buildDirectory.resolve("libs/test-1.0.0-searchableOptions.jar")
            assertExists(searchableOptionsJar)

            println("searchableOptionsJar = ${searchableOptionsJar}")
            searchableOptionsJar containsFileInArchive "search/test-1.0.0.jar.searchableOptions.xml"
            with(searchableOptionsJar readEntry "search/test-1.0.0.jar.searchableOptions.xml") {
                assertEquals(
                    """
                    <options>
                      <configurable id="test.searchable.configurable" configurable_name="Test Searchable Configurable">
                        <option name="configurable" hit="Label for Test Searchable Configurable" />
                        <option name="for" hit="Label for Test Searchable Configurable" />
                        <option name="label" hit="Label for Test Searchable Configurable" />
                        <option name="searchable" hit="Label for Test Searchable Configurable" />
                        <option name="test" hit="Label for Test Searchable Configurable" />
                        <option name="configurable" hit="Test Searchable Configurable" />
                        <option name="searchable" hit="Test Searchable Configurable" />
                        <option name="test" hit="Test Searchable Configurable" />
                      </configurable>
                    </options>
                    """.trimIndent(),
                    this,
                )
            }

            val submoduleSearchableOptionsJar = dir.resolve("submodule/build/libs/submodule-1.0.1-searchableOptions.jar")
            assertFalse(submoduleSearchableOptionsJar.exists())
        }
    }
}
