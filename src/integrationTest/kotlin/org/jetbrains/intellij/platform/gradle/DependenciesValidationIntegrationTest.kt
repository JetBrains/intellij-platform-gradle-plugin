// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.resolveLatestVersion
import kotlin.io.path.Path
import kotlin.test.Ignore
import kotlin.test.Test

private const val DEPENDENCIES = "dependencies"

class DependenciesValidationIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "intellij-platform-dependency-validation",
    useCache = false,
) {
    override val reuseProjectState = false

    private fun assertTestRuntimeFixClasspathDependency(version: String, output: String) {
        assertContains("intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime Fix Classpath", output)
        assertContains("\\--- bundledModule:intellij-platform-test-runtime:$version", output)
    }

    @Test
    fun `allow for no IntelliJ Platform dependency if not running tasks`() {
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.verifyPluginDefaultRecommendedIdes = false
                """.trimIndent()

        build(DEPENDENCIES) {
            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform
                [org.jetbrains.intellij.platform] Configuration 'intellijPlatformDependency' is empty. LocalIvyArtifactPathComponentMetadataRule will not be registered.
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
            val type = intellijPlatformType.toIntelliJPlatformType(intellijPlatformVersion)
            val coordinates = requireNotNull(type.installer)
            val artifactCoordinates = when {
                useCache -> "localIde:${type.code}:${type.code}-$intellijPlatformBuildNumber"
                else -> "${coordinates.groupId}:${coordinates.artifactId}:$intellijPlatformVersion"
            }

            when {
                useCache -> {
                    assertContains(
                        """
                        intellijPlatformDependencyArchive - IntelliJ Platform dependency archive
                        No dependencies
                        """.trimIndent(),
                        output,
                    )
                    assertContains(
                        """
                        intellijPlatformLocal - IntelliJ Platform local
                        \--- $artifactCoordinates
                        """.trimIndent(),
                        output,
                    )
                }

                else -> assertContains(
                    """
                    intellijPlatformDependencyArchive - IntelliJ Platform dependency archive
                    \--- $artifactCoordinates
                    """.trimIndent(),
                    output,
                )
            }
        }
    }

    @Test
    fun `resolve IntelliJ Platform dependency through local IDE path in split mode without global cache`() {
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
                    splitMode = true
                }
                """.trimIndent()

        build(DEPENDENCIES) {
            val type = intellijPlatformType.toIntelliJPlatformType(intellijPlatformVersion)
            val artifactCoordinates = "localIde:${type.code}:${type.code}-$intellijPlatformBuildNumber"

            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform
                \--- $artifactCoordinates
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
            assertContains(
                "The 'intellijPlatformDependency' configuration already contains the following IntelliJ Platform dependency: $intellijPlatformType-$intellijPlatformVersion (installer)",
                output,
            )
        }
    }

    @Test
    fun `inform about missing IntelliJ Platform dependency`() {
        val type = intellijPlatformType.toIntelliJPlatformType(intellijPlatformVersion)
        val coordinates = requireNotNull(type.installer)
        val artifactCoordinates = when {
            useCache -> "localIde:${type.code}:${type.code}-$intellijPlatformBuildNumber"
            else -> "${coordinates.groupId}:${coordinates.artifactId}:$intellijPlatformVersion"
        }

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
                org.jetbrains.intellij.platform.verifyPluginDefaultRecommendedIdes = false
                """.trimIndent()

        build(DEPENDENCIES) {
            assertContains(
                """
                intellijPlatformDependency - IntelliJ Platform
                [org.jetbrains.intellij.platform] Configuration 'intellijPlatformDependency' has some resolution errors. LocalIvyArtifactPathComponentMetadataRule will not be registered.
                \--- $artifactCoordinates FAILED
                """.trimIndent(),
                output,
            )
        }

        buildAndFail(Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION) {
            assertContains(
                """
                > Could not resolve all files for configuration ':intellijPlatformDependency'.
                   > Could not find $artifactCoordinates.
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

        build(DEPENDENCIES, "--configuration=marketplaceZipSigner") {
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
    @Ignore("Temporarily disabled due to Maven Central library publishing delays")
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
                }
                """.trimIndent()

        val latestVersion = Coordinates("org.jetbrains", "marketplace-zip-signer").resolveLatestVersion()
        build(DEPENDENCIES, "--configuration=marketplaceZipSigner") {
            assertContains(
                """
                marketplaceZipSigner - Marketplace ZIP Signer
                \--- org.jetbrains:marketplace-zip-signer:{prefer +} -> $latestVersion
                """.trimIndent(),
                output,
            )
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
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                        bundledPlugin("Git4Idea")
                    }
                }
                """.trimIndent()

        build(DEPENDENCIES, "--configuration=compileClasspath") {
            val type = intellijPlatformType.toIntelliJPlatformType(intellijPlatformVersion)
            val coordinates = requireNotNull(type.installer)

            val artifactCoordinates = when {
                useCache -> "localIde:${type.code}:${type.code}-$intellijPlatformBuildNumber"
                else -> "${coordinates.groupId}:${coordinates.artifactId}:$intellijPlatformVersion"
            }
            val artifactVersion = "$intellijPlatformType-$intellijPlatformBuildNumber"

            assertContains(
                """
                compileClasspath - Compile classpath for 'main'.
                +--- bundledPlugin:Git4Idea:$artifactVersion
                |    \--- bundledModule:intellij.platform.collaborationTools:$artifactVersion
                |         +--- bundledModule:intellij.platform.vcs.dvcs.impl:$artifactVersion
                |         |    +--- bundledModule:intellij.platform.vcs.log.impl:$artifactVersion
                |         |    |    +--- bundledModule:intellij.platform.vcs.impl:$artifactVersion
                |         |    |    |    +--- bundledModule:intellij.libraries.microba:$artifactVersion
                |         |    |    |    \--- bundledModule:intellij.platform.vcs.impl.shared:$artifactVersion
                |         |    |    \--- bundledModule:intellij.platform.vcs.impl.shared:$artifactVersion
                |         |    +--- bundledModule:intellij.platform.vcs.impl.backend:$artifactVersion
                |         |    |    +--- bundledModule:intellij.platform.kernel.backend:$artifactVersion
                |         |    |    |    \--- bundledModule:intellij.platform.rpc.backend:$artifactVersion
                |         |    |    +--- bundledModule:intellij.platform.vcs.impl:$artifactVersion (*)
                |         |    |    \--- bundledModule:intellij.platform.vcs.impl.shared:$artifactVersion
                |         |    +--- bundledModule:intellij.platform.vcs.impl.shared:$artifactVersion
                |         |    \--- bundledModule:intellij.platform.vcs.dvcs.impl.shared:$artifactVersion
                |         +--- bundledModule:intellij.platform.vcs.log.impl:$artifactVersion (*)
                |         \--- bundledModule:intellij.platform.vcs.dvcs.impl.shared:$artifactVersion
                \--- $artifactCoordinates
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
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                        bundledPlugin("Coverage")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        build(DEPENDENCIES) {
            val type = intellijPlatformType.toIntelliJPlatformType(intellijPlatformVersion)
            val coordinates = requireNotNull(type.installer)

            val artifactCoordinates = when {
                useCache -> "localIde:$intellijPlatformType:$intellijPlatformType-$intellijPlatformBuildNumber"
                else -> "${coordinates.groupId}:${coordinates.artifactId}:$intellijPlatformVersion"
            }
            val artifactVersion = "$intellijPlatformType-$intellijPlatformBuildNumber"

            assertContains(
                """
                compileClasspath - Compile classpath for 'main'.
                +--- bundledPlugin:Coverage:$artifactVersion
                |    +--- bundledPlugin:com.intellij.java:$artifactVersion
                |    \--- bundledModule:intellij.platform.coverage:$artifactVersion
                |         \--- bundledModule:intellij.platform.coverage.agent:$artifactVersion
                \--- $artifactCoordinates
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
                intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime Fix Classpath
                \--- bundledModule:intellij-platform-test-runtime:$intellijPlatformType-243.21565.193
                """.trimIndent(),
                output,
            )
        }

        // Test with Rider to verify intellij.rider dependency
        build(
            DEPENDENCIES,
            projectProperties = properties + mapOf("intellijPlatform.type" to IntelliJPlatformType.Rider),
        ) {
            assertContains(
                """
                intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime Fix Classpath
                \--- bundledModule:intellij-platform-test-runtime:RD-243.21565.191
                """.trimIndent(),
                output,
            )

            assertContains(
                """
                intellijPlatformTestRuntimeFixClasspath - IntelliJ Platform Test Runtime Fix Classpath
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
    @Ignore
    fun `do not fail when default IntelliJ Platform dependencies are absent in old IntelliJ Platform releases`() {
        val properties = defaultProjectProperties + mapOf("intellijPlatform.type" to IntelliJPlatformType.Rider)
        val fullLineBundledPluginPath = Path("plugins", "fullLine")

        gradleProperties += //language=properties
                """
                org.jetbrains.intellij.platform.verifyPluginDefaultRecommendedIdes = false
                """.trimIndent()

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
            assertTestRuntimeFixClasspathDependency("RD-241.19072.30", output)
            assertNotContains(
                """
                +--- bundledModule:intellij.rider
                """.trimIndent(),
                output,
            )
        }

        build(DEPENDENCIES, projectProperties = properties + mapOf("intellijPlatform.version" to "2024.2.8")) {
            assertTestRuntimeFixClasspathDependency("RD-242.23726.225", output)
        }
    }
}
