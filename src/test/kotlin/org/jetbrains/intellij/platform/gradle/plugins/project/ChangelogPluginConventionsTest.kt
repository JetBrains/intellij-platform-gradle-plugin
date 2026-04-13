// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class ChangelogPluginConventionsTest : IntelliJPluginTestBase() {

    @Test
    fun `apply changelog conventions when plugin is present`() {
        pluginXml overwrite //language=xml
                """
                <idea-plugin />
                """.trimIndent()
        writeChangelog()
        applyChangelogPlugin()

        buildFile write //language=kotlin
                """
                tasks.register("printChangelogConventions") {
                    doLast {
                        val changelog = project.extensions.getByType(org.jetbrains.changelog.ChangelogPluginExtension::class.java)
                
                        println("groups=${'$'}{changelog.groups.get()}")
                        println("repositoryUrl=${'$'}{changelog.repositoryUrl.orNull}")
                        println("versionPrefix=${'$'}{changelog.versionPrefix.orNull}")
                        println("changeNotes=${'$'}{intellijPlatform.pluginConfiguration.changeNotes.get()}")
                    }
                }
                """.trimIndent()

        build(
            "printChangelogConventions",
            projectProperties = mapOf("pluginRepositoryUrl" to "https://example.com/repository"),
        ) {
            assertContains("groups=[]", output)
            assertContains("repositoryUrl=https://example.com/repository", output)
            assertContains("versionPrefix=", output)
            assertContains("<li>Initial release</li>", output)
        }
    }

    @Test
    fun `allow overriding changelog conventions`() {
        pluginXml overwrite //language=xml
                """
                <idea-plugin />
                """.trimIndent()
        writeChangelog()
        applyChangelogPlugin()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        changeNotes = "custom notes"
                    }
                }
                
                extensions.getByType(org.jetbrains.changelog.ChangelogPluginExtension::class.java).apply {
                    groups.set(listOf("Custom"))
                    repositoryUrl.set("https://example.com/custom")
                    versionPrefix.set("v")
                }
                
                tasks.register("printChangelogConventions") {
                    doLast {
                        val changelog = project.extensions.getByType(org.jetbrains.changelog.ChangelogPluginExtension::class.java)
                
                        println("groups=${'$'}{changelog.groups.get()}")
                        println("repositoryUrl=${'$'}{changelog.repositoryUrl.orNull}")
                        println("versionPrefix=${'$'}{changelog.versionPrefix.orNull}")
                        println("changeNotes=${'$'}{intellijPlatform.pluginConfiguration.changeNotes.get()}")
                    }
                }
                """.trimIndent()

        build("printChangelogConventions") {
            assertContains("groups=[Custom]", output)
            assertContains("repositoryUrl=https://example.com/custom", output)
            assertContains("versionPrefix=v", output)
            assertContains("changeNotes=custom notes", output)
        }
    }

    @Test
    fun `publish plugin depends on patch changelog when plugin is present`() {
        pluginXml overwrite //language=xml
                """
                <idea-plugin>
                    <name>PluginName</name>
                    <version>0.0.1</version>
                    <vendor>JetBrains</vendor>
                    <depends>com.intellij.modules.platform</depends>
                </idea-plugin>
                """.trimIndent()
        writeChangelog()
        applyChangelogPlugin()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    publishing {}
                }
                """.trimIndent()

        buildAndFail(Tasks.PUBLISH_PLUGIN, environment = pluginTemplateEnvironment()) {
            assertTaskOutcome("patchChangelog", TaskOutcome.SUCCESS)
            assertContains("'token' property must be specified for plugin publishing", output)
        }
    }

    private fun applyChangelogPlugin() {
        buildFile.prepend(
            //language=kotlin
            """
            buildscript {
                repositories {
                    gradlePluginPortal()
                    mavenCentral()
                }
                
                dependencies {
                    classpath("org.jetbrains.intellij.plugins:gradle-changelog-plugin:$CHANGELOG_PLUGIN_VERSION")
                }
            }
            """.trimIndent()
        )
        buildFile write //language=kotlin
                """
                apply(plugin = "${Constants.Plugins.External.CHANGELOG}")
                """.trimIndent()
    }

    private fun writeChangelog() {
        dir.resolve("CHANGELOG.md") overwrite //language=markdown
                """
                # Changelog
                
                ## [Unreleased]
                
                ### Added
                - Initial release
                """.trimIndent()
    }

    companion object {
        private const val CHANGELOG_PLUGIN_VERSION = "2.5.0"
    }
}
