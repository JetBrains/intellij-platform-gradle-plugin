// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers

import org.jetbrains.intellij.platform.gradle.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractorTransformerTargetResolverTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve name for JetBrains Marketplace plugin dependency`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        plugin("org.intellij.scala:2024.1.9")
                    }
                }
                repositories {
                    intellijPlatform {
                        marketplace()
                    }
                }
                """.trimIndent()

        prepareTest(artifactPathValue = "intellijPlatformPlugins.single().toPath()")

        build(randomTaskName) {
            assertLogValue("intellijPlatformDependencyPath: ") {
                assertTrue(it.endsWith("/ideaIC-2022.3.3.zip"))
            }
            assertLogValue("jetbrainsRuntimeDependencyPath: ") {
                assertTrue(it.isEmpty())
            }
            assertLogValue("intellijPlatformPluginsPath: ") {
                assertTrue(it.endsWith("/org.intellij.scala-2024.1.9.zip"))
            }
            assertLogValue("target: ") {
                assertEquals("com.jetbrains.plugins-org.intellij.scala-2024.1.9", it)
            }
        }
    }

    @Test
    fun `resolve name for IntelliJ Platform dependency`() {
        prepareTest(artifactPathValue = "intellijPlatformDependency.single().toPath()")

        build(randomTaskName) {
            assertLogValue("intellijPlatformDependencyPath: ") {
                assertTrue(it.endsWith("/ideaIC-2022.3.3.zip"))
            }
            assertLogValue("jetbrainsRuntimeDependencyPath: ") {
                assertTrue(it.isEmpty())
            }
            assertLogValue("intellijPlatformPluginsPath: ") {
                assertTrue(it.isEmpty())
            }
            assertLogValue("target: ") {
                assertEquals("IC-2022.3.3", it)
            }
        }
    }

    @Test
    fun `resolve name for JetBrains Runtime dependency`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        jetbrainsRuntime("jbr_jcef-17.0.10-osx-aarch64-b1087.21")
                    }
                }
                repositories {
                    intellijPlatform {
                        jetbrainsRuntime()
                    }
                }
                """.trimIndent()

        prepareTest(artifactPathValue = "jetbrainsRuntimeDependency.single().toPath()")

        build(randomTaskName) {
            assertLogValue("intellijPlatformDependencyPath: ") {
                assertTrue(it.endsWith("/ideaIC-2022.3.3.zip"))
            }
            assertLogValue("jetbrainsRuntimeDependencyPath: ") {
                assertTrue(it.endsWith("/jbr-jbr_jcef-17.0.10-osx-aarch64-b1087.21.tar.gz"))
            }
            assertLogValue("intellijPlatformPluginsPath: ") {
                assertTrue(it.isEmpty())
            }
            assertLogValue("target: ") {
                assertEquals("jbr_jcef-17.0.10-osx-aarch64-b1087.21", it)
            }
        }
    }

    private fun prepareTest(artifactPathValue: String) {
        buildFile prepend //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.Constants.Configurations
                import org.jetbrains.intellij.platform.gradle.resolvers.ExtractorTransformerTargetResolver
                import kotlin.io.path.Path
                import kotlin.io.path.invariantSeparatorsPathString
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks {
                    val intellijPlatformDependency = configurations.getByName(Configurations.INTELLIJ_PLATFORM_DEPENDENCY)
                    val jetbrainsRuntimeDependency = configurations.getByName(Configurations.JETBRAINS_RUNTIME_DEPENDENCY)
                    val intellijPlatformPlugins = configurations.getByName(Configurations.INTELLIJ_PLATFORM_PLUGINS)
                    
                    val intellijPlatformDependencyPathProvider = provider {
                        intellijPlatformDependency.singleOrNull()?.toPath()?.invariantSeparatorsPathString.orEmpty()
                    }
                    val jetbrainsRuntimeDependencyPathProvider = provider {
                        jetbrainsRuntimeDependency.singleOrNull()?.toPath()?.invariantSeparatorsPathString.orEmpty()
                    }
                    val intellijPlatformPluginsPathProvider = provider {
                        intellijPlatformPlugins.toList().joinToString(":") { it.toPath().invariantSeparatorsPathString }
                    }
                    val targetProvider = provider {
                        runCatching {
                            ExtractorTransformerTargetResolver(
                                artifactPath = $artifactPathValue,
                                intellijPlatformDependency,
                                jetbrainsRuntimeDependency,
                                intellijPlatformPlugins,
                            ).resolve()
                        }.getOrNull()
                    }
                
                    register("$randomTaskName") {
                        doLast {
                            println("intellijPlatformDependencyPath: " + intellijPlatformDependencyPathProvider.get())
                            println("jetbrainsRuntimeDependencyPath: " + jetbrainsRuntimeDependencyPathProvider.get())
                            println("intellijPlatformPluginsPath: " + intellijPlatformPluginsPathProvider.get())
                            println("target: " + targetProvider.get())
                        }
                    }
                }
                """.trimIndent()
    }
}
