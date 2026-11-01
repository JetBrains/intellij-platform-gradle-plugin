// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.assertContains
import org.jetbrains.intellij.platform.gradle.assertNotContains
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.gradleProperties
import org.jetbrains.intellij.platform.gradle.overwrite
import org.jetbrains.intellij.platform.gradle.pluginXml
import org.jetbrains.intellij.platform.gradle.write
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.outputStream
import kotlin.test.Test
import kotlin.test.assertTrue

class TestIdeTaskTest : IntelliJPluginTestBase() {

    @Test
    fun `test task sets idea home path to target IntelliJ Platform`() {
        buildFile write //language=kotlin
                """
                tasks.register("verifyTestIdeaHomePath") {
                    doLast {
                        val arguments = tasks.named<org.gradle.api.tasks.testing.Test>("test")
                            .get()
                            .jvmArgumentProviders
                            .flatMap { it.asArguments() }
                                
                        val ideaHomePath = arguments
                            .single { it.startsWith("-Didea.home.path=") }
                            .substringAfter("=")
                            .let(::file)
                    
                        check(ideaHomePath.isDirectory) { "idea.home.path does not point to a directory: ${'$'}ideaHomePath" }
                        check(ideaHomePath.resolve("product-info.json").isFile || ideaHomePath.resolve("Resources/product-info.json").isFile) {
                            "idea.home.path does not point to an IntelliJ Platform home: ${'$'}ideaHomePath"
                        }
                
                        println("IDEA_HOME_PATH=${'$'}ideaHomePath")
                    }
                }
                """.trimIndent()

        build("verifyTestIdeaHomePath") {
            assertContains("IDEA_HOME_PATH=", output)
        }
    }

    @Test
    fun `bundled plugins are added to the classpath`() {
        buildFile write //language=kotlin
                """
                tasks.register("printBundledPluginsClasspath") {
                    doLast {
                        val testTask = tasks.named<org.gradle.api.tasks.testing.Test>("test").get()
                        val ideaHomePath = testTask.jvmArgumentProviders
                            .flatMap { it.asArguments() }
                            .single { it.startsWith("-Didea.home.path=") }
                            .substringAfter("=")
                            .let(::file)
                        val pluginsDirectories = listOf(
                            ideaHomePath.resolve("plugins"),
                            ideaHomePath.resolve("Contents/plugins"),
                        ).filter { it.isDirectory }

                        testTask.classpath.files
                            .filter { classpathEntry ->
                                pluginsDirectories.any { classpathEntry.toPath().startsWith(it.toPath()) }
                            }
                            .map { it.canonicalPath }
                            .sorted()
                            .forEach { println("BUNDLED_PLUGIN_CLASSPATH=${'$'}it") }
                    }
                }
                """.trimIndent()

        val classpath = build("printBundledPluginsClasspath").output.bundledPluginsClasspathEntries()

        assertTrue(
            classpath.isNotEmpty(),
            "The bundled plugins classpath should contain entries from bundled plugins",
        )
    }

    @Test
    fun `bundled plugins required by the tested plugin are kept on the classpath despite being excluded`() {
        val yamlPluginId = "org.jetbrains.plugins.yaml"
        val keptMessage = "Bundled plugin '$yamlPluginId' is kept on the test classpath despite being listed in " +
            "'${GradleProperties.TestIdeBundledPluginsClasspathExcludes}'"

        gradleProperties write //language=properties
                """
                ${GradleProperties.TestIdeBundledPluginsClasspathExcludes}=$yamlPluginId
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks.register("printYamlPluginClasspath") {
                    // The tested plugin descriptor is read from the test sandbox
                    dependsOn("${Tasks.PREPARE_TEST_SANDBOX}")

                    doLast {
                        tasks.named<org.gradle.api.tasks.testing.Test>("test").get().classpath.files
                            .map { it.invariantSeparatorsPath }
                            .filter { it.contains("/plugins/yaml/") }
                            .forEach { println("YAML_PLUGIN_CLASSPATH=${'$'}it") }
                    }
                }
                """.trimIndent()

        fun writePluginXml(depends: String) = pluginXml overwrite //language=xml
                """
                <idea-plugin>
                    <id>test</id>
                    <name>Test</name>
                    <vendor>JetBrains</vendor>
                    <depends>com.intellij.modules.platform</depends>
                    $depends
                </idea-plugin>
                """.trimIndent()

        writePluginXml(depends = "<depends optional=\"true\" config-file=\"yaml.xml\">$yamlPluginId</depends>")
        dir.resolve("src/main/resources/META-INF/yaml.xml") write "<idea-plugin/>"

        build("printYamlPluginClasspath") {
            assertNotContains("YAML_PLUGIN_CLASSPATH=", output)
            assertNotContains(keptMessage, output)
        }

        writePluginXml(depends = "<depends>$yamlPluginId</depends>")

        build("printYamlPluginClasspath") {
            assertContains("YAML_PLUGIN_CLASSPATH=", output)
            assertContains(keptMessage, output)
        }
    }

    @Test
    fun `bundled dependencies of required split-JAR modules in local plugins are kept on the classpath`() {
        val yamlPluginId = "org.jetbrains.plugins.yaml"
        val entries = mapOf(
            "lib/plugin.jar" to ("META-INF/plugin.xml" to """
                <idea-plugin>
                    <id>local.dependency</id>
                    <name>Local Dependency</name>
                    <version>1</version>
                    <vendor>JetBrains</vendor>
                    <content><module name="required.module" loading="required" /></content>
                </idea-plugin>
            """.trimIndent()),
            "lib/modules/required.module.jar" to ("required.module.xml" to """
                <idea-plugin>
                    <dependencies><plugin id="$yamlPluginId" /></dependencies>
                </idea-plugin>
            """.trimIndent()),
        )
        entries.forEach { (path, entry) ->
            val jar = dir.resolve("dependency/$path")
            jar.parent.createDirectories()
            ZipOutputStream(jar.outputStream()).use { zip ->
                zip.putNextEntry(ZipEntry(entry.first))
                zip.write(entry.second.toByteArray())
                zip.closeEntry()
            }
        }
        gradleProperties write "${GradleProperties.TestIdeBundledPluginsClasspathExcludes}=$yamlPluginId"
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        localPlugin(file("dependency"))
                    }
                }

                tasks.register("printSplitPluginDependencyClasspath") {
                    dependsOn("${Tasks.PREPARE_TEST_SANDBOX}")
                    doLast {
                        tasks.named<org.gradle.api.tasks.testing.Test>("test").get().classpath.files
                            .map { it.invariantSeparatorsPath }
                            .filter { it.contains("/plugins/yaml/") }
                            .forEach { println("YAML_PLUGIN_CLASSPATH=${'$'}it") }
                    }
                }
                """.trimIndent()

        build("printSplitPluginDependencyClasspath") {
            assertContains("YAML_PLUGIN_CLASSPATH=", output)
            assertContains("Bundled plugin '$yamlPluginId' is kept on the test classpath despite being listed in", output)
        }
    }

    private fun String.bundledPluginsClasspathEntries() = lineSequence()
        .filter { it.startsWith("BUNDLED_PLUGIN_CLASSPATH=") }
        .map { it.substringAfter('=') }
        .toSet()
}
