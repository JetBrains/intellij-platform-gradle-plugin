// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.appendText
import kotlin.io.path.fileSize
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class InstrumentationTaskIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "instrumentation-task",
) {

    private val defaultArgs = listOf("--configuration-cache", "--info")

    @BeforeTest
    override fun setup() {
        super.setup()

        disableDebug()
    }

    @Test
    fun `instrument NotNull annotations`() {
        build(
            Tasks.BUILD_PLUGIN,
            args = defaultArgs,
            projectProperties = defaultProjectProperties,
        ) {
            output containsText "[ant:instrumentIdeaExtensions] Added @NotNull assertions to 4 files"
        }
    }

    @Test
    fun `produce instrumented jar`() {
        build(
            Tasks.BUILD_PLUGIN,
            args = defaultArgs,
            projectProperties = defaultProjectProperties,
        ) {
            buildDirectory.resolve("classes/java/main/Main.class").let {
                assertExists(it)
                assertEquals(607, it.fileSize())
            }
            buildDirectory.resolve("classes/java/main/CustomMain.class").let {
                assertExists(it)
                assertEquals(632, it.fileSize())
            }
            buildDirectory.resolve("tmp/instrumentCode/Main.class").let {
                assertExists(it)
                assertEquals(964, it.fileSize())
            }
            buildDirectory.resolve("classes/java/main/Form.class").let {
                assertExists(it)
                assertEquals(433, it.fileSize())
            }
            buildDirectory.resolve("tmp/instrumentCode/Form.class").let {
                assertExists(it)
                assertEquals(1220, it.fileSize())
            }
            buildDirectory.resolve("classes/kotlin/main/MainKt.class").let {
                assertExists(it)
                assertEquals(782, it.fileSize())
            }

            buildDirectory.resolve("idea-sandbox/IC-2022.3.3/plugins/test/lib/test-1.0.0.jar").let { jar ->
                jar containsFileInArchive "Main.class"
                assertEquals(964, (jar readEntry "Main.class").length)

                jar containsFileInArchive "Form.class"
                assertEquals(1220, (jar readEntry "Form.class").length)

                jar containsFileInArchive "MainKt.class"
                assertEquals(1174, (jar readEntry "MainKt.class").length)

                jar containsFileInArchive "CustomMain.class"
                assertEquals(989, (jar readEntry "CustomMain.class").length)
            }

            buildDirectory.resolve("idea-sandbox/IC-2022.3.3/plugins/test/lib/submodule-1.0.0.jar").let { jar ->
                jar containsFileInArchive "FormSub.class"
                assertEquals(1229, (jar readEntry "FormSub.class").length)

                jar containsFileInArchive "MainSub.class"
                assertEquals(977, (jar readEntry "MainSub.class").length)

                jar containsFileInArchive "MyProjectService.class"
                assertEquals(578, (jar readEntry "MyProjectService.class").length)
            }
        }
    }

    @Test
    fun `test incremental build`() {
        build(
            Tasks.BUILD_PLUGIN,
            projectProperties = defaultProjectProperties,
        )

        build(
            Tasks.External.JAR,
            args = defaultArgs,
            projectProperties = defaultProjectProperties,
        ) {
            output containsText "> Task :compileKotlin UP-TO-DATE"
            output containsText "> Task :compileJava UP-TO-DATE"
        }

        dir.resolve("src/main/kotlin/MainKt.kt").appendText("// foo\n")

        build(
            Tasks.External.JAR,
            args = defaultArgs,
            projectProperties = defaultProjectProperties,
        ) {
            output containsText "Task ':compileKotlin' is not up-to-date"
            output containsText "> Task :compileJava UP-TO-DATE"
        }

        dir.resolve("src/main/java/Main.java").appendText("// foo\n")

        build(
            Tasks.External.JAR,
            args = defaultArgs,
            projectProperties = defaultProjectProperties,
        ) {
            output containsText "Task ':compileKotlin' is not up-to-date"
            output containsText "Task ':compileJava' is not up-to-date"
        }

        dir.resolve("src/main/java/Form.form").appendText("<!-- foo -->\n")

        build(
            Tasks.External.JAR,
            args = defaultArgs,
            projectProperties = defaultProjectProperties,
        ) {
            output containsText "> Task :compileKotlin UP-TO-DATE"
            output containsText "> Task :compileJava UP-TO-DATE"
            output containsText "Form.form has changed"
        }
    }

    @Ignore
    @Test
    fun `fail on null with instrumented code not NPE`() {
        buildAndFail(
            "test",
            args = defaultArgs,
            projectProperties = defaultProjectProperties,
        ) {
            val testResultsFile = buildDirectory.resolve("test-results/test/TEST-InstrumentationTests.xml")
            assertExists(testResultsFile)

            output containsText "InstrumentationTests > fooTest FAILED"
            testResultsFile containsText "Argument for @NotNull parameter 's' of TestFoo.testFoo must not be null"

            output containsText "InstrumentationTests > test FAILED"
            testResultsFile containsText "Argument for @NotNull parameter 's' of Foo.foo must not be null"
        }
    }
}
