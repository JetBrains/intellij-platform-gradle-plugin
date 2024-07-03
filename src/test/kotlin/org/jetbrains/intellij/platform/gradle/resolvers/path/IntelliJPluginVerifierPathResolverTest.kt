// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.resolveLatestVersion
import kotlin.io.path.createFile
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntelliJPluginVerifierPathResolverTest : IntelliJPluginTestBase() {

    @Test
    fun `use an existing file provided with localPath`() {
        val dummyFile = dir.resolve("dummyFile").createFile()
        prepareTest("layout.file(provider { file(\"${dummyFile.invariantSeparatorsPathString}\") })")

        build(randomTaskName) {
            assertLogValue("intellijPluginVerifierPathProvider: ") {
                assertTrue(it.isEmpty())
            }
            assertLogValue("pathProvider: ") {
                assertEquals(dummyFile.invariantSeparatorsPathString, it)
            }
        }
    }

    @Test
    fun `fail on a missing file provided with localPath`() {
        prepareTest("layout.file(provider { file(\"/missingFile\") })")

        buildAndFail(randomTaskName) {
            assertContains("> Cannot resolve 'IntelliJ Plugin Verifier'", output)
        }
    }

    @Test
    fun `resolve latest Plugin Verifier`() {
        val latestVersion = Coordinates("org.jetbrains.intellij.plugins", "verifier-cli").resolveLatestVersion()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginVerifier()
                    }
                }
                """.trimIndent()

        prepareTest()

        build(randomTaskName) {
            assertLogValue("intellijPluginVerifierPathProvider: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith("/verifier-cli-$latestVersion-all.jar"))
            }
            assertLogValue("pathProvider: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith("/verifier-cli-$latestVersion-all.jar"))
            }
        }
    }

    @Test
    fun `resolve Plugin Verifier with fixed version`() {
        val version = "1.364"

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginVerifier("$version")
                    }
                }
                """.trimIndent()

        prepareTest()

        build(randomTaskName) {
            assertLogValue("intellijPluginVerifierPathProvider: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith("/verifier-cli-$version-all.jar"))
            }
            assertLogValue("pathProvider: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith("/verifier-cli-$version-all.jar"))
            }
        }
    }

    private fun prepareTest(localPathValue: String = "layout.file(provider { null })") {
        buildFile prepend  //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.Constants.Configurations
                import org.jetbrains.intellij.platform.gradle.resolvers.path.IntelliJPluginVerifierPathResolver
                import kotlin.io.path.invariantSeparatorsPathString
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks {
                    val intellijPluginVerifierConfiguration = configurations.getByName(Configurations.INTELLIJ_PLUGIN_VERIFIER)
                    val intelliJPluginVerifierPathResolver = IntelliJPluginVerifierPathResolver(
                        intellijPluginVerifierConfiguration,
                        localPath = $localPathValue,
                    )
                
                    val intellijPluginVerifierPathProvider = provider {
                        intellijPluginVerifierConfiguration.singleOrNull()?.toPath()?.invariantSeparatorsPathString.orEmpty()
                    }
                    val pathProvider = provider {
                        intelliJPluginVerifierPathResolver.resolve().invariantSeparatorsPathString
                    }
                
                    register("$randomTaskName") {
                        doLast {
                            println("intellijPluginVerifierPathProvider: " + intellijPluginVerifierPathProvider.get())
                            println("pathProvider: " + pathProvider.get())
                        }
                    }
                }
                """.trimIndent()
    }
}
