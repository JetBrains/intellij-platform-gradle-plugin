// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.resolvers.path

import org.gradle.api.GradleException
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.invariantSeparatorsPathString
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

        val exception = assertFailsWith<GradleException> {
            resolver.resolve()
        }
        assertEquals("Cannot resolve 'Module Descriptors' with: ${dir.invariantSeparatorsPathString}", exception.message)
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
        buildFile.kotlin(
            """
            import org.jetbrains.intellij.platform.gradle.resolvers.path.ModuleDescriptorsPathResolver
            import kotlin.io.path.invariantSeparatorsPathString
            """.trimIndent(),
            prepend = true,
        )
        buildFile.kotlin(
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
        )
    }
}
