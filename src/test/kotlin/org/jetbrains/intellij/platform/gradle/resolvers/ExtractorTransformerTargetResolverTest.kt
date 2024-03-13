// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers

import org.gradle.testkit.runner.BuildResult
import org.intellij.lang.annotations.Language
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import java.nio.file.Path
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExtractorTransformerTargetResolverTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve name for JetBrains Marketplace plugin dependency`() {
        buildFile.kotlin(
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
        )
        prepareAndBuild(
            """
            val artifactPath = intellijPlatformPlugins.single().toPath()
            """.trimIndent()
        ) {
            val (
                intellijPlatformDependencyPath,
                jetbrainsRuntimeDependencyPath,
                intellijPlatformPluginsPath,
                target,
            ) = it.readLines()

            assertTrue(intellijPlatformDependencyPath.endsWith("/ideaIC-2022.3.3.zip"))
            assertEquals("", jetbrainsRuntimeDependencyPath)
            assertTrue(intellijPlatformPluginsPath.endsWith("/org.intellij.scala-2024.1.9.zip"))
            assertEquals("com.jetbrains.plugins-org.intellij.scala-2024.1.9", target)
        }
    }

    @Test
    fun `resolve name for IntelliJ Platform dependency`() {
        prepareAndBuild(
            """
            val artifactPath = intellijPlatformDependency.single().toPath()
            """.trimIndent()
        ) {
            val (
                intellijPlatformDependencyPath,
                jetbrainsRuntimeDependencyPath,
                intellijPlatformPluginsPath,
                target,
            ) = it.readLines()

            assertTrue(intellijPlatformDependencyPath.endsWith("/ideaIC-2022.3.3.zip"))
            assertEquals("", jetbrainsRuntimeDependencyPath)
            assertEquals("", intellijPlatformPluginsPath)
            assertEquals("IC-2022.3.3", target)
        }
    }

    @Test
    fun `resolve name for JetBrains Runtime dependency`() {
        buildFile.kotlin(
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
        )
        prepareAndBuild(
            """
            val artifactPath = jetbrainsRuntimeDependency.single().toPath()
            """.trimIndent()
        ) {
            val (
                intellijPlatformDependencyPath,
                jetbrainsRuntimeDependencyPath,
                intellijPlatformPluginsPath,
                target,
            ) = it.readLines()

            assertTrue(intellijPlatformDependencyPath.endsWith("/ideaIC-2022.3.3.zip"))
            assertTrue(jetbrainsRuntimeDependencyPath.endsWith("/jbr-jbr_jcef-17.0.10-osx-aarch64-b1087.21.tar.gz"))
            assertEquals("", intellijPlatformPluginsPath)
            assertEquals("jbr_jcef-17.0.10-osx-aarch64-b1087.21", target)
        }
    }

    private fun prepareAndBuild(@Language("kotlin") artifactPathSnippet: String, block: BuildResult.(output: Path) -> Unit = {}) {
        val taskName = "resolveExtractorTransformerTargetResolver"
        val outputFileName = "output.txt"

        buildFile.kotlin(
            """
            import org.jetbrains.intellij.platform.gradle.Constants.Configurations
            import org.jetbrains.intellij.platform.gradle.resolvers.ExtractorTransformerTargetResolver
            import kotlin.io.path.Path
            import kotlin.io.path.invariantSeparatorsPathString
            """.trimIndent(),
            prepend = true,
        )
        buildFile.kotlin(
            """
            tasks {
                val outputFile = file("$outputFileName")
                
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
                    $artifactPathSnippet
                    runCatching {
                        ExtractorTransformerTargetResolver(
                            artifactPath,
                            intellijPlatformDependency,
                            jetbrainsRuntimeDependency,
                            intellijPlatformPlugins,
                        ).resolve()
                    }.getOrNull()
                }
            
                register("$taskName") {
                    doLast {
                        outputFile.writeText(
                            listOf(
                                intellijPlatformDependencyPathProvider.get(),
                                jetbrainsRuntimeDependencyPathProvider.get(),
                                intellijPlatformPluginsPathProvider.get(),
                                targetProvider.get(),
                            ).joinToString(System.lineSeparator())
                        )
                    }
                }
            }
            """.trimIndent()
        )

        build(taskName) {
            block(dir.resolve(outputFileName))
        }
    }
}
