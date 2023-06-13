// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin

import java.io.File
import kotlin.test.Test

class TestClasspathProjectResourcesIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "test-classpath-project-resources",
) {

    @Test
    fun `verify classpath entries`() {
        build("test").let {
            val classpathEntries = it.output
                .lines()
                .filter { line -> line.startsWith("test-classpath-project-resources: Test classpath entry:") }

            val testResourcesEntryIndex = classpathEntries
                .indexOfFirst { entry -> entry.contains("/build/resources/test".replace("/", File.separator)) }
            val powerMockDependencyEntryIndex = classpathEntries
                .indexOfFirst { entry -> entry.contains("powermock") }
            val firstIdeJarEntryIndex = classpathEntries
                .indexOfFirst { entry -> entry.contains("/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/".replace("/", File.separator)) }

            assert(testResourcesEntryIndex < powerMockDependencyEntryIndex)
            assert(testResourcesEntryIndex < firstIdeJarEntryIndex)
        }
    }
}
