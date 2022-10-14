// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertEquals

@Suppress("ComplexRedundantLet")
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

        build("buildSourceSet", "--info").let {
            assertContains("Added @NotNull assertions to 1 files", it.output)
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

        build("buildTestSourceSet", "--info").let {
            assertContains("Added @NotNull assertions to 1 files", it.output)
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

        build("buildSourceSet", "--info").let {
            assertNotContains("Added @NotNull", it.output)
        }
    }

    @Test
    fun `do not instrument code on empty source sets`() {
        build("buildSourceSet", "--info").let {
            assertNotContains("Compiling forms and instrumenting code", it.output)
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

        file("src/main/kotlin/pack/AppKt.form").xml(
            """<?xml version="1.0" encoding="UTF-8"?>
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

        build("buildSourceSet", "--info").let {
            assertContains("Compiling forms and instrumenting code", it.output)
        }
    }

    @Test
    fun `instrumentation does not invalidate compile tasks`() {
        writeJavaFile()
        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildSourceSet")

        build("buildSourceSet").let {
            assertEquals(TaskOutcome.UP_TO_DATE, it.task(":${JavaPlugin.CLASSES_TASK_NAME}")?.outcome)
        }
    }

    @Test
    fun `reuse configuration cache`() {
        writeJavaFile()

        buildFile.groovy(
            """
            intellij {
                instrumentCode = true
            }
            """.trimIndent()
        )

        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildSourceSet")
        build("buildSourceSet").let {
            assertContains("Reusing configuration cache.", it.output)
        }
    }
}
