// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.readText
import kotlin.test.Test

class BuildSearchableOptionsTaskTest : SearchableOptionsTestBase() {

    @Test
    fun `build searchable options produces XML`() {
        pluginXml write getPluginXmlWithSearchableConfigurable()

        getTestSearchableConfigurableJava() write getSearchableConfigurableCode()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = true
                }
                """.trimIndent()

        build(Tasks.BUILD_SEARCHABLE_OPTIONS) {
            assertContains("Found 391 configurables", output)
        }

        val xml = buildDirectory.resolve("tmp/${Tasks.BUILD_SEARCHABLE_OPTIONS}/p-PluginName-searchableOptions.json")
        assertExists(xml)

        xml.readText().let {
            assertContains("test.searchable.configurable", it)
            assertContains("Test Searchable Configurable", it)
            assertContains("\"hit\":\"Label for Test Searchable Configurable\"", it)
        }
    }

    @Test
    fun `skip build searchable options if no Configurable EPs are present`() {
        pluginXml write getPluginXmlWithoutSearchableConfigurable()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = true
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN) {
            assertTaskOutcome(Tasks.BUILD_SEARCHABLE_OPTIONS, TaskOutcome.SKIPPED)
            assertTaskOutcome(Tasks.JAR_SEARCHABLE_OPTIONS, TaskOutcome.SKIPPED)
        }
    }

    @Test
    fun `skip build searchable options if disabled via extension`() {
        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = false
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN) {
            assertTaskOutcome(Tasks.BUILD_SEARCHABLE_OPTIONS, TaskOutcome.SKIPPED)
            assertTaskOutcome(Tasks.JAR_SEARCHABLE_OPTIONS, TaskOutcome.SKIPPED)
        }
    }

    @Test
    fun `build searchable options if forced via property without Configurable EPs`() {
        pluginXml write getPluginXmlWithoutSearchableConfigurable()

        gradleProperties write //language=properties
                """
                ${GradleProperties.ForceBuildSearchableOptions} = true
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = false
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN) {
            assertTaskOutcome(Tasks.BUILD_SEARCHABLE_OPTIONS, TaskOutcome.SUCCESS)
            assertTaskOutcome(Tasks.JAR_SEARCHABLE_OPTIONS, TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `build searchable options produces XML if enabled via property and explicitly configured`() {
        pluginXml write getPluginXmlWithSearchableConfigurable()

        getTestSearchableConfigurableJava() write getSearchableConfigurableCode()

        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.buildSearchableOptions = true
                """.trimIndent()


        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = true
                }
                """.trimIndent()

        build(Tasks.BUILD_SEARCHABLE_OPTIONS) {
            assertContains("Found 391 configurables", output)
        }

        val xml = buildDirectory.resolve("tmp/${Tasks.BUILD_SEARCHABLE_OPTIONS}/p-PluginName-searchableOptions.json")
        assertExists(xml)

        xml.readText().let {
            assertContains("test.searchable.configurable", it)
            assertContains("Test Searchable Configurable", it)
            assertContains("\"hit\":\"Label for Test Searchable Configurable\"", it)
        }
    }

    @Test
    fun `build searchable options when Configurable EP is declared in submodule xml`() {
        pluginXml write getPluginXmlWithoutSearchableConfigurable()

        settingsFile overwrite //language=kotlin
                """
                rootProject.name = "projectName"

                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
                }

                include("submodule")
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginComposedModule(implementation(project(":submodule")))
                    }
                }

                intellijPlatform {
                    buildSearchableOptions = true
                }
                """.trimIndent()

        dir.resolve("submodule/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.kotlin.jvm")
                    id("org.jetbrains.intellij.platform.module")
                }
                
                kotlin {
                    jvmToolchain(21)
                }
                
                repositories {
                    mavenCentral()
                
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        val useInstaller = providers.gradleProperty("intellijPlatform.useInstaller").orElse("true").map { it.toBoolean() }
                        val type = providers.gradleProperty("intellijPlatform.type").orElse("$intellijPlatformType")
                        val version = providers.gradleProperty("intellijPlatform.version").orElse("$intellijPlatformVersion")
                
                        create(type, version) { this.useInstaller.set(useInstaller) }
                    }
                }

                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        dir.resolve("submodule/src/main/resources/submodule.xml") write //language=xml
                """
                <idea-plugin>
                    <extensions defaultExtensionNs="com.intellij">
                        <projectConfigurable instance="SubmoduleSearchableConfigurable"/>
                    </extensions>
                </idea-plugin>
                """.trimIndent()

        dir.resolve("submodule/src/main/java/SubmoduleSearchableConfigurable.java") write //language=java
                """
                import com.intellij.openapi.options.SearchableConfigurable;
                import org.jetbrains.annotations.Nls;
                import org.jetbrains.annotations.NotNull;
                import org.jetbrains.annotations.Nullable;
                
                import javax.swing.*;
                
                public class SubmoduleSearchableConfigurable implements SearchableConfigurable {
                
                    @NotNull
                    @Override
                    public String getId() {
                        return "submodule.searchable.configurable";
                    }
                
                    @Nls(capitalization = Nls.Capitalization.Title)
                    @Override
                    public String getDisplayName() {
                        return "Submodule Searchable Configurable";
                    }
                
                    @Nullable
                    @Override
                    public JComponent createComponent() {
                        return new JLabel("Label for Submodule Searchable Configurable");
                    }
                
                    @Override
                    public boolean isModified() {
                        return false;
                    }
                
                    @Override
                    public void apply() {
                    }
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN) {
            assertTaskOutcome(Tasks.BUILD_SEARCHABLE_OPTIONS, TaskOutcome.SUCCESS)
            assertTaskOutcome(Tasks.JAR_SEARCHABLE_OPTIONS, TaskOutcome.SUCCESS)
        }
    }

    @Test
    fun `skip build searchable options if disabled via property and explicitly configured`() {

        gradleProperties write //language=properties
                """
                org.jetbrains.intellij.platform.buildSearchableOptions = false
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = providers.gradleProperty("org.jetbrains.intellij.platform.buildSearchableOptions").map {
                        it.toBoolean()
                    }
                }
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN) {
            assertTaskOutcome(Tasks.BUILD_SEARCHABLE_OPTIONS, TaskOutcome.SKIPPED)
            assertTaskOutcome(Tasks.JAR_SEARCHABLE_OPTIONS, TaskOutcome.SKIPPED)
        }
    }

    @Test
    fun `reuses configuration cache`() {
        pluginXml write getPluginXmlWithSearchableConfigurable()
        getTestSearchableConfigurableJava() write getSearchableConfigurableCode()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    buildSearchableOptions = true
                }
                """.trimIndent()

        buildWithConfigurationCache(Tasks.BUILD_SEARCHABLE_OPTIONS)

        buildWithConfigurationCache(Tasks.BUILD_SEARCHABLE_OPTIONS) {
            assertConfigurationCacheReused()
        }
    }
}
