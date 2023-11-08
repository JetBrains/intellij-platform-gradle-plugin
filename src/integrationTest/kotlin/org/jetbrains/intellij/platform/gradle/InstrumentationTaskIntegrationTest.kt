// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import java.nio.file.Files
import kotlin.io.path.appendText
import kotlin.test.Test

class InstrumentationTaskIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "instrumentation-task",
) {

    private val defaultArgs = listOf("--configuration-cache", "--info")

    @Test
    fun `instrument NotNull annotations`() {
        build("buildPlugin", args = defaultArgs).let {
            it.output containsText "[ant:instrumentIdeaExtensions] Added @NotNull assertions to 4 files"
        }
    }

    @Test
    fun `produce instrumented jar`() {
        build("buildPlugin", args = defaultArgs).let {
            buildDirectory.resolve("classes/java/main/Main.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 658L)
            }
            buildDirectory.resolve("classes/java/main/CustomMain.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 683L)
            }
            buildDirectory.resolve("tmp/instrumentCode/Main.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 1015L)
            }
            buildDirectory.resolve("classes/java/main/Form.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 482L)
            }
            buildDirectory.resolve("tmp/instrumentCode/Form.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 1269L)
            }
            buildDirectory.resolve("classes/kotlin/main/MainKt.class").run {
                assert(Files.exists(this))
                assert(Files.size(this) == 782L)
            }

            buildDirectory.resolve("libs/instrumented-test-1.0.0.jar").let { jar ->
                jar containsFileInArchive "Main.class"
                assert((jar readEntry "Main.class").length == 1015)

                jar containsFileInArchive "Form.class"
                assert((jar readEntry "Form.class").length == 1269)

                jar containsFileInArchive "MainKt.class"
                assert((jar readEntry "MainKt.class").length == 1174)

                jar containsFileInArchive "CustomMain.class"
                assert((jar readEntry "CustomMain.class").length == 1040)
            }
        }
    }

    @Test
    fun `test incremental build`() {
        build("buildPlugin")

        build("jar", args = defaultArgs).let {
            it.output containsText "> Task :compileKotlin UP-TO-DATE"
            it.output containsText "> Task :compileJava UP-TO-DATE"
        }

        dir.resolve("src/main/kotlin/MainKt.kt").appendText("// foo\n")

        build("jar", args = defaultArgs).let {
            it.output containsText "Task ':compileKotlin' is not up-to-date"
            it.output containsText "> Task :compileJava UP-TO-DATE"
        }

        dir.resolve("src/main/java/Main.java").appendText("// foo\n")

        build("jar", args = defaultArgs).let {
            it.output containsText "Task ':compileKotlin' is not up-to-date"
            it.output containsText "Task ':compileJava' is not up-to-date"
        }

        dir.resolve("src/main/java/Form.form").appendText("<!-- foo -->\n")

        build("jar", args = defaultArgs).let {
            it.output containsText "> Task :compileKotlin UP-TO-DATE"
            it.output containsText "> Task :compileJava UP-TO-DATE"
            it.output containsText "Form.form has changed"
        }
    }

    @Test
    fun `fail on null with instrumented code not NPE`() {
        buildAndFail("test", args = defaultArgs).let {
            it.output containsText "InstrumentationTests > fooTest FAILED"
            it.output containsText "Argument for @NotNull parameter 's' of TestFoo.testFoo must not be null"

            it.output containsText "InstrumentationTests > test FAILED"
            it.output containsText "Argument for @NotNull parameter 's' of Foo.foo must not be null"
        }
    }
}
