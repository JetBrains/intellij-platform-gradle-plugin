// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.Version
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.BeforeTest
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class PrepareSandboxTaskTest : IntelliJPluginTestBase() {

    override fun intellijPlatformDependency() = """local("${createLocalIntelliJPlatform().invariantSeparatorsPathString}")"""

    @BeforeTest
    override fun setup() {
        super.setup()

        buildFile write //language=kotlin
                """
                dependencies {
                    compileOnly("org.jetbrains:annotations:26.0.2")
                }
                """.trimIndent()
    }

    private fun createLocalIntelliJPlatform() = dir.resolve("local-ide").also { platformPath ->
        platformPath.resolve("build.txt") write "$intellijPlatformType-$intellijPlatformBuildNumber"
        platformPath.resolve("product-info.json") write //language=json
                """
                {
                  "name": "IntelliJ IDEA",
                  "version": "$intellijPlatformVersion",
                  "buildNumber": "$intellijPlatformBuildNumber",
                  "productCode": "$intellijPlatformType",
                  "dataDirectoryName": "IntelliJIdeaTest",
                  "svgIconPath": "bin/idea.svg",
                  "productVendor": "JetBrains",
                  "launch": [],
                  "bundledPlugins": [
                    "com.intellij",
                    "com.intellij.copyright"
                  ],
                  "modules": [],
                  "layout": [
                    {
                      "name": "com.intellij",
                      "kind": "plugin",
                      "classPath": [
                        "lib/product.jar"
                      ]
                    },
                    {
                      "name": "com.intellij.copyright",
                      "kind": "plugin",
                      "classPath": [
                        "plugins/copyright/lib/copyright.jar"
                      ]
                    }
                  ]
                }
                """.trimIndent()

        writePlugin(
            path = platformPath.resolve("lib/product.jar"),
            descriptorName = "ideaPlugin.xml",
            id = "com.intellij",
            name = "IDEA CORE",
        )
        writePlugin(
            path = platformPath.resolve("plugins/copyright/lib/copyright.jar"),
            descriptorName = "plugin.xml",
            id = "com.intellij.copyright",
            name = "Copyright",
        )
        writeEmptyJar(platformPath.resolve("modules/module-descriptors.jar"))
    }

    private fun writePlugin(path: Path, descriptorName: String, id: String, name: String) {
        path.parent.createDirectories()
        ZipOutputStream(Files.newOutputStream(path)).use { zip ->
            zip.putNextEntry(ZipEntry("META-INF/$descriptorName"))
            zip.write(
                //language=xml
                """
                <idea-plugin>
                  <id>$id</id>
                  <name>$name</name>
                  <version>1.0</version>
                </idea-plugin>
                """.trimIndent().toByteArray()
            )
            zip.closeEntry()
        }
    }

    private fun writeEmptyJar(path: Path) {
        path.parent.createDirectories()
        ZipOutputStream(Files.newOutputStream(path)).close()
    }

    private val sandbox
        get() = cacheDirectory.resolve(Sandbox.CONTAINER).resolve("projectName").resolve("$intellijPlatformType-$intellijPlatformVersion")

    private val updatesFile
        get() = sandbox.resolve("config/options/updates.xml")

    private val disabledPluginsFile
        get() = sandbox.resolve("config/disabled_plugins.txt")

    private val splitModeFrontendPropertiesFile
        get() = sandbox.resolve("frontend.properties")

    private val sinceBuild: String
        get() {
            val version = Version.parse(intellijPlatformBuildNumber)
            return "${version.major}"
        }

    @Test
    @Ignore
    fun `prepare sandbox for two plugins`() {
        writeJavaFile()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <id>org.intellij.test.plugin</id>
                    <name>Test</name>
                    <version>1.0</version>
                    <vendor url="https://jetbrains.com">JetBrains</vendor>
                    <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                    <change-notes/>
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    implementation(project("nestedProject"))
                }
                
                intellijPlatform {
                    pluginConfiguration {
                        name = "myPluginName"
                    }
                }
                """.trimIndent()

        settingsFile write //language=kotlin
                """
                include("nestedProject")
                """.trimIndent()

        dir.resolve("nestedProject/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform")
                }
                
                version = "1.0.0"
                
                repositories { 
                    mavenCentral()
                    
                    intellijPlatform {
                        releases()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        create("$intellijPlatformType", "$intellijPlatformVersion")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                    pluginConfiguration {
                        name = "myNestedPluginName"
                    }
                }
                """.trimIndent()

        dir.resolve("nestedProject/src/main/java/NestedAppFile.java") write //language=java
                """
                class NestedAppFile {}
                """.trimIndent()

        dir.resolve("nestedProject/src/main/resources/META-INF/plugin.xml") write pluginXml.readText()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "plugins/myPluginName/lib/projectName-1.0.0.jar",
                "plugins/myPluginName/lib/nestedProject-1.0.0.jar",

                // OLD:
                "config/options/updates.xml",
                "plugins/myNestedPluginName/lib/nestedProject-1.0.0.jar",
                "plugins/myPluginName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )

        sandbox.resolve("plugins/myPluginName/lib/projectName-1.0.0.jar").toZip().use { jar ->
            assertEquals(
                setOf(
                    "META-INF/",
                    "META-INF/MANIFEST.MF",
                    "App.class",
                    "META-INF/plugin.xml",
                ),
                collectPaths(jar),
            )
        }

        sandbox.resolve("plugins/myNestedPluginName/lib/nestedProject-1.0.0.jar").toZip().use { jar ->
            assertEquals(
                setOf(
                    "META-INF/",
                    "META-INF/MANIFEST.MF",
                    "NestedAppFile.class",
                    "META-INF/plugin.xml",
                ),
                collectPaths(jar),
            )
        }
    }

    @Test
    @Ignore
    fun `prepare sandbox for two plugins with evaluated project`() {
        writeJavaFile()

        pluginXml write //language=xml
                """
                <idea-plugin>
                  <id>org.intellij.test.plugin</id>
                  <name>Test</name>
                  <version>1.0</version>
                  <vendor url="https://jetbrains.com">JetBrains</vendor>
                  <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                  <change-notes/>
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    implementation(project(":nestedProject"))
                }
                
                project(":nestedProject") {
                    intellijPlatform {
                        instrumentCode = false
                        pluginConfiguration {
                            name = "myNestedPluginName"            
                        }
                    }
                }
                """.trimIndent()

        settingsFile write //language=kotlin
                """
                include("nestedProject")
                """.trimIndent()

        dir.resolve("nestedProject/src/main/java/NestedAppFile.java") write //language=java
                """
                class NestedAppFile {}
                """.trimIndent()

        dir.resolve("nestedProject/src/main/resources/META-INF/plugin.xml") write pluginXml.readText()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "plugins/myPluginName/lib/projectName-1.0.0.jar",
                "plugins/myNestedPluginName/lib/nestedProject-1.0.0.jar",
                "config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )

        sandbox.resolve("plugins/myPluginName/lib/projectName-1.0.0.jar").toZip().use { jar ->
            assertEquals(
                setOf(
                    "META-INF/",
                    "META-INF/MANIFEST.MF",
                    "App.class",
                    "META-INF/plugin.xml",
                ),
                collectPaths(jar),
            )
        }

        sandbox.resolve("plugins/myNestedPluginName/lib/nestedProject-1.0.0.jar").toZip().use { jar ->
            assertEquals(
                setOf(
                    "META-INF/",
                    "META-INF/MANIFEST.MF",
                    "NestedAppFile.class",
                    "META-INF/plugin.xml",
                ),
                collectPaths(jar),
            )
        }
    }

    @Test
    fun `prepare sandbox task without plugin_xml`() {
        writeJavaFile()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        name = "myPluginName"
                    }
                }
                dependencies {
                    implementation("joda-time:joda-time:2.8.1")
                    intellijPlatform {
                        bundledPlugin("com.intellij.copyright")
                    }
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "config/disabled_plugins.txt",
                "plugins/projectName/lib/joda-time-2.8.1.jar",
                "plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox task`() {
        writeJavaFile()
        dir.resolve("src/main/resources/META-INF/other.xml") write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        dir.resolve("src/main/resources/META-INF/nonIncluded.xml") write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                  <depends config-file="other.xml" />
                </idea-plugin>
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    pluginConfiguration {
                        name = "myPluginName"
                    }
                }
                dependencies {
                    implementation("joda-time:joda-time:2.8.1")
                    intellijPlatform {
                        bundledPlugin("com.intellij.copyright")
                    }
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "config/disabled_plugins.txt",
                "plugins/projectName/lib/joda-time-2.8.1.jar",
                "plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )

        sandbox.resolve("plugins/projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
            assertEquals(
                setOf(
                    "META-INF/",
                    "META-INF/MANIFEST.MF",
                    "App.class",
                    "META-INF/nonIncluded.xml",
                    "META-INF/other.xml",
                    "META-INF/plugin.xml",
                ),
                collectPaths(jar),
            )

            assertZipContent(
                jar,
                "META-INF/plugin.xml",
                """
                <idea-plugin>
                  <idea-version since-build="$sinceBuild" />
                  <version>1.0.0</version>
                  <name>myPluginName</name>
                  <depends config-file="other.xml" />
                </idea-plugin>
                """.trimIndent()
            )
        }
    }

    @Test
    fun `exclude dependencies from all sandbox runtime classpaths`() {
        dir.resolve("src/main/java/App.java") write //language=java
                """
                import org.joda.time.DateTime;
                import org.apache.logging.log4j.LogManager;

                class App {
                    private final DateTime now = new DateTime();
                    private static final String LOGGER_NAME = LogManager.getLogger().getName();
                }
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    implementation("joda-time:joda-time:2.8.1")
                    implementation("org.apache.logging.log4j:log4j-core:2.24.3")
                }

                configurations.named("intellijPlatformSandboxRuntimeClasspath") {
                    exclude(group = "joda-time", module = "joda-time")
                    exclude(group = "org.apache.logging.log4j", module = "log4j-api")
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX, Tasks.PREPARE_TEST_SANDBOX)

        listOf(
            sandbox.resolve("plugins/projectName/lib"),
            sandbox.resolve("plugins-test/projectName/lib"),
        ).forEach { lib ->
            assertExists(lib.resolve("log4j-core-2.24.3.jar"))
            assertFalse(lib.resolve("joda-time-2.8.1.jar").exists())
            assertFalse(lib.resolve("log4j-api-2.24.3.jar").exists())
        }
    }

    @Test
    fun `exclude default sandbox dependencies`() {
        addDefaultSandboxDependencies()

        build(Tasks.PREPARE_SANDBOX, Tasks.PREPARE_TEST_SANDBOX)

        sandboxLibDirectories().forEach { lib ->
            assertFalse(lib.resolve("kotlin-stdlib-$kotlinPluginVersion.jar").exists())
            assertFalse(lib.resolve("kotlinx-coroutines-core-jvm-1.7.1.jar").exists())
        }
    }

    @Test
    fun `include default sandbox dependencies when default exclusions are disabled`() {
        addDefaultSandboxDependencies()
        gradleProperties write "${GradleProperties.UseDefaultSandboxExclusions}=false"

        build(Tasks.PREPARE_SANDBOX, Tasks.PREPARE_TEST_SANDBOX)

        sandboxLibDirectories().forEach { lib ->
            assertExists(lib.resolve("kotlin-stdlib-$kotlinPluginVersion.jar"))
            assertExists(lib.resolve("kotlinx-coroutines-core-jvm-1.7.1.jar"))
        }
    }

    private fun addDefaultSandboxDependencies() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinPluginVersion")
                    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.7.1")
                }
                """.trimIndent()
    }

    private fun sandboxLibDirectories() = listOf(
        sandbox.resolve("plugins/projectName/lib"),
        sandbox.resolve("plugins-test/projectName/lib"),
    )

    @Test
    fun `prepare sandbox for splitMode with plugin installed on frontend via deprecated target`() {
        buildSandboxForSplitMode(
            """
            @Suppress("DEPRECATION")
            splitModeTarget = SplitModeAware.SplitModeTarget.FRONTEND
            """.trimIndent()
        )
        assertFileContent(
            sandbox.resolve("frontend.properties"),
            """
            idea.config.path=${sandbox.resolve("config/frontend").invariantSeparatorsPathString}
            idea.system.path=${sandbox.resolve("system/frontend").invariantSeparatorsPathString}
            idea.log.path=${sandbox.resolve("log/frontend").invariantSeparatorsPathString}
            idea.plugins.path=${sandbox.resolve("plugins/frontend").invariantSeparatorsPathString}
            """.trimIndent()
        )
        assertExists(sandbox.resolve("plugins/frontend/projectName/lib/projectName-1.0.0.jar"))
        assertFalse(sandbox.resolve("plugins/projectName/lib/projectName-1.0.0.jar").exists())
    }

    @Test
    fun `prepare sandbox for splitMode with plugin installed on backend`() {
        buildSandboxForSplitMode("pluginInstallationTarget = SplitModeAware.PluginInstallationTarget.BACKEND")
        assertFileContent(
            sandbox.resolve("frontend.properties"),
            """
            idea.config.path=${sandbox.resolve("config/frontend").invariantSeparatorsPathString}
            idea.system.path=${sandbox.resolve("system/frontend").invariantSeparatorsPathString}
            idea.log.path=${sandbox.resolve("log/frontend").invariantSeparatorsPathString}
            idea.plugins.path=${sandbox.resolve("plugins/frontend").invariantSeparatorsPathString}
            """.trimIndent()
        )
        assertExists(sandbox.resolve("plugins/projectName/lib/projectName-1.0.0.jar"))
        assertFalse(sandbox.resolve("plugins/frontend/projectName/lib/projectName-1.0.0.jar").exists())
    }

    @Test
    fun `prepare sandbox for splitMode with plugin installed on backend and frontend`() {
        buildSandboxForSplitMode("pluginInstallationTarget = SplitModeAware.PluginInstallationTarget.BOTH")
        assertFileContent(
            sandbox.resolve("frontend.properties"),
            """
            idea.config.path=${sandbox.resolve("config/frontend").invariantSeparatorsPathString}
            idea.system.path=${sandbox.resolve("system/frontend").invariantSeparatorsPathString}
            idea.log.path=${sandbox.resolve("log/frontend").invariantSeparatorsPathString}
            idea.plugins.path=${sandbox.resolve("plugins").invariantSeparatorsPathString}
            """.trimIndent()
        )
        assertExists(sandbox.resolve("plugins/projectName/lib/projectName-1.0.0.jar"))
        assertFalse(sandbox.resolve("plugins/frontend/projectName/lib/projectName-1.0.0.jar").exists())
    }

    @Test
    fun `prepare sandbox for splitMode defaults plugin installation target to backend`() {
        buildSandboxForSplitMode()

        assertExists(sandbox.resolve("plugins/projectName/lib/projectName-1.0.0.jar"))
        assertFalse(sandbox.resolve("plugins/frontend/projectName/lib/projectName-1.0.0.jar").exists())
    }

    private fun buildSandboxForSplitMode(splitModeConfiguration: String = "") {
        writeJavaFile()

        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile prepend // language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    sandboxContainer = file("${cacheDirectory.resolve(Sandbox.CONTAINER).invariantSeparatorsPathString}")
                    splitMode = true
                    $splitModeConfiguration
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)
    }

    @Test
    fun `prepare sandbox with external jar-type plugin`() {
        writeJavaFile()

        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        marketplace()
                    }
                }
                dependencies {
                    intellijPlatform {
                        plugin("org.jetbrains.postfixCompletion", "0.8-beta")
                    }
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "config/disabled_plugins.txt",
                "plugins/projectName/lib/projectName-1.0.0.jar",
                "plugins/org.jetbrains.postfixCompletion-0.8-beta.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox for splitMode installs external jar-type plugin on backend by default`() {
        writeJavaFile()

        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        marketplace()
                    }
                }
                dependencies {
                    intellijPlatform {
                        plugin("org.jetbrains.postfixCompletion", "0.8-beta")
                    }
                }
                intellijPlatform {
                    splitMode = true
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertExists(sandbox.resolve("plugins/projectName/lib/projectName-1.0.0.jar"))
        assertExists(sandbox.resolve("plugins/org.jetbrains.postfixCompletion-0.8-beta.jar"))
        assertFalse(sandbox.resolve("plugins/frontend/projectName/lib/projectName-1.0.0.jar").exists())
        assertFalse(sandbox.resolve("plugins/frontend/org.jetbrains.postfixCompletion-0.8-beta.jar").exists())
    }

    @Test
    fun `prepare sandbox for splitMode installs external jar-type plugin in shared plugins dir when requested on both sides`() {
        writeJavaFile()

        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile prepend // language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware
                """.trimIndent()

        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        marketplace()
                    }
                }
                dependencies {
                    intellijPlatform {
                        plugin("org.jetbrains.postfixCompletion", "0.8-beta")
                    }
                }
                intellijPlatform {
                    splitMode = true
                    pluginInstallationTarget = SplitModeAware.PluginInstallationTarget.BOTH
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertExists(sandbox.resolve("plugins/projectName/lib/projectName-1.0.0.jar"))
        assertExists(sandbox.resolve("plugins/org.jetbrains.postfixCompletion-0.8-beta.jar"))
        assertFalse(sandbox.resolve("plugins/frontend/projectName/lib/projectName-1.0.0.jar").exists())
        assertFalse(sandbox.resolve("plugins/frontend/org.jetbrains.postfixCompletion-0.8-beta.jar").exists())
    }

    @Test
    fun `prepare sandbox with external zip-type plugin`() {
        writeJavaFile()

        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        marketplace()
                    }
                }
                dependencies {
                    intellijPlatform {
                        plugin("org.intellij.plugins.markdown", "$markdownPluginVersion")
                    }
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "config/disabled_plugins.txt",
                "plugins/projectName/lib/projectName-1.0.0.jar",
                "plugins/markdown/lib/markdown.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `exclude bundled plugin libraries from classpath and sandbox`() {
        val repository = createPluginFixtureRepository(includeControlPlugin = true)

        writeJavaFile()
        pluginXml write "<idea-plugin />"
        configurePluginFixtureRepository(repository)
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        plugin("$FILTER_TARGET_PLUGIN_ID", "$PLUGIN_FIXTURE_VERSION") {
                            excludeBundledLibrary("jena-*.jar")
                        }
                        plugin("$FILTER_CONTROL_PLUGIN_ID", "$PLUGIN_FIXTURE_VERSION")
                    }
                }
                """.trimIndent()
        configureClasspathAssertionTask(
            taskName = "assertFilteredPluginClasspath",
            configurationName = Constants.Configurations.External.COMPILE_CLASSPATH,
            expectedPresent = listOf(
                FILTER_TARGET_MAIN_JAR,
                FILTER_TARGET_SUPPORT_JAR,
                FILTER_CONTROL_MAIN_JAR,
                FILTER_CONTROL_JENA_JAR,
            ),
            expectedAbsent = listOf(FILTER_TARGET_JENA_CORE_JAR, FILTER_TARGET_JENA_ARQ_JAR),
        )

        build(Tasks.PREPARE_SANDBOX, "assertFilteredPluginClasspath")
        buildWithConfigurationCache(Tasks.PREPARE_SANDBOX, "assertFilteredPluginClasspath")
        buildWithConfigurationCache(Tasks.PREPARE_SANDBOX, "assertFilteredPluginClasspath") {
            assertConfigurationCacheReused()
        }

        val plugins = sandbox.resolve("plugins")
        assertExists(plugins.resolve("filter-target/lib/$FILTER_TARGET_MAIN_JAR"))
        assertExists(plugins.resolve("filter-target/lib/$FILTER_TARGET_SUPPORT_JAR"))
        assertFalse(plugins.resolve("filter-target/lib/$FILTER_TARGET_JENA_CORE_JAR").exists())
        assertFalse(plugins.resolve("filter-target/lib/modules/$FILTER_TARGET_JENA_ARQ_JAR").exists())
        assertExists(plugins.resolve("filter-control/lib/$FILTER_CONTROL_MAIN_JAR"))
        assertExists(plugins.resolve("filter-control/lib/$FILTER_CONTROL_JENA_JAR"))
    }

    @Test
    fun `exclude bundled libraries from test plugin classpath`() {
        val repository = createPluginFixtureRepository()

        writeJavaFile()
        pluginXml write "<idea-plugin />"
        configurePluginFixtureRepository(repository)
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        testPlugin("$FILTER_TARGET_PLUGIN_ID", "$PLUGIN_FIXTURE_VERSION") {
                            excludeBundledLibraries("$FILTER_TARGET_JENA_CORE_JAR", "$FILTER_TARGET_JENA_ARQ_JAR")
                        }
                    }
                }
                """.trimIndent()
        configureClasspathAssertionTask(
            taskName = "assertFilteredTestPluginClasspath",
            configurationName = Constants.Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH,
            expectedPresent = listOf(FILTER_TARGET_MAIN_JAR, FILTER_TARGET_SUPPORT_JAR),
            expectedAbsent = listOf(FILTER_TARGET_JENA_CORE_JAR, FILTER_TARGET_JENA_ARQ_JAR),
        )

        build("assertFilteredTestPluginClasspath")
    }

    @Test
    fun `exclude bundled libraries from custom testing plugin classpath and sandbox`() {
        val repository = createPluginFixtureRepository()
        val customTest = "customTest"

        writeJavaFile()
        pluginXml write "<idea-plugin />"
        configurePluginFixtureRepository(repository)
        buildFile write //language=kotlin
                """
                val $customTest by intellijPlatformTesting.testIde.registering {
                    plugins {
                        plugin("$FILTER_TARGET_PLUGIN_ID", "$PLUGIN_FIXTURE_VERSION") {
                            excludeBundledLibrary("jena-*.jar")
                        }
                    }
                }
                """.trimIndent()
        configureClasspathAssertionTask(
            taskName = "assertFilteredCustomTestPluginClasspath",
            configurationName = "${Constants.Configurations.INTELLIJ_PLATFORM_TEST_CLASSPATH}_$customTest",
            expectedPresent = listOf(FILTER_TARGET_MAIN_JAR, FILTER_TARGET_SUPPORT_JAR),
            expectedAbsent = listOf(FILTER_TARGET_JENA_CORE_JAR, FILTER_TARGET_JENA_ARQ_JAR),
        )

        build("${Tasks.PREPARE_SANDBOX}_$customTest", "assertFilteredCustomTestPluginClasspath")

        val plugin = sandbox.resolve("plugins_$customTest/filter-target")
        assertExists(plugin.resolve("lib/$FILTER_TARGET_MAIN_JAR"))
        assertExists(plugin.resolve("lib/$FILTER_TARGET_SUPPORT_JAR"))
        assertFalse(plugin.resolve("lib/$FILTER_TARGET_JENA_CORE_JAR").exists())
        assertFalse(plugin.resolve("lib/modules/$FILTER_TARGET_JENA_ARQ_JAR").exists())
    }

    @Test
    fun `exclude bundled libraries from plugin dependency inherited from module sandbox`() {
        val repository = createPluginFixtureRepository(includeControlPlugin = true)

        gradleProperties write "\norg.gradle.unsafe.isolated-projects=true"
        writeJavaFile()
        pluginXml write "<idea-plugin />"
        configurePluginFixtureRepository(repository)
        settingsFile write //language=kotlin
                """
                include("backend")
                """.trimIndent()
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginModule(implementation(project(":backend")))
                        plugin("$FILTER_CONTROL_PLUGIN_ID", "$PLUGIN_FIXTURE_VERSION") {
                            excludeBundledLibrary("$FILTER_CONTROL_JENA_JAR")
                        }
                    }
                }
                """.trimIndent()

        val backendBuildFile = dir.resolve("backend/build.gradle.kts")
        backendBuildFile write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.module")
                }
                """.trimIndent()
        backendBuildFile write pluginFixtureRepository(repository)
        backendBuildFile write //language=kotlin
                """
                repositories {
                    intellijPlatform {
                        localPlatformArtifacts()
                    }
                }

                dependencies {
                    intellijPlatform {
                        local("${dir.resolve("local-ide").invariantSeparatorsPathString}")
                        plugin("$FILTER_TARGET_PLUGIN_ID", "$PLUGIN_FIXTURE_VERSION") {
                            excludeBundledLibrary("jena-*.jar")
                        }
                    }
                }

                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()
        dir.resolve("backend/src/main/java/BackendFeature.java") write "class BackendFeature {}"

        build("clean", Tasks.PREPARE_SANDBOX)
        build("clean", Tasks.PREPARE_SANDBOX)
        sandbox.toFile().deleteRecursively()
        build("clean", Tasks.PREPARE_SANDBOX) {
            assertConfigurationCacheReused()
        }

        val plugin = sandbox.resolve("plugins/filter-target")
        assertExists(plugin.resolve("lib/$FILTER_TARGET_MAIN_JAR"))
        assertExists(plugin.resolve("lib/$FILTER_TARGET_SUPPORT_JAR"))
        assertFalse(plugin.resolve("lib/$FILTER_TARGET_JENA_CORE_JAR").exists())
        assertFalse(plugin.resolve("lib/modules/$FILTER_TARGET_JENA_ARQ_JAR").exists())
        val controlPlugin = sandbox.resolve("plugins/filter-control")
        assertExists(controlPlugin.resolve("lib/$FILTER_CONTROL_MAIN_JAR"))
        assertFalse(controlPlugin.resolve("lib/$FILTER_CONTROL_JENA_JAR").exists())
    }

    @Test
    fun `fail when bundled library exclusion removes plugin descriptor`() {
        val repository = createPluginFixtureRepository()

        writeJavaFile()
        pluginXml write "<idea-plugin />"
        configurePluginFixtureRepository(repository)
        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        plugin("$FILTER_TARGET_PLUGIN_ID", "$PLUGIN_FIXTURE_VERSION") {
                            excludeBundledLibrary("$FILTER_TARGET_MAIN_JAR")
                        }
                    }
                }
                """.trimIndent()

        buildAndFail(Tasks.PREPARE_SANDBOX) {
            assertContains(
                "Filtering bundled libraries from plugin '$FILTER_TARGET_PLUGIN_ID' removed or invalidated its plugin descriptor.",
                output,
            )
        }
    }

    @Test
    @Ignore
    fun `prepare sandbox with plugin dependency with classes directory`() {
        val plugin = createPlugin()

        writeJavaFile()

        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellij {
                    plugins = ['${plugin.invariantSeparatorsPathString}']
                    pluginName = 'myPluginName'
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "plugins/myPluginName/lib/projectName.jar",
                "config/options/updates.xml",
                "plugins/${plugin.name}/classes/A.class",
                "plugins/${plugin.name}/classes/someResources.properties",
                "plugins/${plugin.name}/META-INF/plugin.xml",
            ),
            collectPaths(sandbox),
        )
    }

    private fun createPlugin() = createTempDirectory("tmp").also {
        it.resolve("classes").createDirectory().apply {
            resolve("A.class").createFile()
            resolve("someResources.properties").createFile()
        }
        it.resolve("META-INF").createDirectory().apply {
            resolve("plugin.xml") write //language=xml
                    """
                    <idea-plugin>
                      <id>$name</id>
                      <name>Test</name>
                      <version>1.0</version>
                      <idea-version since-build="221.6008" until-build="221.*" />
                      <vendor url="https://jetbrains.com">JetBrains</vendor>
                      <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
                      <change-notes/>
                    </idea-plugin>
                    """.trimIndent()
        }
    }

    @Test
    fun `prepare custom sandbox task`() {
        writeJavaFile()
        dir.resolve("src/main/resources/META-INF/other.xml") write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        dir.resolve("src/main/resources/META-INF/nonIncluded.xml") write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <depends config-file="other.xml" />
                </idea-plugin>
                """.trimIndent()

        val customSandbox = dir.resolve("customSandbox")
        buildFile write //language=kotlin
                """
                dependencies {
                    implementation("joda-time:joda-time:2.8.1")
                    intellijPlatform {
                        bundledPlugin("com.intellij.copyright")
                    }
                }
                intellijPlatform {
                    sandboxContainer = file("${customSandbox.invariantSeparatorsPathString}")
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "config/disabled_plugins.txt",
                "plugins/projectName/lib/joda-time-2.8.1.jar",
                "plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(customSandbox.resolve("projectName").resolve("$intellijPlatformType-$intellijPlatformVersion")),
        )
    }

    @Test
    fun `use gradle project name if plugin name is not defined`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "config/disabled_plugins.txt",
                "plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `disable ide update without updates_xml`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertFileContent(
            sandbox.resolve("config/options/updates.xml"),
            """
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false" />
              </component>
            </application>
            """.trimIndent(),
        )
    }

    @Test
    fun `prepare sandbox does not rewrite unchanged config files`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                intellijPlatform {
                    splitMode = true
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        val updatesLastModifiedTime = updatesFile.getLastModifiedTime()
        val disabledPluginsLastModifiedTime = disabledPluginsFile.getLastModifiedTime()
        val splitModeFrontendPropertiesLastModifiedTime = splitModeFrontendPropertiesFile.getLastModifiedTime()

        Thread.sleep(1_100)

        build(Tasks.PREPARE_SANDBOX, args = listOf("--rerun-tasks"))

        assertEquals(updatesLastModifiedTime, updatesFile.getLastModifiedTime())
        assertEquals(disabledPluginsLastModifiedTime, disabledPluginsFile.getLastModifiedTime())
        assertEquals(splitModeFrontendPropertiesLastModifiedTime, splitModeFrontendPropertiesFile.getLastModifiedTime())
    }

    @Test
    fun `disable ide update without updates component`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX) {
            updatesFile overwrite //language=xml
                    """
                    <application>
                        <component name="SomeOtherComponent">
                            <option name="SomeOption" value="false" />
                        </component>
                    </application>
                    """.trimIndent()
        }

        build(Tasks.PREPARE_SANDBOX) {
            assertFileContent(
                updatesFile,
                """
                <application>
                    <component name="SomeOtherComponent">
                        <option name="SomeOption" value="false" />
                    </component>
                </application>
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `disable ide update without check_needed option`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX) {
            updatesFile overwrite //language=xml
                    """
                <application>
                    <component name="UpdatesConfigurable">
                        <option name="SomeOption" value="false" />
                    </component>
                </application>
                """.trimIndent()
        }

        build(Tasks.PREPARE_SANDBOX) {
            assertFileContent(
                updatesFile,
                """
                <application>
                    <component name="UpdatesConfigurable">
                        <option name="SomeOption" value="false" />
                    </component>
                </application>
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `disable ide update without value attribute`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        updatesFile write //language=xml
                """
                <application>
                    <component name="UpdatesConfigurable">
                        <option name="CHECK_NEEDED" />
                    </component>
                </application>
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertFileContent(
            updatesFile,
            """
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false" />
              </component>
            </application>
            """.trimIndent(),
        )
    }

    @Test
    fun `disable ide update`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        updatesFile write //language=xml
                """
                <application>
                    <component name="UpdatesConfigurable">
                        <option name="CHECK_NEEDED" value="true" />
                    </component>
                </application>
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertFileContent(
            updatesFile,
            """
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false" />
              </component>
            </application>
            """.trimIndent(),
        )
    }

    @Test
    fun `disable ide update with updates_xml empty`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        updatesFile write ""

        build(Tasks.PREPARE_SANDBOX)

        assertFileContent(
            updatesFile,
            """
            <application>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false" />
              </component>
            </application>
            """.trimIndent(),
        )
    }

    @Test
    fun `disable ide update with complex updates_xml`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX) {
            updatesFile overwrite //language=xml
                    """
                    <application>
                        <component name="UpdatesConfigurable">
                            <enabledExternalComponentSources>
                                <item value="Android SDK" />
                            </enabledExternalComponentSources>
                            <option name="externalUpdateChannels">
                                <map>
                                    <entry key="Android SDK" value="Stable Channel" />
                                </map>
                            </option>
                            <knownExternalComponentSources>
                                <item value="Android SDK" />
                            </knownExternalComponentSources>
                            <option name="LAST_BUILD_CHECKED" value="IC-202.8194.7" />
                            <option name="LAST_TIME_CHECKED" value="1622537478550" />
                            <option name="CHECK_NEEDED" value="false" />
                        </component>
                    </application>
                    """.trimIndent()
        }

        build(Tasks.PREPARE_SANDBOX) {
            assertFileContent(
                updatesFile,
                """
                <application>
                    <component name="UpdatesConfigurable">
                        <enabledExternalComponentSources>
                            <item value="Android SDK" />
                        </enabledExternalComponentSources>
                        <option name="externalUpdateChannels">
                            <map>
                                <entry key="Android SDK" value="Stable Channel" />
                            </map>
                        </option>
                        <knownExternalComponentSources>
                            <item value="Android SDK" />
                        </knownExternalComponentSources>
                        <option name="LAST_BUILD_CHECKED" value="IC-202.8194.7" />
                        <option name="LAST_TIME_CHECKED" value="1622537478550" />
                        <option name="CHECK_NEEDED" value="false" />
                    </component>
                </application>
                """.trimIndent(),
            )
        }
    }

    @Test
    fun `replace jar on version changing`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        buildFile write //language=kotlin
                """
                version = "1.0.1"
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "config/disabled_plugins.txt",
                "plugins/projectName/lib/projectName-1.0.1.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `rename jars with same names`() {
        emptyZipFile("one/core.jar")
        emptyZipFile("two/core.jar")
        emptyZipFile("three/core.jar")
        writeJavaFile()

        buildFile write //language=kotlin
                """
                dependencies {
                    implementation("joda-time:joda-time:2.8.1")
                    implementation(fileTree("one"))
                    implementation(fileTree("two"))
                    implementation(fileTree("three"))
                }
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "config/options/updates.xml",
                "config/disabled_plugins.txt",
                "plugins/projectName/lib/core_1.jar",
                "plugins/projectName/lib/core_2.jar",
                "plugins/projectName/lib/joda-time-2.8.1.jar",
                "plugins/projectName/lib/projectName-1.0.0.jar",
                "plugins/projectName/lib/core.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepareTestingSandbox runs before test`() {
        writeJavaFile()
        dir.resolve("additional/some-file").ensureExists()

        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        buildFile write //language=kotlin
                """
                tasks {
                    ${Tasks.PREPARE_TEST_SANDBOX} {
                        from("additional")
                    }
                }
                """.trimIndent()

        build(Tasks.External.TEST)

        assertEquals(
            setOf(
                "plugins-test/projectName/lib/projectName-1.0.0.jar",
                "plugins-test/some-file",
                "config-test/options/updates.xml",
                "config-test/disabled_plugins.txt",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `create sandbox in a custom location`() {
        val taskName = "customRunIde"

        buildFile write //language=kotlin
                """
                val $taskName by intellijPlatformTesting.runIde.registering {
                    task {
                        enabled = false
                    }
                    prepareSandboxTask {
                        sandboxDirectory = project.layout.buildDirectory.dir("custom-sandbox")
                    }
                }
                """.trimIndent()

        build(taskName)

        assertExists(buildDirectory.resolve("custom-sandbox/config_$taskName"))
        assertExists(buildDirectory.resolve("custom-sandbox/plugins_$taskName"))
        assertExists(buildDirectory.resolve("custom-sandbox/log_$taskName"))
        assertExists(buildDirectory.resolve("custom-sandbox/system_$taskName"))
    }

    @Test
    fun `create test sandbox in a custom location`() {
        val taskName = "customTest"

        buildFile write //language=kotlin
                """
                val $taskName by intellijPlatformTesting.testIde.registering {
                    task {
                        enabled = false
                    }
                    prepareSandboxTask {
                        sandboxDirectory = project.layout.buildDirectory.dir("custom-sandbox")
                    }
                }
                """.trimIndent()

        build(taskName)

        assertExists(buildDirectory.resolve("custom-sandbox/config_$taskName"))
        assertExists(buildDirectory.resolve("custom-sandbox/plugins_$taskName"))
        assertExists(buildDirectory.resolve("custom-sandbox/log_$taskName"))
        assertExists(buildDirectory.resolve("custom-sandbox/system_$taskName"))
    }

    @Test
    fun `create test sandbox in a custom location with custom suffix`() {
        val taskName = "customTest"

        buildFile write //language=kotlin
                """
                val $taskName by intellijPlatformTesting.testIde.registering {
                    task {
                        enabled = false
                    }
                    prepareSandboxTask {
                        sandboxDirectory = project.layout.buildDirectory.dir("custom-sandbox")
                        sandboxSuffix = "-foo"
                    }
                }
                """.trimIndent()

        build(taskName)

        assertExists(buildDirectory.resolve("custom-sandbox/config-foo"))
        assertExists(buildDirectory.resolve("custom-sandbox/plugins-foo"))
        assertExists(buildDirectory.resolve("custom-sandbox/log-foo"))
        assertExists(buildDirectory.resolve("custom-sandbox/system-foo"))
    }

    @Test
    fun `reuses configuration cache`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)
        buildWithConfigurationCache(Tasks.PREPARE_SANDBOX)

        buildWithConfigurationCache(Tasks.PREPARE_SANDBOX) {
            assertConfigurationCacheReused()
        }
    }

    private fun createPluginFixtureRepository(includeControlPlugin: Boolean = false) =
        dir.resolve("plugin-fixture-repository").also { repository ->
            writePluginFixture(
                repository = repository,
                id = FILTER_TARGET_PLUGIN_ID,
                rootName = "filter-target",
                mainJarName = FILTER_TARGET_MAIN_JAR,
                bundledLibraries = listOf(
                    "lib/$FILTER_TARGET_JENA_CORE_JAR",
                    "lib/modules/$FILTER_TARGET_JENA_ARQ_JAR",
                    "lib/$FILTER_TARGET_SUPPORT_JAR",
                ),
            )
            if (includeControlPlugin) {
                writePluginFixture(
                    repository = repository,
                    id = FILTER_CONTROL_PLUGIN_ID,
                    rootName = "filter-control",
                    mainJarName = FILTER_CONTROL_MAIN_JAR,
                    bundledLibraries = listOf("lib/$FILTER_CONTROL_JENA_JAR"),
                )
            }
        }

    private fun writePluginFixture(
        repository: Path,
        id: String,
        rootName: String,
        mainJarName: String,
        bundledLibraries: List<String>,
    ) {
        val pluginRoot = dir.resolve("plugin-fixture-staging/$id/$rootName")
        val files = buildList {
            add(pluginRoot.resolve("lib/$mainJarName").also {
                writePlugin(it, descriptorName = "plugin.xml", id = id, name = rootName)
            })
            addAll(bundledLibraries.map { relativePath ->
                pluginRoot.resolve(relativePath).also(::writeEmptyJar)
            })
        }
        val moduleDirectory = repository.resolve(
            "${PLUGIN_FIXTURE_GROUP.replace('.', '/')}/$id/$PLUGIN_FIXTURE_VERSION",
        ).createDirectories()

        val artifact = moduleDirectory.resolve("$id-$PLUGIN_FIXTURE_VERSION.zip")
        ZipOutputStream(Files.newOutputStream(artifact)).use { zip ->
            files.forEach { file ->
                zip.putNextEntry(
                    ZipEntry("$rootName/${file.relativeTo(pluginRoot).invariantSeparatorsPathString}")
                )
                Files.copy(file, zip)
                zip.closeEntry()
            }
        }
        moduleDirectory.resolve("$id-$PLUGIN_FIXTURE_VERSION.pom") write //language=xml
                """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>$PLUGIN_FIXTURE_GROUP</groupId>
                  <artifactId>$id</artifactId>
                  <version>$PLUGIN_FIXTURE_VERSION</version>
                  <packaging>zip</packaging>
                </project>
                """.trimIndent()
    }

    private fun configurePluginFixtureRepository(repository: Path) {
        buildFile write pluginFixtureRepository(repository)
    }

    private fun pluginFixtureRepository(repository: Path) = //language=kotlin
        """
        repositories {
            exclusiveContent {
                forRepository {
                    maven {
                        name = "pluginFixture"
                        url = uri("${repository.invariantSeparatorsPathString}")
                    }
                }
                filter {
                    includeGroup("$PLUGIN_FIXTURE_GROUP")
                }
            }
        }
        """.trimIndent()

    private fun configureClasspathAssertionTask(
        taskName: String,
        configurationName: String,
        expectedPresent: List<String>,
        expectedAbsent: List<String>,
    ) {
        val taskClassName = taskName.replaceFirstChar(Char::uppercaseChar) + "Task"
        val present = expectedPresent.joinToString(prefix = "listOf(", postfix = ")") { "\"$it\"" }
        val absent = expectedAbsent.joinToString(prefix = "listOf(", postfix = ")") { "\"$it\"" }

        buildFile write //language=kotlin
                """
                abstract class $taskClassName : org.gradle.api.DefaultTask() {
                    @get:org.gradle.api.tasks.Classpath
                    abstract val classpath: org.gradle.api.file.ConfigurableFileCollection

                    @get:org.gradle.api.tasks.Input
                    abstract val expectedPresent: org.gradle.api.provider.ListProperty<String>

                    @get:org.gradle.api.tasks.Input
                    abstract val expectedAbsent: org.gradle.api.provider.ListProperty<String>

                    @org.gradle.api.tasks.TaskAction
                    fun assertClasspath() {
                        val names = classpath.files.map { it.name }.toSet()
                        check(names.containsAll(expectedPresent.get())) {
                            "Missing expected plugin libraries: ${'$'}{expectedPresent.get().toSet() - names}; classpath: ${'$'}names"
                        }
                        check(names.intersect(expectedAbsent.get().toSet()).isEmpty()) {
                            "Excluded plugin libraries remain on the classpath: ${'$'}{names.intersect(expectedAbsent.get().toSet())}"
                        }
                    }
                }

                tasks.register<$taskClassName>("$taskName") {
                    classpath.from(configurations.named("$configurationName"))
                    expectedPresent.set($present)
                    expectedAbsent.set($absent)
                }
                """.trimIndent()
    }

    companion object {
        private const val PLUGIN_FIXTURE_GROUP = Constants.Configurations.Dependencies.MARKETPLACE_GROUP
        private const val PLUGIN_FIXTURE_VERSION = "1.0.0"
        private const val FILTER_TARGET_PLUGIN_ID = "com.example.filter-target"
        private const val FILTER_CONTROL_PLUGIN_ID = "com.example.filter-control"
        private const val FILTER_TARGET_MAIN_JAR = "filter-target.jar"
        private const val FILTER_TARGET_JENA_CORE_JAR = "jena-core-4.2.0.jar"
        private const val FILTER_TARGET_JENA_ARQ_JAR = "jena-arq-4.2.0.jar"
        private const val FILTER_TARGET_SUPPORT_JAR = "target-support.jar"
        private const val FILTER_CONTROL_MAIN_JAR = "filter-control.jar"
        private const val FILTER_CONTROL_JENA_JAR = "jena-core-5.0.0.jar"
    }
}
