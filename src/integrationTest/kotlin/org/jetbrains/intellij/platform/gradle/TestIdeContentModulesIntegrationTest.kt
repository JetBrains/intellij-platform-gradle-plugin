// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.BuildResult
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

private const val CLASSPATH_ENTRY_LABEL = "test-ide-content-modules: Test classpath entry:"
private const val STRUCTURE_VIEW_PLUGIN_JAR = "plugins/platform-structureView-plugin/lib/platform-structureView-plugin.jar"

class TestIdeContentModulesIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "test-ide-content-modules",
) {

    // The IDE loads content modules only with their plugins since 2026.2
    override val defaultProjectProperties
        get() = super.defaultProjectProperties + mapOf(
            "intellijPlatform.type" to "IU",
            "intellijPlatform.version" to "2026.2.1",
        )

    private fun properties(
        bundledPluginsClasspathEnabled: Boolean,
        bundledPlugins: List<String> = emptyList(),
        bundledModules: List<String> = emptyList(),
        expectedPlugins: List<String> = emptyList(),
    ) = defaultProjectProperties + mapOf(
        GradleProperties.TestIdeBundledPluginsClasspathEnabled.toString() to bundledPluginsClasspathEnabled,
        "bundledPlugins" to bundledPlugins.joinToString(","),
        "bundledModules" to bundledModules.joinToString(","),
        "expectedPlugins" to (bundledPlugins + expectedPlugins + "org.jetbrains.plugins.integration-tests.test-ide-content-modules").joinToString(","),
    )

    private val BuildResult.entries
        get() = output.lines().filter { it.startsWith(CLASSPATH_ENTRY_LABEL) }

    @Test
    fun `load bundled plugin requiring content module of another plugin`() {
        disableDebug()

        val properties = properties(
            bundledPluginsClasspathEnabled = false,
            bundledPlugins = listOf("com.intellij.java"),
            expectedPlugins = listOf("intellij.structureView.plugin"),
        )

        build(Tasks.External.TEST, projectProperties = properties) {
            assert(entries.any { it.endsWith(STRUCTURE_VIEW_PLUGIN_JAR) }) {
                "expect test classpath contains '$STRUCTURE_VIEW_PLUGIN_JAR'"
            }
        }
    }

    @Test
    fun `load bundled plugin with bundled plugins classpath enabled`() {
        disableDebug()

        val properties = properties(
            bundledPluginsClasspathEnabled = true,
            bundledPlugins = listOf("com.intellij.java"),
        )

        build(Tasks.External.TEST, projectProperties = properties)
    }

    @Test
    fun `load bundled plugin requiring content module declared by many plugins`() {
        disableDebug()

        // intellij.platform.commercial.verifier is declared by com.intellij.spring, JavaScript, and others
        val properties = properties(
            bundledPluginsClasspathEnabled = false,
            bundledPlugins = listOf("com.intellij.spring"),
        )

        build(Tasks.External.TEST, projectProperties = properties)
    }

    @Test
    fun `load plugin declaring bundled module`() {
        disableDebug()

        val properties = properties(
            bundledPluginsClasspathEnabled = false,
            bundledModules = listOf("intellij.platform.structureView"),
            expectedPlugins = listOf("intellij.structureView.plugin"),
        )

        build(Tasks.External.TEST, projectProperties = properties) {
            assert(entries.any { it.endsWith(STRUCTURE_VIEW_PLUGIN_JAR) }) {
                "expect test classpath contains '$STRUCTURE_VIEW_PLUGIN_JAR'"
            }
        }
    }

    @Test
    fun `load tested plugin without bundled plugins`() {
        disableDebug()

        build(Tasks.External.TEST, projectProperties = properties(bundledPluginsClasspathEnabled = false))
    }
}
