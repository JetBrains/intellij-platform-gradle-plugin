// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import kotlin.io.path.*
import kotlin.test.Test

class InstrumentationTaskDisabledIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "instrumentation-task-disabled",
) {

    private val defaultArgs = listOf("--configuration-cache")

    @Test
    fun `skip instrumentCode task if disabled`() {
        build("build", args = defaultArgs, projectProperties = mapOf("instrumentCode" to false)) {
            output containsText "> Task :instrumentCode SKIPPED"

            buildDirectory containsFile "libs/test-1.0.0.jar"
            buildDirectory notContainsFile "libs/instrumented-test-1.0.0.jar"

            buildDirectory.resolve("classes/java/main").let {
                it.resolve("ExampleAction.class").let { file ->
                    assert(file.exists())
                    assert(file.fileSize() == 625L)
                }
                it.resolve("Main.class").let { file ->
                    assert(file.exists())
                    assert(file.fileSize() == 658L)
                }
            }
            buildDirectory.resolve("classes/kotlin/main").let {
                it.resolve("MainKt.class").let { file ->
                    assert(file.exists())
                    assert(file.fileSize() == 957L)
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

            assert(buildDirectory.resolve("instrumented").notExists())
        }
    }

    @Test
    fun `produce instrumented artifact when instrumentation is enabled`() {
        build("buildPlugin", args = defaultArgs, projectProperties = mapOf("instrumentCode" to true)) {
            output notContainsText "> Task :instrumentCode SKIPPED"

            buildDirectory containsFile "libs/test-1.0.0.jar"
            buildDirectory containsFile "libs/instrumented-test-1.0.0.jar"

            buildDirectory.resolve("instrumented/instrumentCode").let {
                it.resolve("ExampleAction.class").let { file ->
                    assert(file.exists())
                    assert(file.fileSize() == 1028L)
                }
                it.resolve("Main.class").let { file ->
                    assert(file.exists())
                    assert(file.fileSize() == 1015L)
                }
                it.resolve("MainKt.class").let { file ->
                    assert(file.exists())
                    assert(file.fileSize() == 1015L)
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

            buildDirectory.resolve("instrumented").let {
                assert(it.isDirectory())
                assert(it.listDirectoryEntries().isNotEmpty())
            }
        }
    }

    @Test
    fun `run tests and print nulls when instrumentation is disabled`() {
        build("test", args = defaultArgs, projectProperties = mapOf("instrumentCode" to false)) {
            safeOutput containsText """
                InstrumentationTests > fooTest STANDARD_OUT
                    null
            """.trimIndent()

            safeOutput containsText """
                InstrumentationTests > test STANDARD_OUT
                    null
            """.trimIndent()
        }
    }

    @Test
    fun `run tests and throw unmet assertion exceptions when instrumentation is enabled`() {
        buildAndFail("test", args = defaultArgs, projectProperties = mapOf("instrumentCode" to true)) {
            safeOutput containsText """
                InstrumentationTests > fooTest FAILED
                    java.lang.IllegalArgumentException at InstrumentationTests.java:12
            """.trimIndent()

            safeOutput containsText """
                InstrumentationTests > test FAILED
                    java.lang.IllegalArgumentException at InstrumentationTests.java:7
            """.trimIndent()
        }
    }
}
