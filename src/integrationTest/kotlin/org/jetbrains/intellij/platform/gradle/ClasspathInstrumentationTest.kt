// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.Version
import kotlin.test.Test

private const val DEPENDENCIES = "dependencies"

class ClasspathInstrumentationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "classpath",
) {

    override val defaultProjectProperties
        get() = super.defaultProjectProperties + mapOf(
            "markdownPlugin.version" to markdownPluginVersion,
            "instrumentCode" to true,
        )

    @Test
    fun `dependencies should contain IntelliJ Platform and Markdown plugin`() {
        build(DEPENDENCIES, projectProperties = defaultProjectProperties) {
            val type = intellijPlatformType.toIntelliJPlatformType(intellijPlatformVersion)
            val coordinates = requireNotNull(type.installer)

            output containsText """
                intellijPlatformDependencyArchive - IntelliJ Platform dependency archive
                \--- ${coordinates.groupId}:${coordinates.artifactId}:$intellijPlatformVersion
            """.trimIndent()

            output containsText """
                intellijPlatformLocal - IntelliJ Platform local
                No dependencies
            """.trimIndent()

            output containsText """
                intellijPlatformPlugin - IntelliJ Platform plugins
                \--- com.jetbrains.plugins:org.intellij.plugins.markdown:$markdownPluginVersion
            """.trimIndent()

            val major = Version.parse(intellijPlatformBuildNumber).major
            output containsText """
                intellijPlatformJavaCompiler - Java Compiler used by Ant tasks
                \--- com.jetbrains.intellij.java:java-compiler-ant-tasks:{strictly [$major, $intellijPlatformBuildNumber]; prefer $intellijPlatformBuildNumber} -> $intellijPlatformBuildNumber
                     +--- com.jetbrains.intellij.java:java-gui-forms-compiler:$intellijPlatformBuildNumber
                     |    +--- com.jetbrains.intellij.platform:util-jdom:$intellijPlatformBuildNumber
                     |    |    +--- jaxen:jaxen:1.2.0
                     |    |    \--- org.jetbrains:annotations:24.0.0
                     |    +--- com.jetbrains.intellij.java:java-gui-forms-rt:$intellijPlatformBuildNumber
                     |    +--- org.jetbrains.intellij.deps:asm-all:9.6.1
                     |    +--- com.jgoodies:forms:1.1-preview
                     |    +--- com.jetbrains.intellij.java:java-compiler-instrumentation-util:$intellijPlatformBuildNumber
                     |    |    \--- org.jetbrains.intellij.deps:asm-all:9.6.1
                     |    \--- org.jetbrains:annotations:24.0.0
                     +--- com.jetbrains.intellij.java:java-gui-forms-rt:$intellijPlatformBuildNumber
                     +--- org.jetbrains.intellij.deps:asm-all:9.6.1
                     +--- com.jetbrains.intellij.java:java-compiler-instrumentation-util:$intellijPlatformBuildNumber (*)
                     \--- com.jetbrains.intellij.java:java-compiler-instrumentation-util-java8:$intellijPlatformBuildNumber
                          \--- com.jetbrains.intellij.java:java-compiler-instrumentation-util:$intellijPlatformBuildNumber (*)
            """.trimIndent()
        }
    }

    @Test
    fun `jacoco should work`() {
        disableDebug()

        build(Tasks.External.TEST, projectProperties = defaultProjectProperties) {
            buildDirectory.resolve("jacoco/test.exec").let {
                assertExists(it)
            }

            buildDirectory.resolve("reports/jacoco/test/jacocoTestReport.xml").let {
                assertExists(it)

                it containsText """
                    <method name="getRandomNumber" desc="()I" line="7">
                        <counter type="INSTRUCTION" missed="0" covered="2"/>
                        <counter type="LINE" missed="0" covered="1"/>
                        <counter type="COMPLEXITY" missed="0" covered="1"/>
                        <counter type="METHOD" missed="0" covered="1"/>
                    </method>
                """.trimIndent().replace(""">\s+<""".toRegex(), """><""")
            }
        }
    }

    @Test
    fun `attach bundled testFramework_jar`() {
        disableDebug()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        testFramework(TestFrameworkType.Bundled)
                    }
                }
                
                tasks {
                    test {
                        doLast {
                            println(classpath.joinToString("\n"))
                        }
                    }
                }
                """.trimIndent()

        build(Tasks.External.TEST, projectProperties = defaultProjectProperties) {
            output containsText "testFramework.jar"
        }
    }

    @Test
    fun `attach custom bundled library`() {
        disableDebug()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        bundledLibrary("lib/testFramework.jar")
                    }
                }
                
                tasks {
                    test {
                        doLast {
                            println(classpath.joinToString("\n"))
                        }
                    }
                }
                """.trimIndent()

        build(Tasks.External.TEST, projectProperties = defaultProjectProperties) {
            output containsText "testFramework.jar"
        }
    }

    @Test
    fun `dependencies should not contain Java Compiler if instrumentation is disabled`() {
        disableDebug()

        build(DEPENDENCIES, projectProperties = defaultProjectProperties + mapOf("instrumentCode" to false)) {
            output containsText
                    """
                    intellijPlatformJavaCompiler - Java Compiler used by Ant tasks
                    No dependencies
                    """.trimIndent()
        }
    }
}
