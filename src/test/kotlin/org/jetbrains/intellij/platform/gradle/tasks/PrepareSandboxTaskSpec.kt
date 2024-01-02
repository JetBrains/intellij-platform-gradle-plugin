// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginSpecBase
import kotlin.io.path.*
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals

class PrepareSandboxTaskSpec : IntelliJPluginSpecBase() {

    private val sandbox get() = buildDirectory.resolve(Sandbox.CONTAINER).resolve("$intellijType-$intellijVersion")

    @Test
    @Ignore
    fun `prepare sandbox for two plugins`() {
        writeJavaFile()

        pluginXml.xml(
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
        )

        buildFile.kotlin(
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
        )

        settingsFile.kotlin(
            """
            include("nestedProject")
            """.trimIndent()
        )

        file("nestedProject/build.gradle.kts").kotlin(
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
                    create("$intellijType", "$intellijVersion")
                }
            }
            
            intellijPlatform {
                instrumentCode = false
                pluginConfiguration {
                    name = "myNestedPluginName"
                }
            }
            """.trimIndent()
        )

        file("nestedProject/src/main/java/NestedAppFile.java").java(
            """
            class NestedAppFile {}
            """.trimIndent()
        )

        file("nestedProject/src/main/resources/META-INF/plugin.xml").xml(pluginXml.readText())

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/myPluginName/lib/projectName-1.0.0.jar",
                "/plugins/myPluginName/lib/nestedProject-1.0.0.jar",

                // OLD:
                "/config/options/updates.xml",
                "/plugins/myNestedPluginName/lib/nestedProject-1.0.0.jar",
                "/plugins/myPluginName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )

        val jar = sandbox.resolve("/plugins/myPluginName/lib/projectName-1.0.0.jar").toZip()
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "App.class",
                "META-INF/plugin.xml",
            ),
            collectPaths(jar),
        )

        val nestedProjectJar = sandbox.resolve("/plugins/myNestedPluginName/lib/nestedProject-1.0.0.jar").toZip()
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NestedAppFile.class",
                "META-INF/plugin.xml",
            ),
            collectPaths(nestedProjectJar),
        )
    }

    @Test
    @Ignore
    fun `prepare sandbox for two plugins with evaluated project`() {
        writeJavaFile()

        pluginXml.xml(
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
        )

        buildFile.kotlin(
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
        )

        settingsFile.groovy(
            """
            include("nestedProject")
            """.trimIndent()
        )

        file("nestedProject/src/main/java/NestedAppFile.java").java(
            """
            class NestedAppFile {}
            """.trimIndent()
        )

        file("nestedProject/src/main/resources/META-INF/plugin.xml").xml(pluginXml.readText())

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/plugins/myPluginName/lib/projectName-1.0.0.jar",
                "/plugins/myNestedPluginName/lib/nestedProject-1.0.0.jar",
                "/config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )

        val jar = sandbox.resolve("/plugins/myPluginName/lib/projectName-1.0.0.jar").toZip()
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "App.class",
                "META-INF/plugin.xml",
            ),
            collectPaths(jar),
        )

        val nestedProjectJar = sandbox.resolve("/plugins/myNestedPluginName/lib/nestedProject-1.0.0.jar").toZip()
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NestedAppFile.class",
                "META-INF/plugin.xml",
            ),
            collectPaths(nestedProjectJar),
        )
    }

    @Test
    fun `prepare sandbox task without plugin_xml`() {
        writeJavaFile()

        buildFile.kotlin(
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
        )

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "/plugins/myPluginName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox task`() {
        writeJavaFile()
        file("src/main/resources/META-INF/other.xml").xml("<idea-plugin />")
        file("src/main/resources/META-INF/nonIncluded.xml").xml("<idea-plugin />")

        pluginXml.xml(
            """
            <idea-plugin>
              <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.kotlin(
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
        )

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "/plugins/myPluginName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )

        val jar = sandbox.resolve("plugins/myPluginName/lib/projectName-1.0.0.jar").toZip()
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

    @Test
    @Ignore
    fun `prepare ui tests sandbox task`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml("<idea-plugin />")

        file("src/main/resources/META-INF/nonIncluded.xml").xml("<idea-plugin />")

        pluginXml.xml(
            """
            <idea-plugin>
                <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.kotlin(
            """
            dependencies {
                implementation("joda-time:joda-time:2.8.1")

                intellijPlatform {
                    bundledPlugin("com.intellij.copyright")
                }
            }
//            downloadRobotServerPlugin.version = '0.11.1'
            """.trimIndent()
        )

        build(Tasks.PREPARE_UI_TESTING_SANDBOX)

        assertEquals(
            setOf(
                "/config-uiTest/options/updates.xml",
                "/plugins-uiTest/projectName/lib/joda-time-2.8.1.jar",
                "/plugins-uiTest/projectName/lib/projectName-1.0.0.jar",
                "/plugins-uiTest/robot-server-plugin/lib/robot-server-plugin-0.11.1.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox with external jar-type plugin`() {
        writeJavaFile()

        pluginXml.xml("<idea-plugin />")

        buildFile.kotlin(
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
        )

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/projectName/lib/projectName-1.0.0.jar",
                "/plugins/org.jetbrains.postfixCompletion-0.8-beta.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox with external zip-type plugin`() {
        writeJavaFile()

        pluginXml.xml("<idea-plugin />")

        buildFile.groovy(
            """
            repositories {
                intellijPlatform {
                    marketplace()
                }
            }
            dependencies {
                intellijPlatform {
                    plugin("org.intellij.plugins.markdown", "$testMarkdownPluginVersion")
                }
            }
            """.trimIndent()
        )

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/markdown/lib/markdown.jar",
                "/plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    @Ignore
    fun `prepare sandbox with plugin dependency with classes directory`() {
        val plugin = createPlugin()

        writeJavaFile()

        pluginXml.xml("<idea-plugin />")

        buildFile.groovy(
            """
            intellij {
                plugins = ['${adjustWindowsPath(plugin.pathString)}']
                pluginName = 'myPluginName'
            }
            """.trimIndent()
        )

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/plugins/myPluginName/lib/projectName.jar",
                "/config/options/updates.xml",
                "/plugins/${plugin.name}/classes/A.class",
                "/plugins/${plugin.name}/classes/someResources.properties",
                "/plugins/${plugin.name}/META-INF/plugin.xml",
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
            resolve("plugin.xml").xml(
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
            )
        }
    }

    @Test
    fun `prepare custom sandbox task`() {
        writeJavaFile()
        file("src/main/resources/META-INF/other.xml").xml("<idea-plugin />")
        file("src/main/resources/META-INF/nonIncluded.xml").xml("<idea-plugin />")

        pluginXml.xml(
            """
            <idea-plugin>
                <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent()
        )

        val customSandbox = dir.resolve("customSandbox")
        val sandboxPath = adjustWindowsPath(customSandbox.pathString)
        buildFile.kotlin(
            """
            dependencies {
                implementation("joda-time:joda-time:2.8.1")
                intellijPlatform {
                    bundledPlugin("com.intellij.copyright")
                }
            }
            intellijPlatform {
                sandboxContainer = file("$sandboxPath")
            }
            """.trimIndent()
        )

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/projectName/lib/joda-time-2.8.1.jar",
                "/plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(customSandbox.resolve("$intellijType-$intellijVersion")),
        )
    }

    @Test
    fun `use gradle project name if plugin name is not defined`() {
        pluginXml.xml("<idea-plugin />")

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/projectName/lib/projectName-1.0.0.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `disable ide update without updates_xml`() {
        pluginXml.xml("<idea-plugin />")

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
        pluginXml.xml("<idea-plugin />")

        val updatesFile = sandbox.resolve("config/options/updates.xml")
        updatesFile.xml(
            """
            <application>
                <component name="SomeOtherComponent">
                    <option name="SomeOption" value="false" />
                </component>
            </application>
            """.trimIndent()
        )

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
        pluginXml.xml("<idea-plugin />")

        val updatesFile = sandbox.resolve("config/options/updates.xml")
        updatesFile.xml(
            """
            <application>
                <component name="UpdatesConfigurable">
                    <option name="SomeOption" value="false" />
                </component>
            </application>
            """.trimIndent()
        )

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
        pluginXml.xml("<idea-plugin />")

        val updatesFile = sandbox.resolve("config/options/updates.xml")
        updatesFile.xml(
            """
            <application>
                <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" />
                </component>
            </application>
            """.trimIndent()
        )

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
        pluginXml.xml("<idea-plugin />")

        val updatesFile = sandbox.resolve("config/options/updates.xml")
        updatesFile.xml(
            """
            <application>
                <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" value="true" />
                </component>
            </application>
            """.trimIndent()
        )

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
        pluginXml.xml("<idea-plugin />")

        val updatesFile = sandbox.resolve("config/options/updates.xml").xml("")

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
        pluginXml.xml("<idea-plugin />")

        val updatesFile = sandbox.resolve("config/options/updates.xml")
        updatesFile.xml(
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
        )

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
        pluginXml.xml("<idea-plugin />")

        build(Tasks.PREPARE_SANDBOX)

        buildFile.kotlin(
            """
            version = "1.0.1"
            """.trimIndent()
        )

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/plugins/projectName/lib/projectName-1.0.1.jar",
                "/config/options/updates.xml",
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

        buildFile.groovy(
            """
            dependencies {
                implementation("joda-time:joda-time:2.8.1")
                implementation(fileTree("one"))
                implementation(fileTree("two"))
                implementation(fileTree("three"))
            }
            """.trimIndent()
        )

        build(Tasks.PREPARE_SANDBOX)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/projectName/lib/core_1.jar",
                "/plugins/projectName/lib/core_2.jar",
                "/plugins/projectName/lib/joda-time-2.8.1.jar",
                "/plugins/projectName/lib/projectName-1.0.0.jar",
                "/plugins/projectName/lib/core.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepareTestingSandbox runs before test`() {
        writeJavaFile()
        file("additional/some-file")

        pluginXml.xml("<idea-plugin />")

        buildFile.kotlin(
            """
            tasks {
                ${Tasks.PREPARE_TESTING_SANDBOX} {
                    from("additional")
                }
            }
            """.trimIndent()
        )

        build("test")

        assertEquals(
            setOf(
                "/plugins-test/projectName/lib/projectName-1.0.0.jar",
                "/plugins-test/some-file",
                "/config-test/options/updates.xml",
            ),
            collectPaths(sandbox),
        )
    }
}
