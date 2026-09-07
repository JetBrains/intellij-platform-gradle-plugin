// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

private const val DEPENDENCIES = "dependencies"

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
    fun `submodule inherits root IntelliJ Platform dependency`() {
        build(":submodule:$DEPENDENCIES", "--configuration=intellijPlatformDependency", projectProperties = defaultProjectProperties) {
            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform
                \--- localIde:$intellijPlatformType:$intellijPlatformType-$intellijPlatformBuildNumber
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `explicit submodule IntelliJ Platform dependency overrides inherited root dependency`() {
        dir.resolve("submodule/build.gradle.kts") += //language=kotlin
                """

                dependencies {
                    intellijPlatform {
                        create(providers.gradleProperty("intellijPlatform.type"), providers.gradleProperty("intellijPlatform.version")) {
                            useCache = false
                        }
                    }
                }
                """.trimIndent()

        val type = intellijPlatformType.toIntelliJPlatformType(intellijPlatformVersion)
        val coordinates = requireNotNull(type.installer)
        val artifactCoordinates = "${coordinates.groupId}:${coordinates.artifactId}:$intellijPlatformVersion"

        build(":submodule:$DEPENDENCIES", "--configuration=intellijPlatformDependency", projectProperties = defaultProjectProperties) {
            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform
                \--- $artifactCoordinates
                """.trimIndent(),
                output,
            )
            assertNotContains("localIde:$intellijPlatformType:$intellijPlatformType-$intellijPlatformBuildNumber", output)
        }
    }

    @Test
    fun `submodule test task rider plugin home path points to root project`() {
        dir.resolve("submodule/build.gradle.kts") += //language=kotlin
                """
                tasks.named<org.gradle.api.tasks.testing.Test>("test") {
                    val riderPluginHomePath = systemProperties["rider.tests.plugin.home.path"]?.toString().orEmpty()
                
                    println("rider.tests.plugin.home.path=${'$'}riderPluginHomePath")
                    println("rider.tests.plugin.home.path.matchesRootProjectDir=${'$'}{riderPluginHomePath == rootProject.projectDir.invariantSeparatorsPath}")
                    println("rider.tests.plugin.home.path.matchesSubmoduleProjectDir=${'$'}{riderPluginHomePath == projectDir.invariantSeparatorsPath}")
                }
                """.trimIndent()

        build(":submodule:test", projectProperties = defaultProjectProperties, args = listOf("--dry-run")) {
            assertContains("rider.tests.plugin.home.path.matchesRootProjectDir=true", output)
            assertContains("rider.tests.plugin.home.path.matchesSubmoduleProjectDir=false", output)
        }
    }
}
