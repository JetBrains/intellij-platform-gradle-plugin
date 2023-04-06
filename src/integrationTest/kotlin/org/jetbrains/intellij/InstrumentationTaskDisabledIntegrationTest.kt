// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import java.nio.file.Files
import kotlin.test.Test

class InstrumentationTaskDisabledIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "instrumentation-task-disabled",
) {

    private val defaultArgs = listOf("--configuration-cache")

    @Test
    fun `skip instrumentCode task if disabled`() {
        build("build", args = defaultArgs, projectProperties = mapOf("instrumentCode" to false)).let {
            it.output containsText "> Task :instrumentCode SKIPPED"

            buildDirectory containsFile "libs/test-1.0.0.jar"
            buildDirectory notContainsFile "libs/instrumented-test-1.0.0.jar"

            buildDirectory.resolve("classes/java/main").run {
                resolve("ExampleAction.class").run {
                    assert(Files.exists(this))
                    assert(Files.size(this) == 625L)
                }
                resolve("Main.class").run {
                    assert(Files.exists(this))
                    assert(Files.size(this) == 658L)
                }
            }
            buildDirectory.resolve("classes/kotlin/main").run {
                resolve("MainKt.class").run {
                    assert(Files.exists(this))
                    assert(Files.size(this) == 957L)
                }
            }

            buildDirectory.resolve("libs/test-1.0.0.jar").let { jar ->
                jar containsFileInArchive "META-INF/MANIFEST.MF"

                jar containsFileInArchive "META-INF/plugin.xml"
                jar readEntry "META-INF/plugin.xml" containsText "<action id=\"ExampleAction\" class=\"ExampleAction\" text=\"Example Action\">"

                jar containsFileInArchive "ExampleAction.class"
                assert((jar readEntry "ExampleAction.class").length == 625)

                jar containsFileInArchive "Main.class"
                assert((jar readEntry "Main.class").length == 658)

                jar containsFileInArchive "MainKt.class"
                assert((jar readEntry "MainKt.class").length == 955)

                buildDirectory.resolve("instrumented").run {
                    assert(!Files.exists(this))
                }
            }

            buildDirectory.resolve("instrumented").run {
                assert(!Files.exists(this))
            }
        }
    }

    @Test
    fun `produce instrumented artifact when instrumentation is enabled`() {
        build("buildPlugin", args = defaultArgs, projectProperties = mapOf("instrumentCode" to true)).let {
            it.output notContainsText "> Task :instrumentCode SKIPPED"

            buildDirectory containsFile "libs/test-1.0.0.jar"
            buildDirectory containsFile "libs/instrumented-test-1.0.0.jar"

            buildDirectory.resolve("instrumented/instrumentCode").run {
                resolve("ExampleAction.class").run {
                    assert(Files.exists(this))
                    assert(Files.size(this) == 1028L)
                }
                resolve("Main.class").run {
                    assert(Files.exists(this))
                    assert(Files.size(this) == 1015L)
                }
                resolve("MainKt.class").run {
                    assert(Files.exists(this))
                }
            }

            buildDirectory.resolve("libs/test-1.0.0.jar").let { jar ->
                jar containsFileInArchive "META-INF/MANIFEST.MF"

                jar containsFileInArchive "META-INF/plugin.xml"
                jar readEntry "META-INF/plugin.xml" containsText "<action id=\"ExampleAction\" class=\"ExampleAction\" text=\"Example Action\">"

                jar containsFileInArchive "ExampleAction.class"
                assert((jar readEntry "ExampleAction.class").length == 625)

                jar containsFileInArchive "Main.class"
                assert((jar readEntry "Main.class").length == 658)

                jar containsFileInArchive "MainKt.class"
                assert((jar readEntry "MainKt.class").length == 955)
            }

            buildDirectory.resolve("instrumented").run {
                assert(Files.isDirectory(this))
                assert(Files.list(this).toArray().isNotEmpty())
            }
        }
    }

    @Test
    fun `run tests and print nulls when instrumentation is disabled`() {
        build("test", args = defaultArgs, projectProperties = mapOf("instrumentCode" to false)).let {
            it.output containsText """
                InstrumentationTests > fooTest STANDARD_OUT
                    null
            """.trimIndent()

            it.output containsText """
                InstrumentationTests > test STANDARD_OUT
                    null
            """.trimIndent()
        }
    }
    @Test
    fun `run tests and throw unmet assertion exceptions when instrumentation is enabled`() {
        buildAndFail("test", args = defaultArgs, projectProperties = mapOf("instrumentCode" to true)).let {
            it.output containsText """
                InstrumentationTests > fooTest FAILED
                    java.lang.IllegalArgumentException at InstrumentationTests.java:12
            """.trimIndent()

            it.output containsText """
                InstrumentationTests > test FAILED
                    java.lang.IllegalArgumentException at InstrumentationTests.java:7
            """.trimIndent()
        }
    }
}
