// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.extensions

import org.jetbrains.intellij.platform.gradle.*
import kotlin.io.path.readText
import kotlin.test.BeforeTest
import kotlin.test.Test

class IntelliJPlatformIdesCacheEnabledTest : IntelliJPluginTestBase() {

    @BeforeTest
    override fun setup() {
        super.setup()

        buildFile overwrite buildFile.readText().replace("enabled = false", "")

        buildFile write //language=kotlin
                """
                abstract class CheckCacheEnabledTask : DefaultTask() {
                    @get:Input
                    abstract val cacheEnabled: Property<Boolean>
                
                    @TaskAction
                    fun check() {
                        println("CACHE_ENABLED: ${'$'}{cacheEnabled.get()}")
                    }
                }
                
                tasks.register<CheckCacheEnabledTask>("checkCacheEnabled") {
                    cacheEnabled = intellijPlatform.caching.ides.enabled.map {
                        println("it = ${'$'}it")
                        it
                    }
                }
                """.trimIndent()
    }


    @Test
    fun `IntellijPlatformIdesCacheEnabled property sets caching enabled convention to true`() {
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.intellijPlatformIdesCacheEnabled=true
                """.trimIndent()

        build("checkCacheEnabled") {
            assertContains("CACHE_ENABLED: true", output)
        }
    }

    @Test
    fun `IntellijPlatformIdesCacheEnabled property defaults to false when not set`() {
        build("checkCacheEnabled") {
            assertContains("CACHE_ENABLED: false", output)
        }
    }

    @Test
    fun `IntellijPlatformIdesCacheEnabled property can be overridden in build script`() {
        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.IntellijPlatformIdesCacheEnabled=false
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    caching {
                        ides {
                            enabled = true
                        }
                    }
                }
                """.trimIndent()

        build("checkCacheEnabled") {
            assertContains("CACHE_ENABLED: true", output)
        }
    }
}
