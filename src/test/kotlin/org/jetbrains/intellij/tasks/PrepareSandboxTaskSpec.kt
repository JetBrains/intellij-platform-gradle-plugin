// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import org.jetbrains.intellij.IntelliJPluginConstants.DEFAULT_SANDBOX
import org.jetbrains.intellij.IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.io.File
import java.nio.file.Files.createTempDirectory
import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity", "ComplexRedundantLet")
class PrepareSandboxTaskSpec : IntelliJPluginSpecBase() {

    private val sandbox get() = File(buildDirectory, DEFAULT_SANDBOX)

    @Test
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

        buildFile.groovy(
            """
            version = '0.42.123'
            
            intellij {
                pluginName = 'myPluginName'
                plugins = [project('nestedProject')]
            }
            """.trimIndent()
        )

        file("settings.gradle").groovy(
            """
            include 'nestedProject'
            """.trimIndent()
        )

        file("nestedProject/build.gradle").groovy(
            """
            repositories { mavenCentral() }
            apply plugin: 'org.jetbrains.intellij'
            version = '0.42.123'
            
            compileJava {
                sourceCompatibility = '1.8'
                targetCompatibility = '1.8'
            }
            
            intellij {
                version = '$intellijVersion'
                downloadSources = false
                pluginName = 'myNestedPluginName'
                instrumentCode = false
            }
            """.trimIndent()
        )

        file("nestedProject/src/main/java/NestedAppFile.java").groovy(
            """
            class NestedAppFile {}
            """.trimIndent()
        )

        file("nestedProject/src/main/resources/META-INF/plugin.xml").xml(pluginXml.readText())

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar",
                "/plugins/myPluginName/lib/projectName-0.42.123.jar",
            ),
            collectPaths(sandbox),
        )

        val jar = File(sandbox, "/plugins/myPluginName/lib/projectName-0.42.123.jar")
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "App.class",
                "META-INF/plugin.xml",
            ),
            collectPaths(ZipFile(jar)),
        )

        val nestedProjectJar = File(sandbox, "/plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar")
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NestedAppFile.class",
                "META-INF/plugin.xml",
            ),
            collectPaths(ZipFile(nestedProjectJar)),
        )
    }

    @Test
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

        buildFile.groovy(
            """
            allprojects {
                repositories { mavenCentral() }
                version = '0.42.123'
                apply plugin: 'org.jetbrains.intellij'
                intellij { 
                    downloadSources = false
                    version = "$intellijVersion"
                }
            }
            project(':') {
                intellij {
                    pluginName = 'myPluginName'
                    plugins = [project(':nestedProject')]
                }
            }
            project(':nestedProject') {
                compileJava {
                    sourceCompatibility = '1.8'
                    targetCompatibility = '1.8'
                }
                intellij {
                    pluginName = 'myNestedPluginName'
                    instrumentCode = false
                }
            }
            """.trimIndent()
        )

        file("settings.gradle").groovy(
            """
            include 'nestedProject'            
            """.trimIndent()
        )

        file("nestedProject/src/main/java/NestedAppFile.java").java(
            """
            class NestedAppFile {}
            """.trimIndent()
        )

        file("nestedProject/src/main/resources/META-INF/plugin.xml").xml(pluginXml.readText())

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/plugins/myPluginName/lib/projectName-0.42.123.jar",
                "/plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar",
                "/config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )

        val jar = File(sandbox, "/plugins/myPluginName/lib/projectName-0.42.123.jar")
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "App.class",
                "META-INF/plugin.xml",
            ),
            collectPaths(ZipFile(jar)),
        )

        val nestedProjectJar = File(sandbox, "/plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar")
        assertEquals(
            setOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "NestedAppFile.class",
                "META-INF/plugin.xml",
            ),
            collectPaths(ZipFile(nestedProjectJar)),
        )
    }

    @Test
    fun `prepare sandbox task without plugin_xml`() {
        writeJavaFile()

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/plugins/myPluginName/lib/projectName-0.42.123.jar",
                "/plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "/config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox task`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        file("src/main/resources/META-INF/nonIncluded.xml").xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
              <depends config-file="other.xml" />
            </idea-plugin>    
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/plugins/myPluginName/lib/projectName-0.42.123.jar",
                "/plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "/config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )

        val jar = ZipFile(File(sandbox, "/plugins/myPluginName/lib/projectName-0.42.123.jar"))
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
              <version>0.42.123</version>
              <idea-version since-build="212.5712" until-build="212.*" />
              <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent()
        )
    }

    @Test
    fun `prepare ui tests sandbox task`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        file("src/main/resources/META-INF/nonIncluded.xml").xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            downloadRobotServerPlugin.version = '0.11.1'
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
            """.trimIndent()
        )

        build(PREPARE_UI_TESTING_SANDBOX_TASK_NAME)

        assertTrue(
            collectPaths(sandbox).containsAll(
                setOf(
                    "/plugins-uiTest/myPluginName/lib/projectName-0.42.123.jar",
                    "/plugins-uiTest/myPluginName/lib/joda-time-2.8.1.jar",
                    "/config-uiTest/options/updates.xml",
                    "/plugins-uiTest/robot-server-plugin/lib/robot-server-plugin-0.11.1.jar",
                )
            )
        )
    }

    @Test
    fun `prepare sandbox with external jar-type plugin`() {
        writeJavaFile()

        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            intellij {
                plugins = ['org.jetbrains.postfixCompletion:0.8-beta']
                pluginName = 'myPluginName'
            }
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/plugins/org.jetbrains.postfixCompletion-0.8-beta.jar",
                "/plugins/myPluginName/lib/projectName.jar",
                "/config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox with external zip-type plugin`() {
        writeJavaFile()

        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            intellij {
                plugins = ['org.intellij.plugins.markdown:$testMarkdownPluginVersion']
                pluginName = 'myPluginName'
            }
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/config/options/updates.xml",
                "/plugins/markdown/lib/google-api-client-1.25.0.jar",
                "/plugins/markdown/lib/google-api-services-drive-v3-rev197-1.25.0.jar",
                "/plugins/markdown/lib/google-http-client-1.25.0.jar",
                "/plugins/markdown/lib/google-http-client-jackson2-1.25.0.jar",
                "/plugins/markdown/lib/google-oauth-client-1.25.0.jar",
                "/plugins/markdown/lib/j2objc-annotations-1.1.jar",
                "/plugins/markdown/lib/markdown.jar",
                "/plugins/myPluginName/lib/projectName.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox with plugin dependency with classes directory`() {
        val plugin = createPlugin()

        writeJavaFile()

        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            intellij {
                plugins = ['${adjustWindowsPath(plugin.canonicalPath)}']
                pluginName = 'myPluginName'
            }
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

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

    private fun createPlugin() = createTempDirectory("tmp").toFile().also {
        File(it, "classes/").mkdir()
        File(it, "META-INF/").mkdir()
        File(it, "classes/A.class").createNewFile()
        File(it, "classes/someResources.properties").createNewFile()
        File(it, "META-INF/plugin.xml").xml(
            """
            <idea-plugin>
              <id>${it.name}</id>
              <name>Test</name>
              <version>1.0</version>
              <idea-version since-build="212.5712" until-build="212.*" />
              <vendor url="https://jetbrains.com">JetBrains</vendor>
              <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
              <change-notes/>
            </idea-plugin>
            """.trimIndent()
        )
    }

    @Test
    fun `prepare custom sandbox task`() {
        writeJavaFile()

        file("src/main/resources/META-INF/other.xml").xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        file("src/main/resources/META-INF/nonIncluded.xml").xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        pluginXml.xml(
            """
            <idea-plugin>
                <depends config-file="other.xml" />
            </idea-plugin>
            """.trimIndent()
        )

        val sandboxPath = adjustWindowsPath("${dir.canonicalPath}/customSandbox")
        buildFile.groovy(
            """
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
                sandboxDir = '$sandboxPath'
            }
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        val sandbox = File(sandboxPath)
        assertEquals(
            setOf(
                "/plugins/myPluginName/lib/projectName-0.42.123.jar",
                "/plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "/config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `use gradle project name if plugin name is not defined`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/plugins/projectName/lib/projectName.jar",
                "/config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `disable ide update without updates_xml`() {
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            File(buildDirectory, "$DEFAULT_SANDBOX/config/options/updates.xml"),
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
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        val updatesFile = File(directory("build/$DEFAULT_SANDBOX/config/options"), "updates.xml")
        updatesFile.xml(
            """
            <application>
                <component name="SomeOtherComponent">
                    <option name="SomeOption" value="false" />
                </component>
            </application>
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            File(buildDirectory, "$DEFAULT_SANDBOX/config/options/updates.xml"),
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
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        val updatesFile = File(directory("build/$DEFAULT_SANDBOX/config/options"), "updates.xml")
        updatesFile.xml(
            """
            <application>
                <component name="UpdatesConfigurable">
                    <option name="SomeOption" value="false" />
                </component>
            </application>
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            File(buildDirectory, "$DEFAULT_SANDBOX/config/options/updates.xml"),
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
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        val updatesFile = File(directory("build/$DEFAULT_SANDBOX/config/options"), "updates.xml")

        updatesFile.xml(
            """
            <application>
                <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" />
                </component>
            </application>
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            File(buildDirectory, "$DEFAULT_SANDBOX/config/options/updates.xml"),
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
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        val updatesFile = File(directory("build/$DEFAULT_SANDBOX/config/options"), "updates.xml")

        updatesFile.xml(
            """
            <application>
                <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" value="true" />
                </component>
            </application>
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            File(buildDirectory, "$DEFAULT_SANDBOX/config/options/updates.xml"),
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
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        val updatesFile = File(directory("build/$DEFAULT_SANDBOX/config/options"), "updates.xml")

        updatesFile.xml("")

        build(PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            File(buildDirectory, "$DEFAULT_SANDBOX/config/options/updates.xml"),
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
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        val updatesFile = File(directory("build/$DEFAULT_SANDBOX/config/options"), "updates.xml")

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

        build(PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            File(buildDirectory, "$DEFAULT_SANDBOX/config/options/updates.xml"),
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
        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            version = '0.42.123'
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        buildFile.groovy(
            """
            version = '0.42.124'
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/plugins/projectName/lib/projectName-0.42.124.jar",
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
            version = '0.42.123'
            
            intellij { 
                pluginName = 'myPluginName' 
            }
            
            dependencies { 
                implementation 'joda-time:joda-time:2.8.1'
                implementation fileTree('one')
                implementation fileTree('two')
                implementation fileTree('three')
            }
            """.trimIndent()
        )

        build(PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            setOf(
                "/plugins/myPluginName/lib/projectName-0.42.123.jar",
                "/plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "/plugins/myPluginName/lib/core.jar",
                "/plugins/myPluginName/lib/core_1.jar",
                "/plugins/myPluginName/lib/core_2.jar",
                "/config/options/updates.xml",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepareTestingSandbox runs before test`() {
        writeJavaFile()
        file("additional/some-file")

        pluginXml.xml(
            """
            <idea-plugin />
            """.trimIndent()
        )

        buildFile.groovy(
            """
            intellij {
                pluginName = 'myPluginName'
            }
            
            $PREPARE_TESTING_SANDBOX_TASK_NAME {
                from("additional")
            }
            """.trimIndent()
        )

        build("test")

        assertEquals(
            setOf(
                "/plugins-test/myPluginName/lib/projectName.jar",
                "/plugins-test/some-file",
                "/config-test/options/updates.xml",
            ),
            collectPaths(sandbox),
        )
    }
}
