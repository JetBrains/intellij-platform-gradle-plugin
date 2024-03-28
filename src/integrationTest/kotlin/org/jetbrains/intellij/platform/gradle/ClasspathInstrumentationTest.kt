// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class ClasspathInstrumentationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "classpath",
) {

    private val defaultProjectProperties = mapOf(
        "intellijPlatform.version" to intellijPlatformVersion,
        "intellijPlatform.type" to intellijPlatformType,
        "markdownPlugin.version" to markdownPluginVersion,
    )

    @Test
    fun `dependencies should contain IntelliJ Platform and Markdown plugin`() {
        build(Tasks.External.DEPENDENCIES, projectProperties = defaultProjectProperties) {
            safeLogs containsText """
                compileClasspath - Compile classpath for null/main.
                +--- com.jetbrains.intellij.idea:ideaIC:$intellijPlatformVersion
                \--- com.jetbrains.plugins:org.intellij.plugins.markdown:$markdownPluginVersion
            """.trimIndent()

            safeLogs containsText """
                intellijPlatformDependency - IntelliJ Platform dependency archive
                \--- com.jetbrains.intellij.idea:ideaIC:$intellijPlatformVersion
            """.trimIndent()

            safeLogs containsText """
                intellijPlatformPlugins - IntelliJ Platform plugins
                \--- com.jetbrains.plugins:org.intellij.plugins.markdown:$markdownPluginVersion
            """.trimIndent()

            safeLogs containsText """
                intellijPlatformJavaCompiler - Java Compiler used by Ant tasks
                \--- com.jetbrains.intellij.java:java-compiler-ant-tasks:223.8836.41
                     +--- com.jetbrains.intellij.java:java-gui-forms-compiler:223.8836.41
                     |    +--- com.jetbrains.intellij.platform:util-jdom:223.8836.41
                     |    |    +--- jaxen:jaxen:1.2.0
                     |    |    \--- org.jetbrains:annotations:23.0.0
                     |    +--- com.jetbrains.intellij.java:java-gui-forms-rt:223.8836.41
                     |    +--- org.jetbrains.intellij.deps:asm-all:9.2
                     |    +--- com.jgoodies:forms:1.1-preview
                     |    +--- com.jetbrains.intellij.java:java-compiler-instrumentation-util:223.8836.41
                     |    |    \--- org.jetbrains.intellij.deps:asm-all:9.2
                     |    \--- org.jetbrains:annotations:23.0.0
                     +--- com.jetbrains.intellij.java:java-gui-forms-rt:223.8836.41
                     +--- org.jetbrains.intellij.deps:asm-all:9.2
                     +--- com.jetbrains.intellij.java:java-compiler-instrumentation-util:223.8836.41 (*)
                     \--- com.jetbrains.intellij.java:java-compiler-instrumentation-util-java8:223.8836.41
                          \--- com.jetbrains.intellij.java:java-compiler-instrumentation-util:223.8836.41 (*)
            """.trimIndent()
        }
    }

    @Test
    fun `jacoco should work`() {
        disableDebug("Gradle runs Ant with another Java, that leads to NoSuchMethodError during the instrumentation")

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
}
