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
    fun `test searchable options`() {
        build(Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties) {
            val searchableOptionsJar = buildDirectory.resolve("libs/test-1.0.0-searchableOptions.jar")
            assertExists(searchableOptionsJar)

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

    @Test
    fun `test searchable options in 242+`() {
        // 242.20224.387 (2024.2.0.1) is the oldest 224 versions that work in this test.
        build(Tasks.BUILD_PLUGIN, projectProperties = defaultProjectProperties + mapOf("intellijPlatform.version" to "242.20224.387")) {
            val searchableOptionsJar = buildDirectory.resolve("libs/test-1.0.0-searchableOptions.jar")
            assertExists(searchableOptionsJar)

            searchableOptionsJar containsFileInArchive "p-org.jetbrains.plugins.integration-tests.searchable-options-searchableOptions.json"
            with(searchableOptionsJar readEntry "p-org.jetbrains.plugins.integration-tests.searchable-options-searchableOptions.json") {
                assertEquals(
                    """
                    {"id":"test.searchable.configurable","name":"Test Searchable Configurable","entries":[{"hit":"Label for Test Searchable Configurable"},{"hit":"Test Searchable Configurable"}]}
                    
                    """.trimIndent(),
                    this,
                )
            }

            val submoduleSearchableOptionsJar = dir.resolve("submodule/build/libs/submodule-1.0.1-searchableOptions.jar")
            assertFalse(submoduleSearchableOptionsJar.exists())
        }
    }
}
