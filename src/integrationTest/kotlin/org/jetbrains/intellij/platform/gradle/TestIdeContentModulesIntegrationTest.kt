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
        bundledPlugins: List<String> = emptyList(),
        marketplacePlugins: List<String> = emptyList(),
        expectedPlugins: List<String> = emptyList(),
    ) = defaultProjectProperties + mapOf(
        "bundledPlugins" to bundledPlugins.joinToString(","),
        "marketplacePlugins" to marketplacePlugins.joinToString(","),
        "expectedPlugins" to (bundledPlugins + expectedPlugins + "org.jetbrains.plugins.integration-tests.test-ide-content-modules").joinToString(","),
    )

    private val BuildResult.entries
        get() = output.lines().filter { it.startsWith(CLASSPATH_ENTRY_LABEL) }

    @Test
    fun `load bundled plugin requiring content module of another plugin`() {
        disableDebug()

        val properties = properties(
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
    fun `load marketplace plugin requiring bundled JSON plugin`() {
        disableDebug()

        dir.resolve("src/test/kotlin/GoFileTypeTest.kt") write //language=kotlin
                """
                import com.goide.GoFileType
                import com.intellij.openapi.fileTypes.FileTypeManager
                import com.intellij.testFramework.fixtures.BasePlatformTestCase

                class GoFileTypeTest : BasePlatformTestCase() {
                    fun testGoFileType() {
                        assertSame(GoFileType.INSTANCE, FileTypeManager.getInstance().getFileTypeByExtension("go"))
                        assertSame(GoFileType.INSTANCE, FileTypeManager.getInstance().getFileTypeByFileName("_.go"))
                        assertSame(GoFileType.INSTANCE, myFixture.createFile("_.go", "").fileType)
                    }
                }
                """.trimIndent()

        // The Go plugin requires the JSON plugin, bundled since 2024.3: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1798
        val properties = properties(
            marketplacePlugins = listOf("org.jetbrains.plugins.go:243.28141.18"),
            expectedPlugins = listOf("org.jetbrains.plugins.go", "com.intellij.modules.json"),
        ) + mapOf(
            "intellijPlatform.version" to "2024.3.7.1",
        )

        for (taskName in listOf(Tasks.External.TEST, "testIdeCustom")) {
            build(taskName, projectProperties = properties)
        }
    }
}
