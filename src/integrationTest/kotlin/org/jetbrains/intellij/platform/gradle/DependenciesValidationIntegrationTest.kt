// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.resolvers.latestVersion.MarketplaceZipSignerLatestVersionResolver
import kotlin.test.Test
import kotlin.test.assertEquals

class IntelliJPlatformDependencyValidationIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "intellij-platform-dependency-validation",
) {

    @Test
    fun `allow for no IntelliJ Platform dependency if not running tasks`() {
        build(Tasks.External.DEPENDENCIES) {
            assertContains(
                """
                intellijPlatform - IntelliJ Platform
                No dependencies
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `correctly resolve IntelliJ Platform dependency`() {
        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                    }
                }
                """.trimIndent()

        build(Tasks.External.DEPENDENCIES) {
            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform dependency archive
                \--- com.jetbrains.intellij.idea:ideaIC:2022.3.3
                """.trimIndent(),
                output,
            )

            assertContains(
                """
                intellijPlatformLocalInstance - IntelliJ Platform local instance
                No dependencies
                """.trimIndent(),
                output,
            )

            assertContains(
                """
                intellijPlatform - IntelliJ Platform
                \--- com.jetbrains.intellij.idea:ideaIC:2022.3.3
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `report too many IntelliJ Platform dependencies`() {
        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                        create("${IntelliJPlatformType.PhpStorm}", "$intellijPlatformVersion")
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.External.DEPENDENCIES) {
            assertContains(
                """
                > More than one IntelliJ Platform dependency found:
                  com.jetbrains.intellij.idea:ideaIC:2022.3.3
                  com.jetbrains.intellij.phpstorm:phpstorm:2022.3.3
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `inform about missing IntelliJ Platform dependency`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                    }
                }
                """.trimIndent()

        build(Tasks.External.DEPENDENCIES) {
            assertContains(
                """
                intellijPlatform - IntelliJ Platform
                \--- com.jetbrains.intellij.idea:ideaIC:2022.3.3 FAILED
                """.trimIndent(),
                output,
            )
        }

        buildAndFail(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(
                """
                > No IntelliJ Platform dependency found.
                  Please ensure there is a single IntelliJ Platform dependency defined in your project and that the necessary repositories, where it can be located, are added.
                  See: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `correctly resolve Marketplace ZIP Signer dependency in the fixed version`() {
        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                        zipSigner("0.1.24")
                    }
                }
                """.trimIndent()

        build(Tasks.External.DEPENDENCIES) {
            assertContains(
                """
                marketplaceZipSigner - Marketplace ZIP Signer
                \--- org.jetbrains:marketplace-zip-signer:0.1.24
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `correctly resolve Marketplace ZIP Signer dependency in the latest version`() {
        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                        zipSigner()
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                
                    signing {
                        certificateChainFile = file("certificate/chain.crt")
                        privateKeyFile = file("certificate/private.pem")
                    }
                }
                """.trimIndent()

        val version = MarketplaceZipSignerLatestVersionResolver().resolve()
        build(Tasks.External.DEPENDENCIES) {
            assertContains(
                """
                marketplaceZipSigner - Marketplace ZIP Signer
                \--- org.jetbrains:marketplace-zip-signer:$version
                """.trimIndent(),
                output,
            )
        }

        build(Tasks.SIGN_PLUGIN) {
            assertEquals(TaskOutcome.SUCCESS, task(":${Tasks.SIGN_PLUGIN}")?.outcome)
        }
    }

    @Test
    fun `fail signing when no Marketplace ZIP Signer dependency is present`() {
        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                
                    signing {
                        certificateChainFile = file("certificate/chain.crt")
                        privateKeyFile = file("certificate/private.pem")
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.SIGN_PLUGIN) {
            assertContains(
                """
                > Failed to query the value of task ':signPlugin' property 'zipSignerExecutable'.
                   > Cannot resolve the Marketplace ZIP Signer.
                     Please make sure it is added to the project with `zipSigner()` dependency helper or `intellijPlatform.signing.cliPath` extension property.
                """.trimIndent(),
                output,
            )
        }
    }

    // TODO: verify missing IntelliJ Platform dependency when no repositories are added
    // TODO: use IntelliJ Platform from local
}
