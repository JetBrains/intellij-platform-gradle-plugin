// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.resolveLatestVersion
import kotlin.test.Test

private const val DEPENDENCIES = "dependencies"

class IntelliJPlatformDependencyValidationIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "intellij-platform-dependency-validation",
) {

    @Test
    fun `allow for no IntelliJ Platform dependency if not running tasks`() {
        build(DEPENDENCIES) {
            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform
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

        build(DEPENDENCIES) {
            assertContains(
                """
                intellijPlatformDependencyArchive - IntelliJ Platform dependency archive
                \--- idea:ideaIC:2022.3.3
                """.trimIndent(),
                output,
            )

            assertContains(
                """
                intellijPlatformLocal - IntelliJ Platform local
                No dependencies
                """.trimIndent(),
                output,
            )

            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform
                \--- idea:ideaIC:2022.3.3
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

        buildAndFail(DEPENDENCIES) {
            assertContains("The 'intellijPlatformDependency' configuration already contains the following IntelliJ Platform dependency: $intellijPlatformType-$intellijPlatformVersion (installer)", output)
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

        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.addDefaultIntelliJPlatformDependencies = false
                """.trimIndent()

        build(DEPENDENCIES) {
            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform
                \--- idea:ideaIC:2022.3.3 FAILED
                """.trimIndent(),
                output,
            )
        }

        buildAndFail(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(
                """
                > Failed to query the value of task ':verifyPluginProjectConfiguration' property 'runtimeDirectory'.
                   > Could not resolve all files for configuration ':intellijPlatformDependency'.
                      > Could not find idea:ideaIC:2022.3.3.
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

        build(DEPENDENCIES) {
            assertContains(
                """
                marketplaceZipSigner - Marketplace ZIP Signer
                \--- org.jetbrains:marketplace-zip-signer:{prefer 0.1.24} -> 0.1.24
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

        val latestVersion = Coordinates("org.jetbrains", "marketplace-zip-signer").resolveLatestVersion()
        build(DEPENDENCIES) {
            assertContains(
                """
                marketplaceZipSigner - Marketplace ZIP Signer
                \--- org.jetbrains:marketplace-zip-signer:{prefer +} -> $latestVersion
                """.trimIndent(),
                output,
            )
        }

        build(Tasks.SIGN_PLUGIN) {
            assertTaskOutcome(Tasks.SIGN_PLUGIN, TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `correctly resolve Marketplace ZIP Signer dependency in the latest version when a default dependency is used`() {
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

        val latestVersion = Coordinates("org.jetbrains", "marketplace-zip-signer").resolveLatestVersion()
        build(DEPENDENCIES) {
            assertContains(
                """
                marketplaceZipSigner - Marketplace ZIP Signer
                \--- org.jetbrains:marketplace-zip-signer:{prefer +} -> $latestVersion
                """.trimIndent(),
                output,
            )
        }

        build(Tasks.SIGN_PLUGIN) {
            assertTaskOutcome(Tasks.SIGN_PLUGIN, TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `resolve all transitive dependencies on bundled modules for Git4Idea`() {
        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "2024.3")
                        bundledPlugin("Git4Idea")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        build(DEPENDENCIES) {
            assertContains(
                """
                compileClasspath - Compile classpath for 'main'.
                +--- bundledPlugin:Git4Idea:IC-243.21565.193
                |    \--- bundledModule:intellij.platform.collaborationTools:IC-243.21565.193
                |         +--- bundledModule:intellij.platform.vcs.dvcs.impl:IC-243.21565.193
                |         |    \--- bundledModule:intellij.platform.vcs.log.impl:IC-243.21565.193
                |         |         \--- bundledModule:intellij.platform.vcs.impl:IC-243.21565.193
                |         |              \--- bundledModule:intellij.libraries.microba:IC-243.21565.193
                |         \--- bundledModule:intellij.platform.vcs.log.impl:IC-243.21565.193 (*)
                \--- idea:ideaIC:2024.3
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `resolve all transitive dependencies on bundled modules for Coverage`() {
        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "2024.3")
                        bundledPlugin("Coverage")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        build(DEPENDENCIES) {
            assertContains(
                """
                compileClasspath - Compile classpath for 'main'.
                +--- bundledPlugin:Coverage:IC-243.21565.193
                |    +--- bundledModule:intellij.platform.coverage:IC-243.21565.193
                |    |    \--- bundledModule:intellij.platform.coverage.agent:IC-243.21565.193
                |    \--- bundledPlugin:com.intellij.java:IC-243.21565.193
                \--- idea:ideaIC:2024.3
                """.trimIndent(),
                output,
            )
        }
    }

    // TODO: verify missing IntelliJ Platform dependency when no repositories are added
    // TODO: use IntelliJ Platform from local

    @Test
    fun `control adding default IntelliJ Platform dependencies with addDefaultIntelliJPlatformDependencies property to tests runtime classpath`() {
        val properties = defaultProjectProperties + mapOf("intellijPlatform.version" to "2024.3")

        // Test with default dependencies enabled (default behavior)
        buildFile write //language=kotlin
                """
                val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
                val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
                
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
                    }
                }
                """.trimIndent()

        build(DEPENDENCIES, projectProperties = properties) {
            assertContains(
                """
                intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime fix Classpath resolvable configuration
                \--- bundledModule:intellij-platform-test-runtime:IC-243.21565.193
                """.trimIndent(),
                output,
            )
        }

        // Test with Rider to verify intellij.rider dependency
        build(DEPENDENCIES, projectProperties = properties + mapOf("intellijPlatform.type" to IntelliJPlatformType.Rider)) {
            assertContains(
                """
                intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime fix Classpath resolvable configuration
                \--- bundledModule:intellij-platform-test-runtime:RD-243.21565.191
                """.trimIndent(),
                output,
            )

            assertContains(
                """
                intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime fix Classpath resolvable configuration
                \--- bundledModule:intellij-platform-test-runtime:RD-243.21565.191
                """.trimIndent(),
                output,
            )
        }

        // Test with default dependencies disabled
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.addDefaultIntelliJPlatformDependencies = false
                """.trimIndent()

        build(DEPENDENCIES, projectProperties = properties) {
            assertNotContains(
                """
                +--- bundledPlugin:com.intellij:IC-243.21565.193
                """.trimIndent(),
                output,
            )
        }
    }

    @Test
    fun `do not fail when default IntelliJ Platform dependencies are absent in old IntelliJ Platform releases`() {
        val properties = defaultProjectProperties + mapOf("intellijPlatform.type" to IntelliJPlatformType.Rider)

        // Test with default dependencies enabled (default behavior)
        buildFile write //language=kotlin
                """
                val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type")
                val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version")
                
                repositories {
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
                    }
                }
                """.trimIndent()

        build(DEPENDENCIES, projectProperties = properties + mapOf("intellijPlatform.version" to "2024.1.7")) {
            assertContains(
                """
                intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime fix Classpath resolvable configuration
                \--- bundledModule:intellij-platform-test-runtime:RD-241.19072.30
                """.trimIndent(),
                output,
            )
            assertNotContains(
                """
                +--- bundledModule:intellij.rider
                """.trimIndent(),
                output,
            )
        }

        build(DEPENDENCIES, projectProperties = properties + mapOf("intellijPlatform.version" to "2024.2.8")) {
            assertContains(
                """
                intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime fix Classpath resolvable configuration
                \--- bundledModule:intellij-platform-test-runtime:RD-242.23726.225
                """.trimIndent(),
                output,
            )
        }
    }
}
