// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import java.io.File
import kotlin.test.Ignore
import kotlin.test.Test

@Ignore
class TestClasspathProjectResourcesIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "test-classpath-project-resources",
) {

    @Test
    fun `verify classpath entries`() {
        disableDebug()

        build(Tasks.External.TEST, projectProperties = defaultProjectProperties) {
            val classpathEntries = output.lines().filter { it.startsWith("test-classpath-project-resources: Test classpath entry:") }

            // TODO: don't use [File.separator]
            val testResourcesEntryIndex = classpathEntries.indexOfFirst {
                it.contains("/build/resources/test".replace("/", File.separator))
            }
            val powerMockDependencyEntryIndex = classpathEntries.indexOfFirst {
                it.contains("powermock")
            }
//            val firstIdeJarEntryIndex = classpathEntries.indexOfFirst {
//                it.contains("/caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/".replace("/", File.separator))
//            }

            assert(testResourcesEntryIndex < powerMockDependencyEntryIndex)
//            assert(testResourcesEntryIndex < firstIdeJarEntryIndex)
        }
    }
}
