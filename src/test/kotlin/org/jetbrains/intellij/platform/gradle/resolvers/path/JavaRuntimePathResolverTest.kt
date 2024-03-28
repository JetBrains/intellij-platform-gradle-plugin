// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.internal.jvm.Jvm
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
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

        prepareTest()

        build(randomTaskName, args = listOf("--debug")) {
            assertLogValue("jetbrainsRuntimePath: ") {
                assertTrue(it.isEmpty())
            }
            assertLogValue("intellijPlatformPath: ") {
                assertTrue(it.isNotEmpty())
            }
            assertLogValue("resolvedPath: ") {
                assertEquals(Jvm.current().javaHome.toPath().invariantSeparatorsPathString, it)
            }
            assertContains("'Current JVM' resolved as:", output)
        }
    }

    @Test
    fun `resolve remote JetBrains Runtime path, Linux distribution`() {
        val version = "jbr_jcef-17.0.10-linux-aarch64-b1087.21"

        setupJetBrainsRuntimeConfiguration(version)
        prepareTest()

        build(randomTaskName, args = listOf("--debug")) {
            val jetbrainsRuntime = assertLogValue("jetbrainsRuntimePath: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith(version))
            }
            assertLogValue("intellijPlatformPath: ") {
                assertTrue(it.isNotEmpty())
            }
            assertLogValue("resolvedPath: ") {
                assertTrue(it.startsWith(jetbrainsRuntime))
                assertTrue(it.endsWith("$version/$version"))
                assertExists(Path(it).resolve("bin/java"))
            }

            assertContains("'JetBrains Runtime specified with dependencies' resolved as:", output)
        }
    }

    @Test
    fun `resolve remote JetBrains Runtime path, macOS distribution`() {
        val version = "jbr_jcef-17.0.10-osx-aarch64-b1087.21"

        setupJetBrainsRuntimeConfiguration(version)
        prepareTest()

        build(randomTaskName, args = listOf("--debug")) {
            val jetbrainsRuntime = assertLogValue("jetbrainsRuntimePath: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith(version))
            }

            assertLogValue("intellijPlatformPath: ") {
                assertTrue(it.isNotEmpty())
            }
            assertLogValue("resolvedPath: ") {
                assertTrue(it.startsWith(jetbrainsRuntime))
                assertTrue(it.endsWith("$version/$version/Contents/Home"))
                assertExists(Path(it).resolve("bin/java"))
            }

            assertContains("'JetBrains Runtime specified with dependencies' resolved as:", output)
        }
    }

    @Test
    fun `resolve remote JetBrains Runtime path, Windows distribution`() {
        val version = "jbr_jcef-17.0.10-windows-aarch64-b1087.21"

        setupJetBrainsRuntimeConfiguration(version)
        prepareTest()

        build(randomTaskName, args = listOf("--debug")) {
            val jetbrainsRuntime = assertLogValue("jetbrainsRuntimePath: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith(version))
            }
            assertLogValue("intellijPlatformPath: ") {
                assertTrue(it.isNotEmpty())
            }
            assertLogValue("resolvedPath: ") {
                assertTrue(it.startsWith(jetbrainsRuntime))
                assertTrue(it.endsWith("$version/$version"))
                assertExists(Path(it).resolve("bin/java.exe"))
            }
            assertContains("'JetBrains Runtime specified with dependencies' resolved as:", output)
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

    private fun prepareTest() {
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
                val jetbrainsRuntimeConfiguration = configurations.getByName(Configurations.JETBRAINS_RUNTIME)
                val intellijPlatformConfiguration = configurations.getByName(Configurations.INTELLIJ_PLATFORM)
                val javaRuntimePathResolver = JavaRuntimePathResolver(
                    jetbrainsRuntime = jetbrainsRuntimeConfiguration,
                    intellijPlatform = intellijPlatformConfiguration,
                    javaToolchainSpec = project.extensions.findByType(JavaPluginExtension::class.java)!!.toolchain,
                    javaToolchainService = (project as ProjectInternal).services.get(JavaToolchainService::class.java),
                )
            
                val jetbrainsRuntimePathProvider = provider {
                    jetbrainsRuntimeConfiguration.singleOrNull()?.toPath()?.invariantSeparatorsPathString.orEmpty()
                }
                val intellijPlatformPathProvider = provider {
                    intellijPlatformConfiguration.singleOrNull()?.toPath()?.invariantSeparatorsPathString.orEmpty()
                }
                val pathProvider = provider {
                    javaRuntimePathResolver.resolve().invariantSeparatorsPathString
                }
            
                register("$randomTaskName") {
                    doLast {
                        println("jetbrainsRuntimePath: " + jetbrainsRuntimePathProvider.get())
                        println("intellijPlatformPath: " + intellijPlatformPathProvider.get())
                        println("resolvedPath: " + pathProvider.get())
                    }
                }
            }
            """.trimIndent()
        )
    }
}
