package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.utils.create
import com.jetbrains.plugin.structure.base.utils.createDir
import com.jetbrains.plugin.structure.base.utils.readText
import org.jetbrains.intellij.IntelliJPluginConstants
import org.jetbrains.intellij.IntelliJPluginSpecBase
import java.nio.file.Files.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("GroovyUnusedAssignment", "PluginXmlValidity")
class PrepareSandboxTaskSpec : IntelliJPluginSpecBase() {

    private val sandbox = buildDirectory.resolve(IntelliJPluginConstants.DEFAULT_SANDBOX)

    @Test
    fun `prepare sandbox for two plugins`() {
        writeJavaFile()

        pluginXml.xml("""
            <idea-plugin>
              <id>org.intellij.test.plugin</id>
              <name>Test</name>
              <version>1.0</version>
              <vendor url="https://jetbrains.com">JetBrains</vendor>
              <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
              <change-notes/>
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            
            intellij {
                pluginName = 'myPluginName'
                plugins = [project('nestedProject')]
            }
        """)

        createFile("settings.gradle").groovy("""
            include 'nestedProject'
        """)

        createFile("nestedProject/build.gradle").groovy("""
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
        """)

        createFile("nestedProject/src/main/java/NestedAppFile.java").java("""
            class NestedAppFile {}
        """)

        createFile("nestedProject/src/main/resources/META-INF/plugin.xml").xml(pluginXml.readText())

        build(":${IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME}")

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar",
                "plugins/myPluginName/lib/projectName-0.42.123.jar",
            ),
            collectPaths(sandbox),
        )

        val jar = sandbox.resolve("plugins/myPluginName/lib/projectName-0.42.123.jar")
        assertEquals(
            listOf(
                "App.class",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/plugin.xml",
            ),
            collectPathsFromZip(jar),
        )

        val nestedProjectJar = sandbox.resolve("plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar")
        assertEquals(
            listOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/plugin.xml",
                "NestedAppFile.class",
            ),
            collectPathsFromZip(nestedProjectJar),
        )
    }

    @Test
    fun `prepare sandbox for two plugins with evaluated project`() {
        writeJavaFile()

        pluginXml.xml("""
            <idea-plugin>
              <id>org.intellij.test.plugin</id>
              <name>Test</name>
              <version>1.0</version>
              <vendor url="https://jetbrains.com">JetBrains</vendor>
              <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
              <change-notes/>
            </idea-plugin>
        """)

        buildFile.groovy("""
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
        """)

        createFile("settings.gradle").groovy("""
            include 'nestedProject'            
        """)

        createFile("nestedProject/src/main/java/NestedAppFile.java").java("""
            class NestedAppFile {}
        """)

        createFile("nestedProject/src/main/resources/META-INF/plugin.xml").xml(pluginXml.readText())

        build(":${IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME}")

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar",
                "plugins/myPluginName/lib/projectName-0.42.123.jar",
            ),
            collectPaths(sandbox),
        )

        val jar = sandbox.resolve("plugins/myPluginName/lib/projectName-0.42.123.jar")
        assertEquals(
            listOf(
                "App.class",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/plugin.xml",
            ),
            collectPathsFromZip(jar),
        )

        val nestedProjectJar = sandbox.resolve("plugins/myNestedPluginName/lib/nestedProject-0.42.123.jar")
        assertEquals(
            listOf(
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/plugin.xml",
                "NestedAppFile.class",
            ),
            collectPathsFromZip(nestedProjectJar),
        )
    }

    @Test
    fun `prepare sandbox task without plugin_xml`() {
        writeJavaFile()

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "plugins/myPluginName/lib/projectName-0.42.123.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox task`() {
        writeJavaFile()

        createFile("src/main/resources/META-INF/other.xml").xml("""
            <idea-plugin />
        """)

        createFile("src/main/resources/META-INF/nonIncluded.xml").xml("""
            <idea-plugin />
        """)

        pluginXml.xml("""
            <idea-plugin>
              <depends config-file="other.xml" />
            </idea-plugin>    
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "plugins/myPluginName/lib/projectName-0.42.123.jar",
            ),
            collectPaths(sandbox),
        )

        val jar = sandbox.resolve("plugins/myPluginName/lib/projectName-0.42.123.jar")
        assertEquals(
            listOf(
                "App.class",
                "META-INF/",
                "META-INF/MANIFEST.MF",
                "META-INF/nonIncluded.xml",
                "META-INF/other.xml",
                "META-INF/plugin.xml",
            ),
            collectPathsFromZip(jar),
        )

        assertZipContent(jar, "META-INF/plugin.xml", """
            <idea-plugin>
              <version>0.42.123</version>
              <idea-version since-build="201.6668" until-build="201.*" />
              <depends config-file="other.xml" />
            </idea-plugin>
        """)
    }

    @Test
    fun `prepare ui tests sandbox task`() {
        writeJavaFile()

        createFile("src/main/resources/META-INF/other.xml").xml("""
            <idea-plugin />
        """)

        createFile("src/main/resources/META-INF/nonIncluded.xml").xml("""
            <idea-plugin />
        """)

        pluginXml.xml("""
            <idea-plugin>
                <depends config-file="other.xml" />
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            downloadRobotServerPlugin.version = '0.11.1'
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)

        assertTrue(
            collectPaths(sandbox).containsAll(listOf(
                "plugins-uiTest/myPluginName/lib/projectName-0.42.123.jar",
                "plugins-uiTest/myPluginName/lib/joda-time-2.8.1.jar",
                "config-uiTest/options/updates.xml",
                "plugins-uiTest/robot-server-plugin/lib/robot-server-plugin-0.11.1.jar",
            ))
        )
    }

    @Test
    fun `prepare sandbox with external jar-type plugin`() {
        writeJavaFile()

        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            intellij {
                plugins = ['org.jetbrains.postfixCompletion:0.8-beta']
                pluginName = 'myPluginName'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/myPluginName/lib/projectName.jar",
                "plugins/org.jetbrains.postfixCompletion-0.8-beta.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox with external zip-type plugin`() {
        writeJavaFile()

        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            intellij {
                plugins = ['org.intellij.plugins.markdown:201.6668.74']
                pluginName = 'myPluginName'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/markdown/lib/markdown-0.1.41.jar",
                "plugins/markdown/lib/markdown.jar",
                "plugins/markdown/lib/resources_en.jar",
                "plugins/myPluginName/lib/projectName.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepare sandbox with plugin dependency with classes directory`() {
        val plugin = createPlugin()

        writeJavaFile()

        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            intellij {
                plugins = ['$plugin']
                pluginName = 'myPluginName'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/myPluginName/lib/projectName.jar",
                "plugins/${plugin.fileName}/META-INF/plugin.xml",
                "plugins/${plugin.fileName}/classes/A.class",
                "plugins/${plugin.fileName}/classes/someResources.properties",
            ),
            collectPaths(sandbox),
        )
    }

    private fun createPlugin() = createTempDirectory("tmp").apply {
        resolve("classes/").createDir()
        resolve("META-INF/").createDir()
        resolve("classes/A.class").create()
        resolve("classes/someResources.properties").create()
        resolve("META-INF/plugin.xml").create().xml(
            """
        <idea-plugin>
          <id>$fileName</id>
          <name>Test</name>
          <version>1.0</version>
          <idea-version since-build="201.6668" until-build="201.*" />
          <vendor url="https://jetbrains.com">JetBrains</vendor>
          <description>Lorem ipsum dolor sit amet, consectetur adipisicing elit.</description>
          <change-notes/>
        </idea-plugin>
    """
        )
    }

    @Test
    fun `prepare custom sandbox task`() {
        writeJavaFile()

        createFile("src/main/resources/META-INF/other.xml").xml("""
            <idea-plugin />
        """)

        createFile("src/main/resources/META-INF/nonIncluded.xml").xml("""
            <idea-plugin />
        """)

        pluginXml.xml("""
            <idea-plugin>
                <depends config-file="other.xml" />
            </idea-plugin>
        """)

        val sandboxPath = dir.resolve("customSandbox")
        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
                sandboxDir = '$sandboxPath'
            }
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "plugins/myPluginName/lib/projectName-0.42.123.jar",
            ),
            collectPaths(sandboxPath),
        )
    }

    @Test
    fun `use gradle project name if plugin name is not defined`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/projectName/lib/projectName.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `disable ide update without updates_xml`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            buildDirectory.resolve("${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml"),
            """
                <application>
                  <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" value="false" />
                  </component>
                </application>
            """,
        )
    }

    @Test
    fun `disable ide update without updates component`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        createFile("build/${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml").xml("""
            <application>
                <component name="SomeOtherComponent">
                    <option name="SomeOption" value="false" />
                </component>
            </application>
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            buildDirectory.resolve("${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml"),
            """
                <application>
                  <component name="SomeOtherComponent">
                    <option name="SomeOption" value="false" />
                  </component>
                  <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" value="false" />
                  </component>
                </application>
            """,
        )
    }

    @Test
    fun `disable ide update without check_needed option`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        createFile("build/${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml").xml("""
            <application>
                <component name="UpdatesConfigurable">
                    <option name="SomeOption" value="false" />
                </component>
            </application>
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            buildDirectory.resolve("${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml"),
            """
                <application>
                  <component name="UpdatesConfigurable">
                    <option name="SomeOption" value="false" />
                    <option name="CHECK_NEEDED" value="false" />
                  </component>
                </application>
            """,
        )
    }

    @Test
    fun `disable ide update without value attribute`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        createFile("build/${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml").xml("""
            <application>
                <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" />
                </component>
            </application>
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            buildDirectory.resolve("${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml"),
            """
                <application>
                  <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" value="false" />
                  </component>
                </application>
            """,
        )
    }

    @Test
    fun `disable ide update`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        createFile("build/${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml").xml("""
            <application>
                <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" value="true" />
                </component>
            </application>
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            buildDirectory.resolve("${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml"),
            """
                <application>
                  <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" value="false" />
                  </component>
                </application>
            """,
        )
    }

    @Test
    fun `disable ide update with updates_xml empty`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        createFile("build/${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml")

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            buildDirectory.resolve("${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml"),
            """
                <application>
                  <component name="UpdatesConfigurable">
                    <option name="CHECK_NEEDED" value="false" />
                  </component>
                </application>
            """,
        )
    }

    @Test
    fun `disable ide update with complex updates_xml`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        createFile("build/${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml").xml("""
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
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertFileContent(
            buildDirectory.resolve("${IntelliJPluginConstants.DEFAULT_SANDBOX}/config/options/updates.xml"),
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
            """,
        )
    }

    @Test
    fun `replace jar on version changing`() {
        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            version = '0.42.123'
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        buildFile.groovy("""
            version = '0.42.124'
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/projectName/lib/projectName-0.42.124.jar",
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

        buildFile.groovy("""
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
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

        assertEquals(
            listOf(
                "config/options/updates.xml",
                "plugins/myPluginName/lib/core.jar",
                "plugins/myPluginName/lib/core_1.jar",
                "plugins/myPluginName/lib/core_2.jar",
                "plugins/myPluginName/lib/joda-time-2.8.1.jar",
                "plugins/myPluginName/lib/projectName-0.42.123.jar",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `prepareTestingSandbox runs before test`() {
        writeJavaFile()
        file("additional/some-file")

        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            intellij {
                pluginName = 'myPluginName'
            }
            
            ${IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME} {
                from("additional")
            }
        """)

        build("test")

        assertEquals(
            listOf(
                "config-test/options/updates.xml",
                "plugins-test/myPluginName/lib/projectName.jar",
                "plugins-test/some-file",
            ),
            collectPaths(sandbox),
        )
    }

    @Test
    fun `reuse configuration cache for prepareSandbox`() {
        writeJavaFile()

        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            intellij {
                plugins = ['org.jetbrains.postfixCompletion:0.8-beta']
                pluginName = 'myPluginName'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, "--configuration-cache", "--info")
        val result = build(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }

    @Test
    fun `reuse configuration cache for prepareTestingSandbox`() {
        writeJavaFile()

        pluginXml.xml("""
            <idea-plugin />
        """)

        buildFile.groovy("""
            intellij {
                plugins = ['org.jetbrains.postfixCompletion:0.8-beta']
                pluginName = 'myPluginName'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME, "--configuration-cache", "--info")
        val result = build(IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }

    @Test
    fun `reuse configuration cache for prepareUiTestingSandbox`() {
        writeJavaFile()

        createFile("src/main/resources/META-INF/other.xml").xml("""
            <idea-plugin />
        """)

        createFile("src/main/resources/META-INF/nonIncluded.xml").xml("""
            <idea-plugin />
        """)

        pluginXml.xml("""
            <idea-plugin>
                <depends config-file="other.xml" />
            </idea-plugin>
        """)

        buildFile.groovy("""
            version = '0.42.123'
            intellij {
                pluginName = 'myPluginName'
                plugins = ['copyright']
            }
            downloadRobotServerPlugin.version = '0.11.1'
            dependencies {
                implementation 'joda-time:joda-time:2.8.1'
            }
        """)

        build(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME, "--configuration-cache", "--info")
        val result = build(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME, "--configuration-cache")

        assertTrue(result.output.contains("Reusing configuration cache."))
    }
}
