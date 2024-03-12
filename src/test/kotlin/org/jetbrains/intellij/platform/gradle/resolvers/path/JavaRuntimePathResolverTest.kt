// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.internal.jvm.Jvm
import org.gradle.testkit.runner.BuildResult
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JavaRuntimePathResolverTest : IntelliJPluginTestBase() {

    @Test
    fun `resolve current JVM by default`() {
        buildFile.readText()
            .replace(
                """
                kotlin {
                    jvmToolchain(17)
                }
                """.trimIndent(),
                "",
            )
            .apply { buildFile.kotlin(this, override = true) }

        prepareAndBuild {
            val (jetbrainsRuntime, intellijPlatform, resolvedPath) = it.readLines()

            assertTrue(jetbrainsRuntime.isEmpty())
            assertTrue(intellijPlatform.isNotEmpty())
            assertEquals(Jvm.current().javaHome.absolutePath, resolvedPath)

            assertContains("'Current JVM' resolved as: $resolvedPath", output)
        }
    }

    @Test
    fun `resolve remote JetBrains Runtime path, Linux distribution`() {
        val version = "jbr_jcef-17.0.10-linux-aarch64-b1087.21"

        setupJetBrainsRuntimeConfiguration(version)
        prepareAndBuild {
            val (jetbrainsRuntime, intellijPlatform, resolvedPath) = it.readLines()

            assertTrue(jetbrainsRuntime.isNotEmpty())
            assertTrue(intellijPlatform.isNotEmpty())
            assertTrue(resolvedPath.startsWith(jetbrainsRuntime))
            assertTrue(jetbrainsRuntime.endsWith(version))
            assertTrue(resolvedPath.endsWith("$version/$version"))
            assertTrue(Path(resolvedPath).resolve("bin/java").exists())

            assertContains("'JetBrains Runtime specified with dependencies' resolved as: $resolvedPath", output)
        }
    }

    @Test
    fun `resolve remote JetBrains Runtime path, macOS distribution`() {
        val version = "jbr_jcef-17.0.10-osx-aarch64-b1087.21"

        setupJetBrainsRuntimeConfiguration(version)
        prepareAndBuild {
            val (jetbrainsRuntime, intellijPlatform, resolvedPath) = it.readLines()

            assertTrue(jetbrainsRuntime.isNotEmpty())
            assertTrue(intellijPlatform.isNotEmpty())
            assertTrue(resolvedPath.startsWith(jetbrainsRuntime))
            assertTrue(jetbrainsRuntime.endsWith(version))
            assertTrue(resolvedPath.endsWith("$version/$version/Contents/Home"))
            assertTrue(Path(resolvedPath).resolve("bin/java").exists())

            assertContains("'JetBrains Runtime specified with dependencies' resolved as: $resolvedPath", output)
        }
    }

    @Test
    fun `resolve remote JetBrains Runtime path, Windows distribution`() {
        val version = "jbr_jcef-17.0.10-windows-aarch64-b1087.21"

        setupJetBrainsRuntimeConfiguration(version)
        prepareAndBuild {
            val (jetbrainsRuntime, intellijPlatform, resolvedPath) = it.readLines()

            assertTrue(jetbrainsRuntime.isNotEmpty())
            assertTrue(intellijPlatform.isNotEmpty())
            assertTrue(resolvedPath.startsWith(jetbrainsRuntime))
            assertTrue(jetbrainsRuntime.endsWith(version))
            assertTrue(resolvedPath.endsWith("$version/$version"))
            assertTrue(Path(resolvedPath).resolve("bin/java.exe").exists())

            assertContains("'JetBrains Runtime specified with dependencies' resolved as: $resolvedPath", output)
        }
    }

    private fun setupJetBrainsRuntimeConfiguration(version: String) {
        buildFile.kotlin(
            """
            dependencies {
                intellijPlatform {
                    jetbrainsRuntime("$version")
                }
            }
            repositories {
                intellijPlatform {
                    jetbrainsRuntime()
                }
            }
            """.trimIndent()
        )
    }

    private fun prepareAndBuild(block: BuildResult.(output: Path) -> Unit = {}) {
        val taskName = "resolveJavaRuntimePath"
        val outputFileName = "output.txt"

        buildFile.kotlin(
            """
            import org.gradle.api.internal.project.ProjectInternal
            import org.jetbrains.intellij.platform.gradle.Constants.Configurations
            import org.jetbrains.intellij.platform.gradle.resolvers.path.JavaRuntimePathResolver
            import kotlin.io.path.invariantSeparatorsPathString
            """.trimIndent(),
            prepend = true,
        )
        buildFile.kotlin(
            """
            tasks {
                val outputFile = file("$outputFileName")
                
                val jetbrainsRuntimeConfiguration = configurations.getByName(Constants.Configurations.JETBRAINS_RUNTIME)
                val intellijPlatformConfiguration = configurations.getByName(Constants.Configurations.INTELLIJ_PLATFORM)
                val javaRuntimePathResolver = JavaRuntimePathResolver(
                    jetbrainsRuntime = jetbrainsRuntimeConfiguration,
                    intellijPlatform = intellijPlatformConfiguration,
                    javaToolchainSpec = project.extensions.findByType(JavaPluginExtension::class.java)!!.toolchain,
                    javaToolchainService = (project as ProjectInternal).services.get(JavaToolchainService::class.java),
                )
            
                val jetbrainsRuntimePathProvider = provider {
                    jetbrainsRuntimeConfiguration.singleOrNull()?.absolutePath.orEmpty()
                }
                val intellijPlatformPathProvider = provider {
                    intellijPlatformConfiguration.singleOrNull()?.absolutePath.orEmpty()
                }
                val pathProvider = provider {
                    javaRuntimePathResolver.resolve().invariantSeparatorsPathString
                }
            
                register("$taskName") {
                    doLast {
                        outputFile.writeText(
                            listOf(
                                jetbrainsRuntimePathProvider.get(),
                                intellijPlatformPathProvider.get(),
                                pathProvider.get(),
                            ).joinToString(System.lineSeparator())
                        )
                    }
                }
            }
            """.trimIndent()
        )

        build(taskName, args = listOf("--debug")) {
            block(dir.resolve(outputFileName))
        }
    }
}
