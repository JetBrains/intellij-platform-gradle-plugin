// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import kotlin.io.path.readLines
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ModuleDescriptorsValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `convert module-descriptors file into a set of coordinates`() {
        buildFile.kotlin(
            """
            tasks {
                val outputFile = file("output.txt")
                val moduleDescriptors = providers.of(org.jetbrains.intellij.platform.gradle.providers.ModuleDescriptorsValueSource::class) {
                    parameters {
                        intellijPlatformPath = layout.dir(provider { intellijPlatform.platformPath.toFile() })
                    }
                }
            
                val generateModuleDescriptors by registering {
                    doLast {
                        val content = moduleDescriptors.get().joinToString(System.lineSeparator())
                        outputFile.writeText(content)
                    }
                }
            }
            """.trimIndent()
        )

        build("generateModuleDescriptors")

        val outputFile = dir.resolve("output.txt")
        val content = outputFile.readLines()

        assertContains(content, "com.jetbrains.intellij.platform:boot")
        assertContains(content, "com.jetbrains.intellij.platform:util")
        assertContains(content, "junit:junit")
        assertContains(content, "org.jetbrains:jetCheck")
        assertContains(content, "org.hamcrest:hamcrest-core")
    }
}
