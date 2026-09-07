// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.BeforeTest
import kotlin.test.Test

private const val ASSEMBLE = "assemble"
private const val CLASSES = "classes"

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
                intellijPlatform {
                    instrumentCode = true
                }
                """.trimIndent()

        writeJavaFile()

        build(ASSEMBLE, args = defaultArgs) {
            assertContains("Added @NotNull assertions to 1 files", output)
        }
    }

    @Test
    fun `instrument tests with nullability annotations`() {
        writeTestFile()

        buildFile write //language=kotlin
                """
                dependencies {
                    testImplementation("junit:junit:4.13.2")
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

        build(ASSEMBLE, args = defaultArgs) {
            assertNotContains("Added @NotNull", output)
        }
    }

    @Test
    fun `do not instrument code on empty source sets`() {
        build(ASSEMBLE, args = defaultArgs) {
            assertNotContains("Compiling forms and instrumenting code", output)
        }
    }

    @Test
    fun `instrument kotlin forms`() {
        writeKotlinUIFile()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    instrumentCode = true
                }
                """.trimIndent()

        dir.resolve("src/main/kotlin/pack/AppKt.form") write //language=xml
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <form xmlns="https://www.intellij.com/uidesigner/form/" version="1" bind-to-class="pack.AppKt">
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

        build(ASSEMBLE, args = defaultArgs) {
            assertContains("Compiling forms and instrumenting code", output)
        }
    }

    @Test
    fun `instrumentation does not invalidate compile tasks`() {
        writeJavaFile()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    instrumentCode = true
                }
                """.trimIndent()

        build(ASSEMBLE)
        build(ASSEMBLE) {
            assertTaskOutcome(CLASSES, TaskOutcome.UP_TO_DATE)
        }
    }

    @Test
    fun `serializes instrumentation tasks within a project`() {
        dir.resolve("src/alpha/java/Alpha.java") write "class Alpha {}"
        dir.resolve("src/beta/java/Beta.java") write "class Beta {}"

        gradleProperties write //language=properties
                """
                org.gradle.configuration-cache.parallel=true
                org.gradle.parallel=true
                """.trimIndent()

        buildFile overwrite //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.extensions.*
                import org.jetbrains.intellij.platform.gradle.tasks.*

                plugins {
                    id("java")
                    id("org.jetbrains.intellij.platform") apply false
                }

                sourceSets.create("alpha")
                sourceSets.create("beta")

                apply(plugin = "org.jetbrains.intellij.platform")

                repositories {
                    mavenCentral()
                    intellijPlatform {
                        defaultRepositories()
                    }
                }

                dependencies {
                    extensions.configure<IntelliJPlatformDependenciesExtension> {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                    }
                }

                extensions.configure<IntelliJPlatformExtension> {
                    buildSearchableOptions = false
                    instrumentCode = true
                }

                val instrumentAlphaCodeMarker = layout.buildDirectory.file("instrumentAlphaCode.running")
                val instrumentBetaCodeMarker = layout.buildDirectory.file("instrumentBetaCode.running")

                fun InstrumentCodeTask.failOnOverlap(marker: Provider<RegularFile>, otherMarker: Provider<RegularFile>) {
                    this.doFirst {
                        marker.get().asFile.apply {
                            parentFile.mkdirs()
                            createNewFile()
                        }
                        repeat(20) {
                            check(!otherMarker.get().asFile.exists()) {
                                "Instrumentation tasks from the same project overlapped"
                            }
                            Thread.sleep(100)
                        }
                    }
                    this.doLast {
                        marker.get().asFile.delete()
                    }
                }

                tasks.named<InstrumentCodeTask>("instrumentAlphaCode") {
                    failOnOverlap(instrumentAlphaCodeMarker, instrumentBetaCodeMarker)
                }
                tasks.named<InstrumentCodeTask>("instrumentBetaCode") {
                    failOnOverlap(instrumentBetaCodeMarker, instrumentAlphaCodeMarker)
                }
                """.trimIndent()

        val args = listOf("--parallel", "--max-workers=2")
        buildWithConfigurationCache("clean", "instrumentAlphaCode", "instrumentBetaCode", args = args)
        buildWithConfigurationCache("clean", "instrumentAlphaCode", "instrumentBetaCode", args = args) {
            assertConfigurationCacheReused()
        }
    }

    @Test
    fun `reuses configuration cache`() {
        buildWithConfigurationCache(ASSEMBLE, args = defaultArgs)

        buildWithConfigurationCache(ASSEMBLE, args = defaultArgs) {
            assertConfigurationCacheReused()
        }
    }
}
