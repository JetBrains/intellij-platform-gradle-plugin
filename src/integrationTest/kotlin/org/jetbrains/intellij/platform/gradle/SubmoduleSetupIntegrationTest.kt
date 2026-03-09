// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class SubmoduleSetupIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "submodule-setup",
) {

    @Test
    fun `submodule shouldn't contain the runIde task`() {
        build(Tasks.RUN_IDE, projectProperties = defaultProjectProperties, args = listOf("--dry-run")) {
            assertContains(":runIde SKIPPED", output)
            assertNotContains(":submodule:runIde SKIPPED", output)
        }
    }

    @Test
    fun `submodule test task rider plugin home path points to root project`() {
        dir.resolve("submodule/build.gradle.kts") += //language=kotlin
                """
                tasks.named<org.gradle.api.tasks.testing.Test>("test") {
                    val riderPluginHomePath = systemProperties["rider.tests.plugin.home.path"]?.toString().orEmpty()
                
                    println("rider.tests.plugin.home.path=${'$'}riderPluginHomePath")
                    println("rider.tests.plugin.home.path.matchesRootProjectDir=${'$'}{riderPluginHomePath == rootProject.projectDir.absolutePath}")
                    println("rider.tests.plugin.home.path.matchesSubmoduleProjectDir=${'$'}{riderPluginHomePath == projectDir.absolutePath}")
                }
                """.trimIndent()

        build(":submodule:help", projectProperties = defaultProjectProperties) {
            assertContains("rider.tests.plugin.home.path.matchesRootProjectDir=true", output)
            assertContains("rider.tests.plugin.home.path.matchesSubmoduleProjectDir=false", output)
        }
    }
}
