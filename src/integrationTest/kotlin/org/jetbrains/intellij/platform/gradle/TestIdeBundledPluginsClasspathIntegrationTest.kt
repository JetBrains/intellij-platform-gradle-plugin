// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.BuildResult
import kotlin.io.path.readText
import kotlin.test.Test

private const val GIT_FRONTEND_CLIENT_JAR = "plugins/cwm-plugin/lib/modules/intellij.vcs.git.frontend.split.jar"
private const val ULTIMATE_PLUGIN_ID = "com.intellij.modules.ultimate"
private const val ULTIMATE_PLUGIN_JAR = "plugins/ultimate-plugin/lib/ultimate-plugin.jar"
private const val IDEA_RT_JAR = "plugins/java/lib/idea_rt.jar"

class TestIdeBundledPluginsClasspathIntegrationTest : TestIdeIntegrationTestFixture() {

    /**
     * Makes the tested plugin depend on Git4Idea, and adds the `GitVcsTest` test, which compiles only with Git4Idea declared,
     * as the VCS API is provided by a bundled plugin since 2026.3.
     */
    private fun useGit4Idea() {
        pluginXml overwrite pluginXml.readText().replace(
            "<depends>com.intellij.modules.platform</depends>",
            "<depends>com.intellij.modules.platform</depends>\n    <depends>Git4Idea</depends>",
        )

        dir.resolve("src/test/java/GitVcsTest.java") write //language=java
                """
                import com.intellij.openapi.vcs.ProjectLevelVcsManager;
                import com.intellij.testFramework.HeavyPlatformTestCase;

                import java.util.Arrays;
                import java.util.List;

                public class GitVcsTest extends HeavyPlatformTestCase {
                    public void testGitVcsRegistered() {
                        List<String> vcsNames = Arrays.stream(ProjectLevelVcsManager.getInstance(getProject()).getAllVcss())
                            .map(vcs -> vcs.getName()).toList();

                        assertTrue("Expected the Git VCS to be registered, got: " + vcsNames, vcsNames.contains("Git"));
                    }
                }
                """.trimIndent()
    }

    private val BuildResult.entries
        get() = classpathEntries()

    @Test
    fun `register Git VCS with Git4Idea declared in 2026_1`() {
        disableDebug()
        useGit4Idea()

        // A JetBrains Client plugin JAR of the Remote Development plugin, not listed in the product-info.json layout,
        // declares a content module of Git4Idea, which used to disable it: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2250
        val properties = properties(
            version = "2026.1.1",
            testClasses = listOf("GitVcsTest"),
            bundledPlugins = listOf("Git4Idea"),
        )

        smoke(properties) {
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
            version = "2026.2.3",
            testClasses = listOf("SmokeTest"),
            unexpectedPlugins = defaultExcludes + ULTIMATE_PLUGIN_ID,
        )

        smoke(properties) {
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
            version = "2026.2.3",
            testClasses = listOf("SmokeTest"),
            testBundledPlugins = listOf(ULTIMATE_PLUGIN_ID),
            expectedPlugins = listOf(ULTIMATE_PLUGIN_ID),
        )

        smoke(properties) {
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
            version = "263.7307.44-EAP-SNAPSHOT",
            testClasses = listOf("FileComparisonTest"),
            useInstaller = false,
            fileComparisonDataExpected = true,
        )

        smoke(properties) {
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
            version = "263.7307.44-EAP-SNAPSHOT",
            testClasses = listOf("GitVcsTest"),
            useInstaller = false,
            bundledPlugins = listOf("Git4Idea"),
        ) + mapOf(
            GradleProperties.TestIdeBundledPluginsClasspathExcludes.toString() to excludes.joinToString(","),
        )

        smoke(properties, args = listOf("--info")) {
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
