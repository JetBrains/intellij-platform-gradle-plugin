// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

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
