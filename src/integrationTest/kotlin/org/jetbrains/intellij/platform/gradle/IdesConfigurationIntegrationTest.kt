// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Configurations
import java.nio.file.Paths
import kotlin.test.Test

class IdesConfigurationIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "ides-configuration",
    useCache = false,
) {

    @Test
    fun `configure multiple ides using a provider`() {
        buildFile write //language=kotlin
                """
                val idesToUse = providers.gradleProperty("ides") // IC-2024.1,IC-2024.2
                
                intellijPlatform {
                    pluginVerification {
                        ides {
                            create(idesToUse.map { it.split(",") }) { notation ->
                                val (type, version) = notation.parseIdeNotation()
                                this.type = type
                                this.version = version
                                this.useInstaller = true
                            }
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration=intellijPluginVerifierIdesDependency",
            projectProperties = defaultProjectProperties + listOf("ides" to "IC-2024.1,IC-2024.2"),
        ) {
            assertContains("idea:ideaIC:2024.1", output)
            assertContains("idea:ideaIC:2024.2", output)
        }
    }

    @Test
    fun `configure single ide using a provider`() {
        buildFile write //language=kotlin
                """
                val ideToUse = providers.gradleProperty("ide")
                
                intellijPlatform {
                    pluginVerification {
                        ides {
                            create(ideToUse) { notation ->
                                val (type, version) = notation.parseIdeNotation()
                                this.type = type
                                this.version = version
                            }
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration=intellijPluginVerifierIdesDependency",
            projectProperties = defaultProjectProperties + listOf("ide" to "IC-2024.1"),
        ) {
            assertContains("idea:ideaIC:2024.1", output)
        }
    }

    @Test
    fun `lazy evaluation of ides provider`() {
        buildFile write //language=kotlin
                """
                val idesToUse = providers.gradleProperty("ides").map {
                    println("[DEBUG_LOG] EVALUATING IDES PROVIDER")
                    it.split(",")
                }
                
                intellijPlatform {
                    pluginVerification {
                        ides {
                            create(idesToUse) { notation ->
                                val (type, version) = notation.parseIdeNotation()
                                this.type = type
                                this.version = version
                            }
                        }
                    }
                }
                
                tasks.register("someTask") {
                    doLast {
                        println("Hello")
                    }
                }
                """.trimIndent()

        // Running a task that doesn't need ides should NOT evaluate the provider
        build(
            "someTask",
            projectProperties = defaultProjectProperties + listOf("ides" to "IC-2024.1"),
        ) {
            assertNotContains("[DEBUG_LOG] EVALUATING IDES PROVIDER", output)
        }

        // Running a task that needs ides should evaluate the provider
        build(
            "dependencies",
            "--configuration=intellijPluginVerifierIdesDependency",
            projectProperties = defaultProjectProperties + listOf("ides" to "IC-2024.1"),
        ) {
            assertContains("[DEBUG_LOG] EVALUATING IDES PROVIDER", output)
        }
    }

    @Test
    fun `default to recommended ides when no verifier ides configured`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "231"
                            untilBuild = "231.*"
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration=intellijPluginVerifierIdes",
            projectProperties = defaultProjectProperties,
        ) {
            assertContains("idea:ideaIU:2023.1.7", output)
        }
    }

    @Test
    fun `explicit verifier ides suppress default recommended ides`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "231"
                            untilBuild = "231.*"
                        }
                    }
                    pluginVerification {
                        ides {
                            create("IC", "2024.1")
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration=intellijPluginVerifierIdes",
            projectProperties = defaultProjectProperties,
        ) {
            assertContains("idea:ideaIC:2024.1", output)
            assertNotContains("idea:ideaIU:2023.1.7", output)
        }
    }

    @Test
    fun `allow disabling default recommended ides with Gradle property`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "231"
                            untilBuild = "231.*"
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration=intellijPluginVerifierIdes",
            projectProperties = defaultProjectProperties + mapOf(
                GradleProperties.VerifyPluginDefaultRecommendedIdes.toString() to false,
            ),
        ) {
            assertContains("No dependencies", output)
            assertNotContains("idea:ideaIU:2023.1.7", output)
        }
    }

    @Test
    fun `current helper uses currently targeted IntelliJ Platform`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        ides {
                            current()
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration=intellijPluginVerifierIdes",
            projectProperties = defaultProjectProperties,
        ) {
            assertContains("localIde:$intellijPlatformType:$intellijPlatformType-$intellijPlatformBuildNumber", output)
        }
    }

    @Test
    fun `latest helper resolves the newest selected ide type`() {
        val jetbrainsIdesUrl = Paths.get("src", "test", "resources", "products-releases", "idea-releases-list.xml")
            .toAbsolutePath()
            .toUri()
            .toString()
        val androidStudioUrl = Paths.get("src", "test", "resources", "products-releases", "android-studio-releases-list.xml")
            .toAbsolutePath()
            .toUri()
            .toString()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginVerification {
                        ides {
                            latest {
                                sinceBuild = "223"
                                untilBuild = "233.*"
                                types = listOf(
                                    org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.IntellijIdea,
                                    org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.PhpStorm,
                                )
                            }
                        }
                    }
                }
                
                tasks.register("printLatestIdes") {
                    val ides = provider {
                        configurations["${Configurations.INTELLIJ_PLUGIN_VERIFIER_IDES}"].incoming.dependencies.joinToString()
                    }
                    
                    inputs.property("ides", ides)
                    
                    doLast {
                        println("ides = ${'$'}{ides.get()}")
                    }
                }
                """.trimIndent()

        build(
            "printLatestIdes",
            projectProperties = defaultProjectProperties + mapOf(
                GradleProperties.ProductsReleasesJetBrainsIdesUrl.toString() to jetbrainsIdesUrl,
                GradleProperties.ProductsReleasesAndroidStudioUrl.toString() to androidStudioUrl,
            ),
        ) {
            assertContains("ides = idea:ideaIU:2023.3.4, webide:PhpStorm:2023.3.5", output)
            assertNotContains("idea:ideaIU:2023.2.6", output)
        }
    }

    @Test
    fun `fallback to recommended ides when provider is absent`() {
        buildFile write //language=kotlin
                """
                val ideOverride = providers.gradleProperty("idesOverride").map { it.split(",") }
                
                intellijPlatform {
                    pluginConfiguration {
                        ideaVersion {
                            sinceBuild = "231"
                            untilBuild = "231.*"
                        }
                    }
                    pluginVerification {
                        ides {
                            create(ideOverride.orRecommended()) { notation ->
                                val (type, version) = notation.parseIdeNotation()
                                this.type = type
                                this.version = version
                            }
                        }
                    }
                }
                """.trimIndent()

        build(
            "dependencies",
            "--configuration=intellijPluginVerifierIdesDependency",
            projectProperties = defaultProjectProperties,
        ) {
            assertContains("idea:ideaIU:2023.1.7", output)
        }
    }
}
