// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchableOptionsIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "searchable-options",
) {

    @Test
    fun `test manifest file`() {
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
            assertExists(submoduleSearchableOptionsJar)

            submoduleSearchableOptionsJar containsFileInArchive "search/submodule-1.0.1.jar.searchableOptions.xml"
            with(submoduleSearchableOptionsJar readEntry "search/submodule-1.0.1.jar.searchableOptions.xml") {
                assertEquals(
                    """
                    <options>
                      <configurable id="submodule.searchable.configurable" configurable_name="Submodule Searchable Configurable">
                        <option name="configurable" hit="Label for Submodule Searchable Configurable" />
                        <option name="for" hit="Label for Submodule Searchable Configurable" />
                        <option name="label" hit="Label for Submodule Searchable Configurable" />
                        <option name="searchable" hit="Label for Submodule Searchable Configurable" />
                        <option name="submodule" hit="Label for Submodule Searchable Configurable" />
                        <option name="configurable" hit="Submodule Searchable Configurable" />
                        <option name="searchable" hit="Submodule Searchable Configurable" />
                        <option name="submodule" hit="Submodule Searchable Configurable" />
                      </configurable>
                    </options>
                    """.trimIndent(),
                    this,
                )
            }
        }
    }
}
