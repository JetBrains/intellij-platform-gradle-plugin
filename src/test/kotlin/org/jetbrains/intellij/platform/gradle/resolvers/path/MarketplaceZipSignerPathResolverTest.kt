// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Locations
import org.jetbrains.intellij.platform.gradle.models.Coordinates
import org.jetbrains.intellij.platform.gradle.models.resolveLatestVersion
import java.net.URL
import kotlin.io.path.createFile
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MarketplaceZipSignerPathResolverTest : IntelliJPluginTestBase() {

    @Test
    fun `use an existing file provided with localPath`() {
        val dummyFile = dir.resolve("dummyFile").createFile()
        prepareTest("layout.file(provider { file(\"${dummyFile.invariantSeparatorsPathString}\") })")

        build(randomTaskName) {
            assertLogValue("marketplaceZipSignerPathProvider: ") {
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
            assertContains("> Cannot resolve 'Marketplace ZIP Signer'", output)
        }
    }

    @Test
    fun `resolve latest Marketplace Zip Signer`() {
        val latestVersion = Coordinates("org.jetbrains", "marketplace-zip-signer").resolveLatestVersion()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner()
                    }
                }
                """.trimIndent()

        prepareTest()

        build(randomTaskName) {
            assertLogValue("marketplaceZipSignerPathProvider: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith("/marketplace-zip-signer-$latestVersion-cli.jar"))
            }
            assertLogValue("pathProvider: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith("/marketplace-zip-signer-$latestVersion-cli.jar"))
            }
        }
    }

    @Test
    fun `resolve Marketplace Zip Signer with fixed version`() {
        val version = "0.1.24"

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        zipSigner("$version")
                    }
                }
                """.trimIndent()

        prepareTest()

        build(randomTaskName) {
            assertLogValue("marketplaceZipSignerPathProvider: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith("/marketplace-zip-signer-$version-cli.jar"))
            }
            assertLogValue("pathProvider: ") {
                assertTrue(it.isNotEmpty())
                assertTrue(it.endsWith("/marketplace-zip-signer-$version-cli.jar"))
            }
        }
    }

    private fun prepareTest(localPathValue: String = "layout.file(provider { null })") {
        buildFile prepend //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.Constants.Configurations
                import org.jetbrains.intellij.platform.gradle.resolvers.path.MarketplaceZipSignerPathResolver
                import kotlin.io.path.invariantSeparatorsPathString
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks {
                    val marketplaceZipSignerConfiguration = configurations.getByName(Configurations.MARKETPLACE_ZIP_SIGNER)
                    val marketplaceZipSignerPathResolver = MarketplaceZipSignerPathResolver(
                        marketplaceZipSignerConfiguration,
                        localPath = $localPathValue,
                    )
                
                    val marketplaceZipSignerPathProvider = provider {
                        marketplaceZipSignerConfiguration.singleOrNull()?.toPath()?.invariantSeparatorsPathString.orEmpty()
                    }
                    val pathProvider = provider {
                        marketplaceZipSignerPathResolver.resolve().invariantSeparatorsPathString
                    }
                
                    register("$randomTaskName") {
                        doLast {
                            println("marketplaceZipSignerPathProvider: " + marketplaceZipSignerPathProvider.get())
                            println("pathProvider: " + pathProvider.get())
                        }
                    }
                }
                """.trimIndent()
    }
}
