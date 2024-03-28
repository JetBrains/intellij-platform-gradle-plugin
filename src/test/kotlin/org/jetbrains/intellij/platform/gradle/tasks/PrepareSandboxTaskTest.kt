// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.io.path.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class PrepareSandboxTaskTest : IntelliJPluginTestBase() {

    private val sandbox get() = buildDirectory.resolve(Sandbox.CONTAINER).resolve("$intellijPlatformType-$intellijPlatformVersion")

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
                  <idea-version since-build="223.8836" until-build="223.*" />
                  <version>1.0.0</version>
                  <name>myPluginName</name>
                  <depends config-file="other.xml" />
                </idea-plugin>
                """.trimIndent()
            )
        }
    }

    @Test
    @Ignore
    fun `prepare ui tests sandbox task`() {
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
                dependencies {
                    implementation("joda-time:joda-time:2.8.1")
    
                    intellijPlatform {
                        bundledPlugin("com.intellij.copyright")
                    }
                }
    //            downloadRobotServerPlugin.version = '0.11.1'
                """.trimIndent()

        build(Tasks.PREPARE_UI_TEST_SANDBOX)

        assertEquals(
            setOf(
                "config-uiTest/options/updates.xml",
                "plugins-uiTest/projectName/lib/joda-time-2.8.1.jar",
                "plugins-uiTest/projectName/lib/projectName-1.0.0.jar",
                "plugins-uiTest/robot-server-plugin/lib/robot-server-plugin-0.11.1.jar",
            ),
            collectPaths(sandbox),
        )
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
                "plugins/projectName/lib/projectName-1.0.0.jar",
                "plugins/org.jetbrains.postfixCompletion-0.8-beta.jar",
            ),
            collectPaths(sandbox),
        )
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
                "plugins/markdown/lib/markdown.jar",
                "plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )
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
                "plugins/projectName/lib/joda-time-2.8.1.jar",
                "plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(customSandbox.resolve("$intellijPlatformType-$intellijPlatformVersion")),
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
    fun `disable ide update without updates component`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        val updatesFile = sandbox.resolve("config/options/updates.xml") write //language=xml
                """
                <application>
                    <component name="SomeOtherComponent">
                        <option name="SomeOption" value="false" />
                    </component>
                </application>
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertFileContent(
            updatesFile,
            """
            <application>
              <component name="SomeOtherComponent">
                <option name="SomeOption" value="false" />
              </component>
              <component name="UpdatesConfigurable">
                <option name="CHECK_NEEDED" value="false" />
              </component>
            </application>
            """.trimIndent(),
        )
    }

    @Test
    fun `disable ide update without check_needed option`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        val updatesFile = sandbox.resolve("config/options/updates.xml") write //language=xml
                """
                <application>
                    <component name="UpdatesConfigurable">
                        <option name="SomeOption" value="false" />
                    </component>
                </application>
                """.trimIndent()

        build(Tasks.PREPARE_SANDBOX)

        assertFileContent(
            updatesFile,
            """
            <application>
              <component name="UpdatesConfigurable">
                <option name="SomeOption" value="false" />
                <option name="CHECK_NEEDED" value="false" />
              </component>
            </application>
            """.trimIndent(),
        )
    }

    @Test
    fun `disable ide update without value attribute`() {
        pluginXml write //language=xml
                """
                <idea-plugin />
                """.trimIndent()

        val updatesFile = sandbox.resolve("config/options/updates.xml") write //language=xml
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

        val updatesFile = sandbox.resolve("config/options/updates.xml") write //language=xml
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

        val updatesFile = sandbox.resolve("config/options/updates.xml") write ""

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

        val updatesFile = sandbox.resolve("config/options/updates.xml") write //language=xml
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

        build(Tasks.PREPARE_SANDBOX)

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
                "plugins/projectName/lib/projectName-1.0.1.jar",
                "config/options/updates.xml",
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
            ),
            collectPaths(sandbox),
        )
    }
}
