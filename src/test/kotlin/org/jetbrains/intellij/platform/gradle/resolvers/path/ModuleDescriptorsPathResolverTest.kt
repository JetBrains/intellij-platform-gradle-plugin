// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.jetbrains.intellij.platform.gradle.*
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ModuleDescriptorsPathResolverTest : IntelliJPluginTestBase() {

    /**
     * TODO: Note this test is supposed to succeed as soon IntelliJ Platform will start bundling the file (expectedly with 2024.1 GA).
     * The currently used version is [intellijPlatformVersion].
     */
    @Test
    fun `resolve module-descriptors file using the current IntelliJ Platform`() {
        prepareTest()

        buildAndFail(randomTaskName) {
            assertContains("Cannot resolve 'Module Descriptors'", output)
        }
    }

    @Test
    fun `fail on a missing file in provided platformPath`() {
        val resolver = ModuleDescriptorsPathResolver(
            platformPath = dir
        )

        val exception = assertFailsWith<IllegalArgumentException> {
            resolver.resolve()
        }
        assertEquals("Cannot resolve 'Module Descriptors' with: '$dir'", exception.message)
    }

    @Test
    fun `pass on a present file in provided platformPath`() {
        val file = dir
            .resolve("modules")
            .createDirectories()
            .resolve("module-descriptors.jar")
            .createFile()

        val resolver = ModuleDescriptorsPathResolver(
            platformPath = dir
        )

        assertEquals(file, resolver.resolve())
    }

    private fun prepareTest() {
        buildFile prepend  //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.resolvers.path.ModuleDescriptorsPathResolver
                import kotlin.io.path.invariantSeparatorsPathString
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks {
                    val moduleDescriptorsPathResolver = ModuleDescriptorsPathResolver(intellijPlatform.platformPath)
                    val pathProvider = provider {
                        moduleDescriptorsPathResolver.resolve().invariantSeparatorsPathString
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
