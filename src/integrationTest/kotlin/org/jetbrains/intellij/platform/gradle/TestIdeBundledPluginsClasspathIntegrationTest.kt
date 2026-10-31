// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.BuildResult
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertNotNull

private const val CLASSPATH_ENTRY_LABEL = "test-ide-bundled-plugins-classpath: Test classpath entry:"
private const val GIT_FRONTEND_CLIENT_JAR = "plugins/cwm-plugin/lib/modules/intellij.vcs.git.frontend.split.jar"
private const val ULTIMATE_PLUGIN_ID = "com.intellij.modules.ultimate"
private const val ULTIMATE_PLUGIN_JAR = "plugins/ultimate-plugin/lib/ultimate-plugin.jar"
private const val IDEA_RT_JAR = "plugins/java/lib/idea_rt.jar"

class TestIdeBundledPluginsClasspathIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "test-ide-bundled-plugins-classpath",
) {

    private fun properties(
        testClasses: List<String>,
        useInstaller: Boolean = true,
        bundledPlugins: List<String> = emptyList(),
        testBundledPlugins: List<String> = emptyList(),
        expectedPlugins: List<String> = emptyList(),
    ) = defaultProjectProperties + mapOf(
        "testClasses" to testClasses.joinToString(","),
        "useInstaller" to useInstaller,
        "bundledPlugins" to bundledPlugins.joinToString(","),
        "testBundledPlugins" to testBundledPlugins.joinToString(","),
        "expectedPlugins" to expectedPlugins.joinToString(","),
    )

    private fun intellijIdeaUltimate(version: String) = mapOf(
        "intellijPlatform.type" to "IU",
        "intellijPlatform.version" to version,
    )

    /**
     * Makes the tested plugin depend on Git4Idea, and adds the `GitVcsTest` test, which compiles only with Git4Idea declared,
     * as the VCS API is provided by a bundled plugin since 2026.3.
     */
    private fun useGit4Idea() {
        pluginXml overwrite //language=xml
                """
                <idea-plugin>
                    <id>org.jetbrains.plugins.integration-tests.test-ide-bundled-plugins-classpath</id>
                    <name>Any</name>
                    <vendor>Any</vendor>

                    <depends>com.intellij.modules.platform</depends>
                    <depends>Git4Idea</depends>
                </idea-plugin>
                """.trimIndent()

        dir.resolve("src/test/kotlin/GitVcsTest.kt") write //language=kotlin
                """
                import com.intellij.openapi.vcs.ProjectLevelVcsManager
                import com.intellij.testFramework.HeavyPlatformTestCase

                class GitVcsTest : HeavyPlatformTestCase() {
                    fun testGitVcsRegistered() {
                        val vcsNames = ProjectLevelVcsManager.getInstance(project).allVcss.map { it.name }

                        assertTrue("Expected the Git VCS to be registered, got: ${'$'}vcsNames", "Git" in vcsNames)
                    }
                }
                """.trimIndent()
    }

    private val BuildResult.entries
        get() = output.lines().filter { it.startsWith(CLASSPATH_ENTRY_LABEL) }

    private fun BuildResult.assertBundledPluginsSummary(block: (included: Int, all: Int) -> Unit) {
        val match = assertNotNull(
            Regex("Test classpath includes (\\d+) of (\\d+) bundled plugins").find(output),
            "expect the build output contains the bundled plugins summary",
        )
        block(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    @Test
    fun `register Git VCS with Git4Idea declared in 2026_1`() {
        disableDebug()
        useGit4Idea()

        // A JetBrains Client plugin JAR of the Remote Development plugin, not listed in the product-info.json layout,
        // declares a content module of Git4Idea, which used to disable it: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2250
        val properties = properties(
            testClasses = listOf("GitVcsTest"),
            bundledPlugins = listOf("Git4Idea"),
        ) + intellijIdeaUltimate("2026.1.1")

        build(Tasks.External.TEST, projectProperties = properties) {
            assert(entries.none { it.endsWith(GIT_FRONTEND_CLIENT_JAR) }) {
                "expect test classpath doesn't contain '$GIT_FRONTEND_CLIENT_JAR'"
            }
        }
    }

    @Test
    fun `exclude bundled plugin with obfuscated classes clashing with core classes`() {
        disableDebug()

        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2261
        val properties = properties(
            testClasses = listOf("LoadedPluginsTest"),
        ) + intellijIdeaUltimate("2026.2.3")

        build(Tasks.External.TEST, projectProperties = properties) {
            assertContains("is excluded from the test classpath because", output)
            assertContains("obfuscated classes clash", output)
            assert(entries.none { it.endsWith(ULTIMATE_PLUGIN_JAR) }) {
                "expect test classpath doesn't contain '$ULTIMATE_PLUGIN_JAR'"
            }
        }
    }

    @Test
    fun `keep declared bundled plugin with obfuscated classes clashing with core classes`() {
        disableDebug()

        val properties = properties(
            testClasses = listOf("LoadedPluginsTest"),
            testBundledPlugins = listOf(ULTIMATE_PLUGIN_ID),
            expectedPlugins = listOf(ULTIMATE_PLUGIN_ID),
        ) + intellijIdeaUltimate("2026.2.3")

        build(Tasks.External.TEST, projectProperties = properties) {
            assertContains("shadows", output)
            assertContains("obfuscated classes", output)
            assert(entries.any { it.endsWith(ULTIMATE_PLUGIN_JAR) }) {
                "expect test classpath contains '$ULTIMATE_PLUGIN_JAR'"
            }
        }
    }

    @Test
    fun `load test framework runtime classes provided by Java plugin in 2026_3`() {
        disableDebug()

        // FileComparisonFailedError implements FileComparisonData of idea_rt.jar, provided by the Java plugin since 2026.3
        val properties = properties(
            testClasses = listOf("FileComparisonFailedErrorTest"),
            useInstaller = false,
        ) + intellijIdeaUltimate("263.7307.44-EAP-SNAPSHOT")

        build(Tasks.External.TEST, projectProperties = properties) {
            assert(entries.any { it.endsWith(IDEA_RT_JAR) }) {
                "expect test classpath contains '$IDEA_RT_JAR'"
            }
        }
    }

    @Test
    fun `exclude bundled plugins with plugins requiring them but keep declared plugins`() {
        disableDebug()
        useGit4Idea()

        val excludes = listOf(
            GradleProperties.TestIdeBundledPluginsClasspathExcludes.defaultValue,
            "org.jetbrains.plugins.yaml",
            "Git4Idea",
        )
        val properties = properties(
            testClasses = listOf("GitVcsTest"),
            useInstaller = false,
            bundledPlugins = listOf("Git4Idea"),
        ) + intellijIdeaUltimate("263.7307.44-EAP-SNAPSHOT") + mapOf(
            GradleProperties.TestIdeBundledPluginsClasspathExcludes.toString() to excludes.joinToString(","),
        )

        build(Tasks.External.TEST, projectProperties = properties, args = listOf("--info")) {
            assertContains("Bundled plugin 'org.jetbrains.plugins.yaml' is excluded from the test classpath", output)
            assert(output.lines().any { it.contains("is excluded from the test classpath: requires excluded '") }) {
                "expect plugins requiring 'org.jetbrains.plugins.yaml' are excluded from the test classpath"
            }

            // Git4Idea is declared, so it isn't excluded, together with the plugins requiring it
            assertContains("is kept on the test classpath despite", output)
            assertNotContains("requires excluded 'Git4Idea'", output)

            assertBundledPluginsSummary { included, all ->
                assert(included < all) { "expect some bundled plugins are excluded, but $included of $all are included" }
            }
        }
    }
}
