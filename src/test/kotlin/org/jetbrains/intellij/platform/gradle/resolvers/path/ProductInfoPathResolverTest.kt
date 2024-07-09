// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.prepend
import org.jetbrains.intellij.platform.gradle.write
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ProductInfoPathResolverTest : IntelliJPluginTestBase() {

    /**
     * The currently used version is [intellijPlatformVersion].
     */
    @Test
    fun `resolve product-info file using the current IntelliJ Platform`() {
        prepareTest()

        build(randomTaskName) {
            assertLogValue("pathProvider: ") {
                assertTrue(it.endsWith("/product-info.json"))
            }
        }
    }

    @Test
    fun `fail on a missing file in provided platformPath`() {
        val resolver = ProductInfoPathResolver(
            intellijPlatformDirectory = dir
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            resolver.resolve()
        }
        assertEquals("Cannot resolve 'product-info.json' with: '$dir'", exception.message)
    }

    @Test
    fun `pass with a file provided directly to the resolver`() {
        val file = dir.resolve("product-info.json").createFile()

        val resolver = ProductInfoPathResolver(
            intellijPlatformDirectory = file
        )
        assertEquals(file, resolver.resolve())
    }

    @Test
    fun `pass with a file located in provided directory to the resolver`() {
        val file = dir.resolve("product-info.json").createFile()

        val resolver = ProductInfoPathResolver(
            intellijPlatformDirectory = dir
        )
        assertEquals(file, resolver.resolve())
    }

    @Test
    fun `pass with a file located in Resources within the provided directory to the resolver`() {
        val file = dir
            .resolve("Resources")
            .createDirectories()
            .resolve("product-info.json")
            .createFile()

        val resolver = ProductInfoPathResolver(
            intellijPlatformDirectory = dir
        )
        assertEquals(file, resolver.resolve())
    }

    private fun prepareTest() {
        buildFile prepend  //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.resolvers.path.ProductInfoPathResolver
                import kotlin.io.path.invariantSeparatorsPathString
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks {
                    val productInfoPathResolver = ProductInfoPathResolver(intellijPlatform.platformPath)
                    val pathProvider = provider {
                        productInfoPathResolver.resolve().invariantSeparatorsPathString
                    }
                
                    register("$randomTaskName") {
                        doLast {
                            println("pathProvider: " + pathProvider.get())
                        }
                    }
                }
                """.trimIndent()
    }
}
