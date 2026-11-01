// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

private const val TESTED_PLUGIN_ID = "org.jetbrains.plugins.integration-tests.test-ide-regressions"
private const val YAML_PLUGIN_ID = "org.jetbrains.plugins.yaml"
private const val ULTIMATE_PLUGIN_ID = "com.intellij.modules.ultimate"
private const val SWAGGER_PLUGIN_ID = "com.intellij.swagger"
private const val SPRING_BOOT_PLUGIN_ID = "com.intellij.spring.boot"
private const val SPRING_CLOUD_PLUGIN_ID = "com.intellij.spring.cloud"
private const val CLION_NOVA_PLUGIN_ID = "org.jetbrains.plugins.clion.radler"
private const val DB_PSI_FACADE = "com.intellij.database.psi.DbPsiFacade"
private const val CUSTOM_TEST_IDE_TASK = "testIdeCustom"

/**
 * Regression tests for the issues reported about running tests and the test classpath,
 * run against the IntelliJ Platform versions from the oldest supported 2023.3 (build 233) to the 2026.3 EAP.
 *
 * Each IntelliJ Platform version is verified with the smoke tests, which check that with all bundled plugins on the test classpath:
 * - the light and heavy tests start with the tested plugin loaded, and with no errors logged,
 * - bundled plugins, their file types, and services are available without declaring them,
 * - bundled plugins excluded by default aren't loaded,
 * - file comparison failures and file templates of the tested plugin work,
 * - the optional dependency of the tested plugin on the bundled YAML plugin is loaded.
 */
class TestIdeRegressionsIntegrationTest : IntelliJPlatformIntegrationTestBase(
    resourceName = "test-ide-regressions",
) {

    private val excludesProperty = GradleProperties.TestIdeBundledPluginsClasspathExcludes.toString()
    private val defaultExcludes = GradleProperties.TestIdeBundledPluginsClasspathExcludes.defaultValue.split(',')
    private val smokeTests = listOf("SmokeTest", "HeavySmokeTest", "FileComparisonTest", "FileTemplateTest", "OptionalDependencyTest")

    private val ideaPlugins = listOf("com.intellij.java", "org.jetbrains.kotlin", YAML_PLUGIN_ID, "Git4Idea")
    private val ideaUltimatePlugins = ideaPlugins + listOf("JavaScript", "com.intellij.css", "com.intellij.database")
    private val ideaFileTypes = mapOf("java" to "JAVA", "kt" to "Kotlin", "xml" to "XML", "json" to "JSON", "yaml" to "YAML", "md" to "Markdown")
    private val ideaUltimateFileTypes = ideaFileTypes + mapOf("js" to "JavaScript", "css" to "CSS")

    private fun properties(
        type: String,
        version: String,
        useInstaller: Boolean = true,
        javaRelease: Int = 21,
        testClasses: List<String> = smokeTests,
        expectedBuild: String = "",
        expectedPlugins: List<String> = emptyList(),
        unexpectedPlugins: List<String> = defaultExcludes,
        expectedFileTypes: Map<String, String> = emptyMap(),
        expectedServices: List<String> = emptyList(),
        yamlExpected: Boolean = true,
    ) = defaultProjectProperties + mapOf(
        "intellijPlatform.type" to type,
        "intellijPlatform.version" to version,
        "useInstaller" to useInstaller,
        "javaRelease" to javaRelease,
        "testClasses" to testClasses.joinToString(","),
        "expectedBuild" to expectedBuild,
        "expectedPlugins" to expectedPlugins.joinToString(","),
        "unexpectedPlugins" to unexpectedPlugins.joinToString(","),
        "expectedFileTypes" to expectedFileTypes.entries.joinToString(",") { "${it.key}:${it.value}" },
        "expectedServices" to expectedServices.joinToString(","),
        "yamlExpected" to yamlExpected,
    )

    private fun smoke(
        properties: Map<String, Any>,
        vararg tasks: String = arrayOf(Tasks.External.TEST),
        args: List<String> = emptyList(),
        block: BuildResult.() -> Unit = {},
    ) = build(*tasks, projectProperties = properties, args = args) {
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2119
        assertNotContains("it = com.jetbrains", output)

        // Make sure the expected tests were executed
        val testClasses = properties["testClasses"].toString().split(',').filter(String::isNotEmpty)
        tasks.filterNot { it.startsWith("clean") }.forEach { taskName ->
            assertEquals(TaskOutcome.SUCCESS, task(":$taskName")?.outcome, "expect task '$taskName' to succeed")
            testClasses.forEach {
                dir containsFile "build/test-results/$taskName/TEST-$it.xml"
            }

            // The same JAR added twice may break package-private access or resources lookup:
            // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1640
            // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2137
            val entries = output.lines()
                .filter { it.startsWith("test-ide-regressions: Test classpath entry [$taskName]: ") }
                .map { it.substringAfter("]: ") }
            assert(entries.isNotEmpty()) { "expect classpath entries of task '$taskName' printed" }
            val duplicates = entries.groupBy { it }.filterValues { it.size > 1 }.keys
            assert(duplicates.isEmpty()) { "expect no duplicated classpath entries of task '$taskName': $duplicates" }
        }

        block()
    }

    private fun BuildResult.assertExcludedByDefault(id: String) {
        assertContains("Bundled plugin '$id' is excluded from the test classpath: excluded by default, see '$excludesProperty'", output)
    }

    private fun BuildResult.assertBundledPluginsSummary(block: (included: Int, all: Int) -> Unit) {
        val match = assertNotNull(
            Regex("Test classpath includes (\\d+) of (\\d+) bundled plugins").find(output),
            "expect the build output contains the bundled plugins summary",
        )
        block(match.groupValues[1].toInt(), match.groupValues[2].toInt())
    }

    @Test
    fun `run tests on IntelliJ IDEA Community 2024_1 without the layout in product-info`() {
        disableDebug()

        val properties = properties(
            type = "IC",
            version = "2024.1.2",
            javaRelease = 17,
            expectedBuild = "241.17011.79",
            expectedPlugins = ideaPlugins + listOf("org.intellij.plugins.markdown", "com.intellij.properties"),
            expectedFileTypes = ideaFileTypes + mapOf("properties" to "Properties"),
        )

        smoke(properties)
    }

    private val ideaUltimate2024_1Properties
        get() = properties(
            type = "IU",
            version = "2024.1.7",
            javaRelease = 17,
            expectedBuild = "241.19416.15",
            expectedPlugins = ideaUltimatePlugins,
            unexpectedPlugins = defaultExcludes + SWAGGER_PLUGIN_ID,
            expectedFileTypes = ideaUltimateFileTypes,
            expectedServices = listOf(DB_PSI_FACADE),
        )

    @Test
    fun `run tests on IntelliJ IDEA Ultimate 2024_1 without the layout in product-info`() {
        disableDebug()

        // The bundled Swagger plugin declares `SwVisualEditingActionsTestService` as a test service implementation, which isn't shipped with 2024.1,
        // so its startup activity fails all tests with ClassNotFoundException, unless Swagger is excluded or disabled:
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1844
        val properties = ideaUltimate2024_1Properties + mapOf(
            excludesProperty to (defaultExcludes + SWAGGER_PLUGIN_ID).joinToString(","),
        )

        smoke(properties, args = listOf("--info")) {
            assertContains("Bundled plugin '$SWAGGER_PLUGIN_ID' is excluded from the test classpath: listed in '$excludesProperty'", output)
        }
    }

    @Test
    fun `run tests on IntelliJ IDEA Ultimate 2024_1 with Swagger plugin disabled`() {
        disableDebug()

        // The workaround suggested in https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1844
        val properties = ideaUltimate2024_1Properties + mapOf(
            "disabledPlugins" to SWAGGER_PLUGIN_ID,
        )

        smoke(properties)
    }

    @Test
    fun `run tests on IntelliJ IDEA Ultimate 2023_3 without the layout in product-info`() {
        disableDebug()

        // The bundled Spring Boot plugin declares `TestSpringBootMetadataEditorNotificationsService` as a test service implementation,
        // which isn't shipped with 2023.3, so its PSI listener fails tests modifying files with ClassNotFoundException,
        // unless Spring Boot is excluded, along with Spring Cloud requiring it, like Swagger in 2024.1.
        val excludes = defaultExcludes + SPRING_BOOT_PLUGIN_ID
        val properties = properties(
            type = "IU",
            version = "2023.3.8",
            javaRelease = 17,
            expectedBuild = "233.15619.7",
            expectedPlugins = ideaUltimatePlugins,
            unexpectedPlugins = excludes + SPRING_CLOUD_PLUGIN_ID,
            expectedFileTypes = ideaUltimateFileTypes,
            expectedServices = listOf(DB_PSI_FACADE),
        ) + mapOf(
            excludesProperty to excludes.joinToString(","),
        )

        smoke(properties, args = listOf("--info")) {
            assertContains("Bundled plugin '$SPRING_BOOT_PLUGIN_ID' is excluded from the test classpath: listed in '$excludesProperty'", output)
            assertContains("Bundled plugin '$SPRING_CLOUD_PLUGIN_ID' is excluded from the test classpath: requires excluded '$SPRING_BOOT_PLUGIN_ID'", output)
        }
    }

    @Test
    fun `run tests on IntelliJ IDEA Ultimate 2024_3`() {
        disableDebug()

        // JSON is a bundled plugin since 2024.3: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1798
        val properties = properties(
            type = "IU",
            version = "2024.3.7.1",
            expectedBuild = "243.28141.41",
            expectedPlugins = ideaUltimatePlugins + "com.intellij.modules.json",
            expectedFileTypes = ideaUltimateFileTypes,
            expectedServices = listOf(DB_PSI_FACADE),
        )

        smoke(properties)
    }

    @Test
    fun `run tests on IntelliJ IDEA Ultimate 2025_1`() {
        disableDebug()

        // Database services require the bundled Charts plugin: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1710
        val properties = properties(
            type = "IU",
            version = "2025.1.6",
            expectedBuild = "251.28774.11",
            expectedPlugins = ideaUltimatePlugins + listOf("com.intellij.modules.json", "intellij.charts"),
            expectedFileTypes = ideaUltimateFileTypes,
            expectedServices = listOf(DB_PSI_FACADE),
        )

        smoke(properties)
    }

    @Test
    fun `run tests on IntelliJ IDEA Ultimate 2025_3`() {
        disableDebug()

        // Bundled plugins failing in tests with content modules in `lib/modules`: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2183
        val properties = properties(
            type = "IU",
            version = "2025.3.4",
            expectedBuild = "253.32098.37",
            expectedPlugins = ideaUltimatePlugins + "com.intellij.modules.json",
            expectedFileTypes = ideaUltimateFileTypes,
            expectedServices = listOf(DB_PSI_FACADE),
        )

        smoke(properties)
    }

    @Test
    fun `run tests on IntelliJ IDEA 2026_1`() {
        disableDebug()

        // Git4Idea used to be disabled by a conflicting content module: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2250
        val properties = properties(
            type = "IU",
            version = "2026.1.1",
            expectedBuild = "261.23567.138",
            expectedPlugins = ideaUltimatePlugins + "com.intellij.modules.json",
            expectedFileTypes = ideaUltimateFileTypes,
            expectedServices = listOf(DB_PSI_FACADE),
        )

        smoke(properties)
    }

    @Test
    fun `run tests on IntelliJ IDEA 2026_2 with obfuscated classes clashing with core classes`() {
        disableDebug()

        // The Ultimate plugin and plugins requiring it are excluded: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2261
        val properties = properties(
            type = "IU",
            version = "2026.2.3",
            expectedBuild = "262.10968.63",
            expectedPlugins = ideaPlugins + "com.intellij.modules.json",
            unexpectedPlugins = defaultExcludes + ULTIMATE_PLUGIN_ID,
            expectedFileTypes = ideaFileTypes,
        )

        smoke(properties) {
            assertContains("obfuscated classes clash", output)
        }
    }

    @Test
    fun `run tests on IntelliJ IDEA 2026_3 EAP resolved from the IntelliJ Maven Repository`() {
        disableDebug()

        // XML is a bundled plugin, and `idea_rt.jar` is provided by the Java plugin since 2026.3: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2273
        val properties = properties(
            type = "IU",
            version = "263.7307.44-EAP-SNAPSHOT",
            useInstaller = false,
            expectedBuild = "263.7307.44",
            expectedPlugins = ideaUltimatePlugins + listOf("com.intellij.modules.json", "com.intellij.xml"),
            expectedFileTypes = ideaUltimateFileTypes + mapOf("html" to "HTML"),
            expectedServices = listOf(DB_PSI_FACADE),
        )

        smoke(properties)
    }

    private val clion2025_3Properties
        get() = properties(
            type = "CL",
            version = "2025.3",
            testClasses = listOf("SmokeTest", "HeavySmokeTest", "FileComparisonTest", "FileTemplateTest"),
            expectedBuild = "253.28294.335",
        )

    @Test
    fun `run tests on CLion 2025_3`() {
        disableDebug()

        // With all bundled plugins on the classpath, the Rider C++ and CLion debugger plugins registered the same service:
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2183
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2185
        // The bundled CLion Nova plugin, excluded by default, starts the ReSharper backend and enables `CodeInsightSettings.AUTO_POPUP_JAVADOC_INFO`
        // in its `RadInitialConfigurator` application initialized listener, so the first light test fails with "Code insight settings damaged".
        smoke(clion2025_3Properties, args = listOf("--info")) {
            assertExcludedByDefault(CLION_NOVA_PLUGIN_ID)
        }
    }

    @Test
    fun `run tests on PyCharm 2026_1`() {
        disableDebug()

        // Python plugin classes located in `lib/modules` failed with "lib/modules should be lib directory":
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2070
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2183
        val properties = properties(
            type = "PY",
            version = "2026.1",
            testClasses = listOf("SmokeTest", "HeavySmokeTest", "FileComparisonTest", "FileTemplateTest"),
            expectedBuild = "261.22158.340",
            expectedPlugins = listOf("PythonCore"),
            expectedFileTypes = mapOf("py" to "Python"),
        )

        smoke(properties)
    }

    @Test
    fun `run tests with custom task targeting another IntelliJ Platform`() {
        disableDebug()

        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1864
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1945
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1993
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2015
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2022
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2074
        val properties = properties(
            type = "IU",
            version = "2025.1.6",
            javaRelease = 17,
            testClasses = listOf("SmokeTest", "HeavySmokeTest"),
            expectedBuild = "251.28774.11",
            expectedPlugins = ideaPlugins,
            expectedFileTypes = mapOf("java" to "JAVA", "kt" to "Kotlin", "xml" to "XML", "yaml" to "YAML"),
        ) + mapOf(
            "customTestIde.type" to "IC",
            "customTestIde.version" to "2024.1.2",
            "customTestIde.expectedBuild" to "241.17011.79",
        )

        smoke(properties, Tasks.External.TEST, CUSTOM_TEST_IDE_TASK)
    }

    @Test
    fun `exclude optional bundled plugin dependency of tested plugin without the layout in product-info`() {
        disableDebug()

        // Testing the plugin with its optional dependency missing: https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/115
        val excludes = defaultExcludes + YAML_PLUGIN_ID
        val properties = properties(
            type = "IC",
            version = "2024.1.2",
            javaRelease = 17,
            testClasses = listOf("SmokeTest", "OptionalDependencyTest"),
            expectedPlugins = listOf("com.intellij.java", "Git4Idea"),
            unexpectedPlugins = excludes,
            yamlExpected = false,
        ) + mapOf(
            excludesProperty to excludes.joinToString(","),
        )

        smoke(properties, args = listOf("--info")) {
            assertContains("Bundled plugin '$YAML_PLUGIN_ID' is excluded from the test classpath", output)
            assertBundledPluginsSummary { included, all ->
                assert(included < all) { "expect some bundled plugins are excluded, but $included of $all are included" }
            }
        }
    }

    @Test
    fun `keep bundled plugin required by tested plugin despite being excluded`() {
        disableDebug()

        pluginXml overwrite //language=xml
                """
                <idea-plugin>
                    <id>$TESTED_PLUGIN_ID</id>
                    <name>Test IDE Regressions</name>
                    <vendor>JetBrains</vendor>

                    <depends>com.intellij.modules.platform</depends>
                    <depends config-file="test-ide-regressions-yaml.xml">$YAML_PLUGIN_ID</depends>

                    <extensions defaultExtensionNs="com.intellij">
                        <internalFileTemplate name="RegressionTemplate"/>
                    </extensions>
                </idea-plugin>
                """.trimIndent()

        // Plugins required by the tested plugin are never excluded, as described in CHANGELOG.md
        val properties = properties(
            type = "IU",
            version = "2025.1.6",
            testClasses = listOf("SmokeTest", "OptionalDependencyTest"),
            expectedPlugins = listOf(YAML_PLUGIN_ID),
        ) + mapOf(
            excludesProperty to (defaultExcludes + YAML_PLUGIN_ID).joinToString(","),
        )

        smoke(properties) {
            assertContains("is kept on the test classpath despite", output)
        }
    }

    @Test
    fun `disable bundled plugin in test sandbox`() {
        disableDebug()

        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1887
        val properties = properties(
            type = "IU",
            version = "2025.1.6",
            testClasses = listOf("SmokeTest", "OptionalDependencyTest"),
            unexpectedPlugins = defaultExcludes + YAML_PLUGIN_ID,
            yamlExpected = false,
        ) + mapOf(
            "disabledPlugins" to YAML_PLUGIN_ID,
        )

        smoke(properties)
    }

    @Test
    fun `run tests without explicit opentest4j dependency`() {
        disableDebug()

        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1663
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2127
        val properties = properties(
            type = "IU",
            version = "2025.1.6",
            testClasses = listOf("SmokeTest", "FileComparisonTest"),
        ) + mapOf(
            "opentest4j" to false,
        )

        smoke(properties)
    }

    @Test
    fun `load JNA in tests on IntelliJ IDEA Community 2024_1`() {
        disableDebug()

        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1815
        val properties = properties(
            type = "IC",
            version = "2024.1.2",
            javaRelease = 17,
            testClasses = listOf("JnaTest"),
        )

        smoke(properties)
    }

    private fun writeJUnit5SmokeTest() {
        dir.resolve("src/test/java/JUnit5SmokeTest.java") write //language=java
                """
                import com.intellij.openapi.application.ApplicationManager;
                import com.intellij.testFramework.junit5.TestApplication;
                import org.junit.jupiter.api.Test;

                import static org.junit.jupiter.api.Assertions.assertNotNull;
                import static org.junit.jupiter.api.Assertions.assertTrue;

                @TestApplication
                class JUnit5SmokeTest {

                    @Test
                    void pluginsLoaded() {
                        assertNotNull(ApplicationManager.getApplication());
                        assertTrue(SmokeTest.isLoaded(SmokeTest.TESTED_PLUGIN_ID), SmokeTest.TESTED_PLUGIN_ID);
                        for (String id : SmokeTest.listProperty("expectedPlugins")) {
                            assertTrue(SmokeTest.isLoaded(id), id);
                        }
                    }
                }
                """.trimIndent()
    }

    private fun writeJavaFixtureTest() {
        dir.resolve("src/test/java/JavaFixtureTest.java") write //language=java
                """
                import com.intellij.psi.PsiJavaFile;
                import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

                public class JavaFixtureTest extends LightJavaCodeInsightFixtureTestCase {

                    public void testJavaFile() {
                        PsiJavaFile file = (PsiJavaFile) myFixture.configureByText("Sample.java", "class Sample { void foo() { int x = 1; } }");

                        assertEquals("Sample", file.getClasses()[0].getName());
                        assertEquals(1, file.getClasses()[0].getMethods().length);
                        myFixture.checkHighlighting();
                    }
                }
                """.trimIndent()
    }

    @Test
    fun `run JUnit 5 tests without the layout in product-info`() {
        disableDebug()
        writeJUnit5SmokeTest()

        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/1711
        // https://github.com/JetBrains/intellij-platform-gradle-plugin/issues/2045
        val properties = properties(
            type = "IC",
            version = "2024.1.2",
            javaRelease = 17,
            testClasses = listOf("JUnit5SmokeTest"),
            expectedPlugins = ideaPlugins,
        ) + mapOf(
            "testFrameworks" to "Platform,JUnit5",
            "junitPlatform" to true,
        )

        smoke(properties)
    }

    @Test
    fun `run JUnit 5 tests on IntelliJ IDEA Ultimate 2025_1`() {
        disableDebug()
        writeJUnit5SmokeTest()

        val properties = properties(
            type = "IU",
            version = "2025.1.6",
            testClasses = listOf("JUnit5SmokeTest"),
            expectedPlugins = ideaUltimatePlugins,
        ) + mapOf(
            "testFrameworks" to "Platform,JUnit5",
            "junitPlatform" to true,
        )

        smoke(properties)
    }

    @Test
    fun `run Java fixture tests without the layout in product-info`() {
        disableDebug()
        writeJavaFixtureTest()

        val properties = properties(
            type = "IC",
            version = "2024.1.2",
            javaRelease = 17,
            testClasses = listOf("JavaFixtureTest"),
        ) + mapOf(
            "bundledPlugins" to "com.intellij.java",
            "testFrameworks" to "Platform,Plugin.Java",
        )

        smoke(properties)
    }

    @Test
    fun `run Java fixture tests on IntelliJ IDEA 2026_3 EAP`() {
        disableDebug()
        writeJavaFixtureTest()

        // `idea_rt.jar` is provided by the Java plugin since 2026.3
        val properties = properties(
            type = "IU",
            version = "263.7307.44-EAP-SNAPSHOT",
            useInstaller = false,
            testClasses = listOf("JavaFixtureTest", "FileComparisonTest"),
        ) + mapOf(
            "bundledPlugins" to "com.intellij.java",
            "testFrameworks" to "Platform,Plugin.Java",
        )

        smoke(properties)
    }

    @Test
    fun `reuse configuration cache for test task`() {
        disableDebug()

        val properties = properties(
            type = "IU",
            version = "2025.1.6",
            testClasses = listOf("SmokeTest"),
        )

        val args = listOf("--configuration-cache")

        // Warm up, as other scenarios sharing the local platform artifacts may have stored another location of this IntelliJ Platform version
        // in `platform-path.txt`, which is read and then updated by the first build, so the configuration cache entry it stores isn't reused.
        smoke(properties, "cleanTest", Tasks.External.TEST, args = args)
        smoke(properties, "cleanTest", Tasks.External.TEST, args = args)
        smoke(properties, "cleanTest", Tasks.External.TEST, args = args) {
            assertContains("Reusing configuration cache.", output)
        }
    }
}
