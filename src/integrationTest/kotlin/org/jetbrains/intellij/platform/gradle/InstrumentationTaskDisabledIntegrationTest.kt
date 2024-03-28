// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.fileSize
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.notExists
import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumentationTaskDisabledIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "instrumentation-task-disabled",
) {

    private val defaultArgs = listOf("--configuration-cache")

    @Test
    fun `skip instrumentCode task if disabled`() {
        build(
            Tasks.BUILD_PLUGIN,
            args = defaultArgs,
            projectProperties = defaultProjectProperties + mapOf("instrumentCode" to false),
        ) {
            output containsText "> Task :instrumentCode SKIPPED"

            buildDirectory containsFile "libs/test-1.0.0.jar"
            buildDirectory notContainsFile "libs/instrumented-test-1.0.0.jar"

            buildDirectory.resolve("classes/java/main").let {
                it.resolve("ExampleAction.class").let { file ->
                    assertExists(file)
                    assertEquals(576, file.fileSize())
                }
                it.resolve("Main.class").let { file ->
                    assertExists(file)
                    assertEquals(607, file.fileSize())
                }
            }
            buildDirectory.resolve("classes/kotlin/main").let {
                it.resolve("MainKt.class").let { file ->
                    assertExists(file)
                    assertEquals(957, file.fileSize())
                }
            }

            buildDirectory.resolve("libs/test-1.0.0.jar").let { jar ->
                jar containsFileInArchive "META-INF/MANIFEST.MF"

                jar containsFileInArchive "META-INF/plugin.xml"
                jar readEntry "META-INF/plugin.xml" containsText "<action id=\"ExampleAction\" class=\"ExampleAction\" text=\"Example Action\">"

                jar containsFileInArchive "ExampleAction.class"
                assertEquals(576, (jar readEntry "ExampleAction.class").length)

                jar containsFileInArchive "Main.class"
                assertEquals(607, (jar readEntry "Main.class").length)

                jar containsFileInArchive "MainKt.class"
                assertEquals(955, (jar readEntry "MainKt.class").length)
            }

            assert(buildDirectory.resolve("instrumented").notExists())
        }
    }

    @Test
    fun `produce instrumented artifact when instrumentation is enabled`() {
        disableDebug()

        build(
            Tasks.BUILD_PLUGIN,
            args = defaultArgs,
            projectProperties = defaultProjectProperties + mapOf("instrumentCode" to true),
        ) {
            output notContainsText "> Task :instrumentCode SKIPPED"

            buildDirectory containsFile "libs/test-1.0.0-instrumented.jar"

            buildDirectory.resolve("instrumented/instrumentCode").let {
                it.resolve("ExampleAction.class").let { file ->
                    assertExists(file)
                    assertEquals(979, file.fileSize())
                }
                it.resolve("Main.class").let { file ->
                    assertExists(file)
                    assertEquals(964, file.fileSize())
                }
                it.resolve("MainKt.class").let { file ->
                    assertExists(file)
                    assertEquals(957, file.fileSize())
                }
            }

            buildDirectory.resolve("libs/test-1.0.0-instrumented.jar").let { jar ->
                jar containsFileInArchive "META-INF/MANIFEST.MF"

                jar containsFileInArchive "META-INF/plugin.xml"
                jar readEntry "META-INF/plugin.xml" containsText "<action id=\"ExampleAction\" class=\"ExampleAction\" text=\"Example Action\">"

                jar containsFileInArchive "ExampleAction.class"
                assertEquals(979, (jar readEntry "ExampleAction.class").length)

                jar containsFileInArchive "Main.class"
                assertEquals(964, (jar readEntry "Main.class").length)

                jar containsFileInArchive "MainKt.class"
                assertEquals(955, (jar readEntry "MainKt.class").length)
            }

            buildDirectory.resolve("instrumented").let {
                assert(it.isDirectory())
                assert(it.listDirectoryEntries().isNotEmpty())
            }
        }
    }

    @Test
    fun `run tests and print nulls when instrumentation is disabled`() {
        build(
            Tasks.External.TEST,
            args = defaultArgs,
            projectProperties = defaultProjectProperties + mapOf("instrumentCode" to false),
        ) {
            output containsText """
                InstrumentationTests > fooTest STANDARD_OUT
                    null
            """.trimIndent()

            output containsText """
                InstrumentationTests > test STANDARD_OUT
                    null
            """.trimIndent()
        }
    }

    @Test
    fun `run tests and throw unmet assertion exceptions when instrumentation is enabled`() {
        disableDebug()

        buildAndFail(
            "test",
            args = defaultArgs,
            projectProperties = defaultProjectProperties + mapOf("instrumentCode" to true),
        ) {
            output containsText """
                InstrumentationTests > fooTest FAILED
                    java.lang.IllegalArgumentException at InstrumentationTests.java:12
            """.trimIndent()

            output containsText """
                InstrumentationTests > test FAILED
                    java.lang.IllegalArgumentException at InstrumentationTests.java:7
            """.trimIndent()
        }
    }
}
