// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IntelliJInstrumentCodeTaskTest : IntelliJPluginTestBase() {

    private val defaultArgs = listOf("--info")

    @BeforeTest
    override fun setup() {
        disableDebug()

        super.setup()
    }

    @Test
    fun `instrument code with nullability annotations`() {
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        instrumentationTools()
                    }
                }
                
                intellijPlatform {
                    instrumentCode = true
                }
                """.trimIndent()

        writeJavaFile()

        build(Tasks.External.ASSEMBLE, args = defaultArgs) {
            assertContains("Added @NotNull assertions to 1 files", output)
        }
    }

    @Test
    fun `instrument tests with nullability annotations`() {
        writeTestFile()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        instrumentationTools()
                    }
                }
                
                intellijPlatform {
                    instrumentCode = true
                }
                """.trimIndent()

        build(Tasks.External.TEST, args = defaultArgs) {
            assertContains("Added @NotNull assertions to 1 files", output)
        }
    }

    @Test
    fun `do not instrument code if option is set to false`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        writeJavaFile()

        build(Tasks.External.ASSEMBLE, args = defaultArgs) {
            assertNotContains("Added @NotNull", output)
        }
    }

    @Test
    fun `do not instrument code on empty source sets`() {
        build(Tasks.External.ASSEMBLE, args = defaultArgs) {
            assertNotContains("Compiling forms and instrumenting code", output)
        }
    }

    @Test
    fun `instrument kotlin forms`() {
        writeKotlinUIFile()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        instrumentationTools()
                    }
                }
                
                intellijPlatform {
                    instrumentCode = true
                }
                """.trimIndent()

        dir.resolve("src/main/kotlin/pack/AppKt.form") write //language=xml
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

        build(Tasks.External.ASSEMBLE, args = defaultArgs) {
            assertContains("Compiling forms and instrumenting code", output)
        }
    }

    @Test
    fun `instrumentation does not invalidate compile tasks`() {
        writeJavaFile()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        instrumentationTools()
                    }
                }
                
                intellijPlatform {
                    instrumentCode = true
                }
                """.trimIndent()

        build(Tasks.External.ASSEMBLE)
        build(Tasks.External.ASSEMBLE) {
            assertTaskOutcome(Tasks.External.CLASSES, TaskOutcome.UP_TO_DATE)
        }
    }
}
