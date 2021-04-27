package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IntelliJPluginSpec : IntelliJPluginSpecBase() {

    @Test
    fun `intellij-specific tasks`() {
        assertEquals(
            listOf(
                IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME,
                IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME,
                IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME,
                IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME,
                IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME,
                IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME,
                IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME,
                IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME,
                IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME,
                IntelliJPluginConstants.RUN_IDE_TASK_NAME,
                IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME,
                IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME,
                IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME,
            ),
            tasks(IntelliJPluginConstants.GROUP_NAME),
        )
    }

    @Test
    fun `instrument code with nullability annotations`() {
        buildFile.groovy("""
            intellij {
                instrumentCode = true
            }
        """)

        writeJavaFile()

        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        val result = build("buildSourceSet", "--info")
        assertTrue(result.output.contains("Added @NotNull assertions to 1 files"))
    }

    @Test
    fun `instrument tests with nullability annotations`() {
        writeTestFile()

        buildFile.groovy("""
            intellij {
                instrumentCode = true
            }
        """)
        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        val result = build("buildTestSourceSet", "--info")
        assertTrue(result.output.contains("Added @NotNull assertions to 1 files"))
    }

    @Test
    fun `do not instrument code if option is set to false`() {
        buildFile.groovy("""
            intellij {
                instrumentCode = false
            }
        """)

        writeJavaFile()

        val result = build("buildSourceSet", "--info")
        assertFalse(result.output.contains("Added @NotNull"))
    }

    @Test
    fun `do not instrument code on empty source sets`() {
        val result = build("buildSourceSet", "--info")
        assertFalse(result.output.contains("Compiling forms and instrumenting code"))
    }

    @Test
    fun `instrument kotlin forms`() {
        writeKotlinUIFile()

        buildFile.groovy("""
            intellij {
                instrumentCode = true
            }
        """)

        file("src/main/kotlin/pack/AppKt.form").xml("""<?xml version="1.0" encoding="UTF-8"?>
            <form xmlns="http://www.intellij.com/uidesigner/form/" version="1" bind-to-class="pack.AppKt">
                <grid id="27dc6" binding="panel" layout-manager="GridLayoutManager" row-count="1" column-count="1" same-size-horizontally="false" same-size-vertically="false" hgap="-1" vgap="-1">
                    <margin top="0" left="0" bottom="0" right="0"/>
                    <constraints>
                        <xy x="20" y="20" width="500" height="400"/>
                    </constraints>
                    <properties/>
                    <border type="none"/>
                    <children/>
                </grid>
            </form>
        """)

        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        val result = build("buildSourceSet", "--info")
        result.output.contains("Compiling forms and instrumenting code")
    }

    @Test
    fun `instrumentation does not invalidate compile tasks`() {
        writeJavaFile()
        disableDebug("Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation")

        build("buildSourceSet")

        val result = build("buildSourceSet")
        assertEquals(TaskOutcome.UP_TO_DATE, result.task(":${JavaPlugin.CLASSES_TASK_NAME}")?.outcome)
    }

    @Test
    fun `patch test tasks`() {
        buildFile.groovy("""
            intellij {
                version = '14.1.4' 
            }
        """)

        writeTestFile()

        val result = build(JavaPlugin.TEST_TASK_NAME, "--info")
        val sandboxPath = adjustWindowsPath("${buildDirectory.canonicalPath}/idea-sandbox")
        val testCommand = parseCommand(result.output)

        assertPathParameters(testCommand, sandboxPath)
        assertFalse(testCommand.properties.containsKey("idea.required.plugins.id"))

        assertEquals("boot.jar", File(testCommand.xclasspath).name)
        assertEquals("lib", File(testCommand.xclasspath).parentFile.name)
        assertEquals("256m", testCommand.xms)
        assertEquals("512m", testCommand.xmx)
    }

    @Test
    fun `use compile only classpath for non-builtin plugins if Gradle lte 2_12`() {
        writeTestFile()

        buildFile.groovy("""
            intellij.plugins = ['copyright', 'org.jetbrains.postfixCompletion:0.8-beta']
            task printMainRuntimeClassPath { 
                doLast { println 'runtime: ' + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath { 
                doLast { println 'compile: ' + sourceSets.main.compileClasspath.asPath }
            }
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val runtimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""

            assertTrue(compileClasspath.contains("copyright.jar"))
            assertFalse(runtimeClasspath.contains("copyright.jar"))
            assertTrue(compileClasspath.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
            assertFalse(runtimeClasspath.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        }
    }

    @Test
    fun `add external zip-plugins to compile only classpath`() {
        writeTestFile()

        buildFile.groovy("""
            intellij.plugins = ['org.intellij.plugins.markdown:201.6668.74']

            task printMainRuntimeClassPath { 
                doLast { println 'runtime: ' + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath { 
                doLast { println 'compile: ' + sourceSets.main.compileClasspath.asPath }
            }
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val runtimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""

            assertTrue(compileClasspath.contains ("markdown.jar"))
            assertTrue(compileClasspath.contains ("resources_en.jar"))
            assertTrue(compileClasspath.contains ("kotlin-reflect-1.3.70.jar"))
            assertTrue(compileClasspath.contains ("kotlin-stdlib-1.3.70.jar"))
            assertTrue(compileClasspath.contains ("markdown-0.1.41.jar"))
            assertFalse(runtimeClasspath.contains("markdown.jar"))
            assertFalse(runtimeClasspath.contains("resources_en.jar"))
            assertFalse(runtimeClasspath.contains("kotlin-reflect-1.3.70.jar"))
            assertFalse(runtimeClasspath.contains("kotlin-stdlib-1.3.70.jar"))
            assertFalse(runtimeClasspath.contains("markdown-0.1.41.jar"))
        }
    }

    @Test
    fun `add local plugin to compile only classpath`() {
        val repositoryInstance = PluginRepositoryFactory.create("https://plugins.jetbrains.com", null)
        val plugin = repositoryInstance.downloader.download("org.jetbrains.postfixCompletion", "0.8-beta", dir, null)

        buildFile.groovy("""
            intellij.plugins = ["copyright", "${adjustWindowsPath(plugin?.canonicalPath ?: "")}"]
           
            task printMainRuntimeClassPath {
                doLast { println "runtime: " + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath {
                doLast { println "compile: " + sourceSets.main.compileClasspath.asPath }
            }
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val runtimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""

            assertTrue(compileClasspath.contains ("intellij-postfix.jar"))
            assertFalse(runtimeClasspath.contains("intellij-postfix.jar"))
        }
    }

    @Test
    fun `add builtin plugin dependencies to classpath`() {
        buildFile.groovy("""
            intellij.plugins = ["com.jetbrains.changeReminder"]
            task printTestRuntimeClassPath {
                doLast { println "runtime: " + sourceSets.test.runtimeClasspath.asPath }
            }
            task printTestCompileClassPath {
                doLast { println "compile: " + sourceSets.test.compileClasspath.asPath }
            }
        """)

        val result = build("printTestRuntimeClassPath", "printTestCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val runtimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""

            assertTrue(compileClasspath.contains ("vcs-changeReminder.jar"))
            assertTrue(runtimeClasspath.contains ("vcs-changeReminder.jar"))
            assertTrue(compileClasspath.contains ("git4idea.jar"))
            assertTrue(runtimeClasspath.contains ("git4idea.jar"))
        }
    }

    @Test
    fun `add ant dependencies to classpath`() {
        buildFile.groovy("""
            task printTestRuntimeClassPath {
                doLast {
                    println "runtime: " + sourceSets.test.runtimeClasspath.asPath
                }
            }
            
            task printTestCompileClassPath {
                doLast {
                    println "compile: " + sourceSets.test.compileClasspath.asPath
                }
            }
        """)

        val result = build("printTestRuntimeClassPath", "printTestCompileClassPath")
        val compileClasspath = result.output.lines().find { it.startsWith("compile:") } ?: ""
        val runtimeClasspath = result.output.lines().find { it.startsWith("runtime:") } ?: ""

        assertTrue(compileClasspath.contains("ant.jar"))
        assertTrue(runtimeClasspath.contains("ant.jar"))
    }

    @Test
    fun `use test compile classpath for non-builtin plugins if Gradle lte 2_12`() {
        writeTestFile()

        buildFile.groovy("""
            intellij.plugins = ['copyright', 'org.jetbrains.postfixCompletion:0.8-beta']
            
            task printTestRuntimeClassPath {
                doLast { println 'runtime: ' + sourceSets.test.runtimeClasspath.asPath }
            }
            task printTestCompileClassPath {
                doLast { println 'compile: ' + sourceSets.test.compileClasspath.asPath }
            }
        """)

        val result = build("printTestRuntimeClassPath", "printTestCompileClassPath")
        result.output.lines().let { lines ->
            val runtimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""
            val compileClasspath = lines.find { it.startsWith("compile:") } ?: ""

            assertTrue(compileClasspath.contains ("copyright.jar"))
            assertTrue(runtimeClasspath.contains ("copyright.jar"))
            assertTrue(compileClasspath.contains ("org.jetbrains.postfixCompletion-0.8-beta.jar"))
            assertTrue(runtimeClasspath.contains ("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        }
    }

    @Test
    fun `resolve plugins in Gradle lte 4_3`() {
        writeTestFile()

        buildFile.groovy("""
            intellij.plugins = ['org.jetbrains.postfixCompletion:0.8-beta', 'copyright']
            
            task printMainRuntimeClassPath {
                doLast { println 'runtime: ' + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath {
                doLast { println 'compile: ' + sourceSets.main.compileClasspath.asPath }
            }
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val runtimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""

            assertTrue(compileClasspath.contains ("copyright.jar"))
            assertFalse(runtimeClasspath.contains("copyright.jar"))
            assertTrue(compileClasspath.contains ("org.jetbrains.postfixCompletion-0.8-beta.jar"))
            assertFalse(runtimeClasspath.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        }
    }

    @Test
    fun `resolve bundled plugin by its id`() {
        writeTestFile()

        buildFile.groovy("""
            intellij.plugins = ['com.intellij.copyright']

            task printMainRuntimeClassPath {
                doLast { println 'runtime: ' + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath {
                doLast { println 'compile: ' + sourceSets.main.compileClasspath.asPath }
            }            
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val runtimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""

            assertTrue(compileClasspath.contains ("copyright.jar"))
            assertFalse(runtimeClasspath.contains("copyright.jar"))
        }
    }

    @Test
    fun `add require plugin id parameter in test tasks`() {
        writeTestFile()

        pluginXml.xml("""
            <idea-plugin>
                <name>Name</name>
                <id>com.intellij.mytestid</id>
                <vendor>JetBrains</vendor>
            </idea-plugin>
        """)

        val result = build(JavaPlugin.TEST_TASK_NAME, "--info")
        assertEquals(
            "com.intellij.mytestid",
            parseCommand(result.output).properties["idea.required.plugins.id"],
        )
    }

    @Test
    fun `do not update existing jvm arguments in test tasks`() {
        writeTestFile()

        buildFile.groovy("""
            test {
                minHeapSize = "200m"
                maxHeapSize = "500m"
            }
        """)

        val result = build(JavaPlugin.TEST_TASK_NAME, "--info")
        parseCommand(result.output).let {
            assertEquals("200m", it.xms)
            assertEquals("500m", it.xmx)
        }
    }

    @Test
    fun `custom sandbox directory`() {
        writeTestFile()

        val sandboxPath = adjustWindowsPath("${dir.canonicalPath}/customSandbox")
        buildFile.xml("""
            intellij {
                sandboxDir = '$sandboxPath'    
            }
        """)
        val result = build(JavaPlugin.TEST_TASK_NAME, "--info")
        assertPathParameters(parseCommand(result.output), sandboxPath)
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    fun assertPathParameters(testCommand: ProcessProperties, sandboxPath: String) {
        assertEquals("$sandboxPath/config-test", adjustWindowsPath(testCommand.properties["idea.config.path"] ?: ""))
        assertEquals("$sandboxPath/system-test", adjustWindowsPath(testCommand.properties["idea.system.path"] ?: ""))
        assertEquals("$sandboxPath/plugins-test", adjustWindowsPath(testCommand.properties["idea.plugins.path"] ?: ""))
    }

    private fun parseCommand(output: String) = output.lines()
        .find { it.startsWith("Starting process ") && !it.contains("vm_stat") }
        .let { assertNotNull(it) }
        .let { it.substringAfter("Command: ") }
        .let { ProcessProperties(it) }

    class ProcessProperties(command: String) {

        val properties = mutableMapOf<String, String>()
        val jvmArgs = mutableSetOf<String>()
        var xms = ""
        var xmx = ""
        var xclasspath = ""

        init {
            assertNotNull(command)
            command.trim().split("\\s+".toRegex()).forEach {
                when {
                    it.startsWith("-D") -> {
                        (it.substring(2).split('=') + "").let { (key, value) ->
                            properties[key] = value
                        }
                    }
                    it.startsWith("-Xms") -> xms = it.substring(4)
                    it.startsWith("-Xmx") -> xmx = it.substring(4)
                    it.startsWith("-Xbootclasspath") -> xclasspath = it.substring(15)
                    else -> jvmArgs.add(it)
                }
            }
        }
    }
}
