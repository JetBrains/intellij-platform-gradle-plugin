// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.providers

import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.write
import kotlin.test.Test
import kotlin.test.assertContains

class ModuleDescriptorsValueSourceTest : IntelliJPluginTestBase() {

    @Test
    fun `convert module-descriptors file into a set of coordinates`() {
        buildFile write //language=kotlin
                """
                tasks {
                    val moduleDescriptors = providers.of(org.jetbrains.intellij.platform.gradle.providers.ModuleDescriptorsValueSource::class) {
                        parameters {
                            intellijPlatformPath = layout.dir(provider { intellijPlatform.platformPath.toFile() })
                        }
                    }
                
                    register("$randomTaskName") {
                        doLast {
                            println("Module descriptors: " + moduleDescriptors.get().joinToString(";"))
                        }
                    }
                }
                """.trimIndent()

        build(randomTaskName) {
            assertLogValue("Module descriptors: ") {
                val content = it.split(";")

                assertContains(content, "com.jetbrains.intellij.platform:boot")
                assertContains(content, "com.jetbrains.intellij.platform:util")
                assertContains(content, "junit:junit")
                assertContains(content, "org.jetbrains:jetCheck")
                assertContains(content, "org.hamcrest:hamcrest-core")
            }
        }
    }
}
