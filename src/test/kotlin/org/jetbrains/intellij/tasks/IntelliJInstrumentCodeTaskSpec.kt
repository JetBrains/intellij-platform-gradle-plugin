// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.IntelliJPluginSpecBase
import kotlin.test.Test
import kotlin.test.assertEquals

class IntelliJInstrumentCodeTaskSpec : IntelliJPluginSpecBase() {

    @Test
    fun `instrument code with nullability annotations`() {
        buildFile.groovy("""
            intellij {
                instrumentCode = true
            }
        """)

        writeJavaFile()

        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        val result = build("buildSourceSet", "--info")
        assertContains("Added @NotNull assertions to 1 files", result.output)
    }

    @Test
    fun `instrument tests with nullability annotations`() {
        writeTestFile()

        buildFile.groovy("""
            intellij {
                instrumentCode = true
            }
        """)
        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        val result = build("buildTestSourceSet", "--info")
        assertContains("Added @NotNull assertions to 1 files", result.output)
    }

    @Test
    fun `do not instrument code if option is set to false`() {
        buildFile.groovy("""
            intellij {
                instrumentCode = false
            }
        """)

        writeJavaFile()

        val result = build("buildSourceSet", "--info")
        assertNotContains("Added @NotNull", result.output)
    }

    @Test
    fun `do not instrument code on empty source sets`() {
        val result = build("buildSourceSet", "--info")
        assertNotContains("Compiling forms and instrumenting code", result.output)
    }

    @Test
    fun `instrument kotlin forms`() {
        writeKotlinUIFile()

        buildFile.groovy("""
            intellij {
                instrumentCode = true
            }
        """)

        file("src/main/kotlin/pack/AppKt.form").xml("""<?xml version="1.0" encoding="UTF-8"?>
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
        """)

        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        val result = build("buildSourceSet", "--info")
        assertContains("Compiling forms and instrumenting code", result.output)
    }

    @Test
    fun `instrumentation does not invalidate compile tasks`() {
        writeJavaFile()
        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildSourceSet")

        val result = build("buildSourceSet")
        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":${JavaPlugin.CLASSES_TASK_NAME}")?.outcome)
    }

    @Test
    fun `reuse configuration cache`() {
        writeJavaFile()

        buildFile.groovy("""
            intellij {
                instrumentCode = true
            }
        """)

        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildSourceSet", "--configuration-cache")
        val result = build("buildSourceSet", "--configuration-cache")
        assertContains("Reusing configuration cache.", result.output)
    }
}
