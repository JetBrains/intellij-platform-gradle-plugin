// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertContains

class ApplyRecommendedRepositoriesIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "apply-recommended-repositories",
) {

    @Test
    fun `attach bundled plugin sources`() {
        build(Tasks.External.CLEAN, projectProperties = defaultProjectProperties) {
            assertLogValue("repositories = ") {
                val repositories = it.split(";")

                assertContains(repositories, "https://repo.maven.apache.org/maven2/")
                assertContains(repositories, "https://cache-redirector.jetbrains.com/intellij-dependencies")
                assertContains(repositories, "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/releases")
                assertContains(repositories, "https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository/snapshots")
                assertContains(repositories, "https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven")
            }
        }
    }
}
