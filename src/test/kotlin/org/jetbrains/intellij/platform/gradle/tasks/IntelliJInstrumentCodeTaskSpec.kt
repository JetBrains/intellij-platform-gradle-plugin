// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

@Ignore
@Deprecated("Instrumentation is not yet available")
class IntelliJInstrumentCodeTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `instrument code with nullability annotations`() {
        buildFile.groovy(
            """
            intellij {
                instrumentCode = true
            }
            """.trimIndent()
        )

        writeJavaFile()

        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildSourceSet", "--info") {
            assertContains("Added @NotNull assertions to 1 files", output)
        }
    }

    @Test
    fun `instrument tests with nullability annotations`() {
        writeTestFile()

        buildFile.groovy(
            """
            intellij {
                instrumentCode = true
            }
            """.trimIndent()
        )
        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildTestSourceSet", "--info") {
            assertContains("Added @NotNull assertions to 1 files", output)
        }
    }

    @Test
    fun `do not instrument code if option is set to false`() {
        buildFile.groovy(
            """
            intellij {
                instrumentCode = false
            }
            """.trimIndent()
        )

        writeJavaFile()

        build("buildSourceSet", "--info") {
            assertNotContains("Added @NotNull", output)
        }
    }

    @Test
    fun `do not instrument code on empty source sets`() {
        build("buildSourceSet", "--info") {
            assertNotContains("Compiling forms and instrumenting code", output)
        }
    }

    @Test
    fun `instrument kotlin forms`() {
        writeKotlinUIFile()

        buildFile.groovy(
            """
            intellij {
                instrumentCode = true
            }
            """.trimIndent()
        )

        gradleProperties.properties(
            """
            kotlin.incremental.useClasspathSnapshot=false
            """.trimIndent()
        )

        file("src/main/kotlin/pack/AppKt.form").xml(
            """
            <?xml version="1.0" encoding="UTF-8"?>
            <form xmlns="http://www.intellij.com/uidesigner/form/" version="1" bind-to-class="pack.AppKt">
                <grid id="27dc6" binding="panel" layout-manager="GridLayoutManager" row-count="1" column-count="1" same-size-horizontally="false" same-size-vertically="false" hgap="-1" vgap="-1">
                    <margin top="0" left="0" bottom="0" right="0"/>
                    <constraints>
                        <xy x="20" y="20" width="500" height="400"/>
                    </constraints>
                    <properties/>
                    <border type="none"/>
                    <children/>
                </grid>
            </form>
            """.trimIndent()
        )

        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildSourceSet", "--info") {
            assertContains("Compiling forms and instrumenting code", output)
        }
    }

    @Test
    fun `instrumentation does not invalidate compile tasks`() {
        writeJavaFile()
        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildSourceSet")

        build("buildSourceSet") {
            assertEquals(TaskOutcome.UP_TO_DATE, task(":${JavaPlugin.CLASSES_TASK_NAME}")?.outcome)
        }
    }
}
