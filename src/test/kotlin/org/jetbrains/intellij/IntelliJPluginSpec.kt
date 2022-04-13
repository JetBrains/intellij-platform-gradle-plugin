// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.BuildResult
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.junit.Assume.assumeFalse
import java.io.File
import kotlin.test.*

@Suppress("GroovyAssignabilityCheck")
class IntelliJPluginSpec : IntelliJPluginSpecBase() {

    @Test
    fun `intellij-specific tasks`() {
        assumeFalse(Version.parse(gradleVersion) < Version.parse("6.9"))
        assertEquals(
            listOf(
                IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME,
                IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME,
                IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME,
                IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME,
                IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME,
                IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME,
                IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME,
                IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME,
                IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME,
                IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME,
                IntelliJPluginConstants.RUN_IDE_TASK_NAME,
                IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME,
                IntelliJPluginConstants.RUN_IDE_PERFORMANCE_TEST_TASK_NAME,
                IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME,
                IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME,
                IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME,
                IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME
            ),
            tasks(IntelliJPluginConstants.GROUP_NAME),
        )
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
                doLast { println 'runtimeOnly: ' + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath { 
                doLast { println 'implementation: ' + sourceSets.main.compileClasspath.asPath }
            }
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val runtimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()
            val compileClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()

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
            intellij.plugins = ['org.intellij.plugins.markdown:$testMarkdownPluginVersion']

            task printMainRuntimeClassPath { 
                doLast { println 'runtimeOnly: ' + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath { 
                doLast { println 'implementation: ' + sourceSets.main.compileClasspath.asPath }
            }
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()
            val runtimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()

            assertTrue(compileClasspath.contains("markdown.jar"))
            assertTrue(compileClasspath.contains("kotlin-reflect-1.5.10-release-931.jar"))
            assertTrue(compileClasspath.contains("kotlin-stdlib-jdk8.jar"))
            assertFalse(runtimeClasspath.contains("markdown.jar"))
            assertFalse(runtimeClasspath.contains("kotlin-reflect-1.5.10-release-931.jar"))
            assertFalse(runtimeClasspath.contains("kotlin-stdlib-jdk8.jar"))
        }
    }

    @Test
    fun `add bundled zip plugin source artifacts from src directory when downloadSources = true`() {
        // The bundled Go plugin contains lib/src/go-openapi-sources.jar
        buildFile.groovy("""
            intellij {
              type = 'GO'
              version = '2021.2.4'
              plugins = ['org.jetbrains.plugins.go']
              downloadSources = true
            }
        """)
        buildFile.appendPluginSourceArtifactsTask("unzipped.com.jetbrains.plugins:go:goland-GO")

        val result = build("printPluginSourceArtifacts")
        assertContainsOnlySourceArtifacts(result,
            "lib/src/go-openapi-src-goland-GO-212.5457.54-withSources-sources.jar " +
                    "(unzipped.com.jetbrains.plugins:go:goland-GO-212.5457.54-withSources)",
            "ideaIC-goland-GO-212.5457.54-withSources-sources.jar " +
                    "(unzipped.com.jetbrains.plugins:go:goland-GO-212.5457.54-withSources)"
        )
    }

    @Test
    fun `add bundled zip plugin source artifacts from src directory when downloadSources = false`() {
        buildFile.groovy("""
            intellij {
              type = 'GO'
              version = '2021.2.4'
              plugins = ['org.jetbrains.plugins.go']
              downloadSources = false
            }
        """)
        buildFile.appendPluginSourceArtifactsTask("unzipped.com.jetbrains.plugins:go:goland-GO")

        val result = build("printPluginSourceArtifacts")
        assertContainsOnlySourceArtifacts(result,
            "lib/src/go-openapi-src-goland-GO-212.5457.54-sources.jar " +
                    "(unzipped.com.jetbrains.plugins:go:goland-GO-212.5457.54)"
        )
    }

    @Test
    fun `add external zip plugin source artifacts from src directory when downloadSources = true`() {
        buildFile.groovy("""
            intellij {
              type = 'IC'
              version = '2021.2.4'
              plugins = ['org.jetbrains.plugins.go:212.5712.14'] // Go plugin is external for IC
              downloadSources = true
            }
        """)
        buildFile.appendPluginSourceArtifactsTask("unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go")

        val result = build("printPluginSourceArtifacts")
        assertContainsOnlySourceArtifacts(result,
            "go/lib/src/go-openapi-src-212.5712.14-sources.jar " +
                    "(unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go:212.5712.14)"
        )
    }

    @Test
    fun `add external zip plugin source artifacts from src directory when downloadSources = false`() {
        buildFile.groovy("""
            intellij {
              type = 'IC'
              version = '2021.2.4'
              plugins = ['org.jetbrains.plugins.go:212.5712.14'] // Go plugin is external for IC
              downloadSources = false
            }
        """)
        buildFile.appendPluginSourceArtifactsTask("unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go")

        val result = build("printPluginSourceArtifacts")
        assertContainsOnlySourceArtifacts(result,
            "go/lib/src/go-openapi-src-212.5712.14-sources.jar " +
                    "(unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go:212.5712.14)"
        )
    }

    private fun File.appendPluginSourceArtifactsTask(pluginComponentId: String) {
        this.groovy(
            """
                task printPluginSourceArtifacts {
                  doLast {
                    def pluginComponentId = configurations.compileClasspath
                      .resolvedConfiguration.lenientConfiguration
                      .allModuleDependencies
                      .collect { it.moduleArtifacts }
                      .flatten()
                      .collect { it.id.componentIdentifier }
                      .find { it.displayName.startsWith("$pluginComponentId") }
        
                    dependencies.createArtifactResolutionQuery()
                      .forComponents([pluginComponentId])
                      .withArtifacts(JvmLibrary.class, SourcesArtifact.class)
                      .execute()
                      .resolvedComponents
                      .collect { it.getArtifacts(SourcesArtifact.class) }
                      .flatten()
                      .each { println("source artifact:" + it.id.displayName) }
                  }
                }
            """
        )
    }

    private fun assertContainsOnlySourceArtifacts(result: BuildResult, vararg expectedSourceArtifacts: String) {
        result.output.lines().let { lines ->
            val actualSourceArtifacts = lines
                .filter { it.startsWith("source artifact:") }
                .map { it.removePrefix("source artifact:") }
            val sortedActualSourceArtifacts = actualSourceArtifacts.sorted()
            val sortedExpectedSourceArtifacts = expectedSourceArtifacts.asList().sorted()
            if (sortedActualSourceArtifacts != sortedExpectedSourceArtifacts) {
                fail(
                    "Expected and actual source artifacts differ:\n" +
                            "Expected: $sortedExpectedSourceArtifacts\n" +
                            "Actual:   $sortedActualSourceArtifacts"
                )
            }
        }
    }

    @Test
    fun `add local plugin to compile only classpath`() {
        val repositoryInstance = PluginRepositoryFactory.create(IntelliJPluginConstants.MARKETPLACE_HOST, null)
        val plugin = repositoryInstance.downloader.download("org.jetbrains.postfixCompletion", "0.8-beta", dir, null)

        buildFile.groovy("""
            intellij.plugins = ["copyright", "${adjustWindowsPath(plugin?.canonicalPath.orEmpty())}"]
           
            task printMainRuntimeClassPath {
                doLast { println "runtimeOnly: " + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath {
                doLast { println "implementation: " + sourceSets.main.compileClasspath.asPath }
            }
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()
            val runtimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()

            assertTrue(compileClasspath.contains("intellij-postfix.jar"))
            assertFalse(runtimeClasspath.contains("intellij-postfix.jar"))
        }
    }

    @Test
    fun `add builtin plugin dependencies to classpath`() {
        buildFile.groovy("""
            intellij.plugins = ["com.jetbrains.changeReminder"]
            task printTestRuntimeClassPath {
                doLast { println "runtimeOnly: " + sourceSets.test.runtimeClasspath.asPath }
            }
            task printTestCompileClassPath {
                doLast { println "implementation: " + sourceSets.test.compileClasspath.asPath }
            }
        """)

        val result = build("printTestRuntimeClassPath", "printTestCompileClassPath")
        result.output.lines().let { lines ->
            val compileClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()
            val runtimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()

            assertTrue(compileClasspath.contains("vcs-changeReminder.jar"))
            assertTrue(runtimeClasspath.contains("vcs-changeReminder.jar"))
            assertTrue(compileClasspath.contains("git4idea.jar"))
            assertTrue(runtimeClasspath.contains("git4idea.jar"))
        }
    }

    @Test
    fun `add ant dependencies to classpath`() {
        buildFile.groovy("""
            task printTestRuntimeClassPath {
                doLast { println "runtimeOnly: " + sourceSets.test.runtimeClasspath.asPath }
            }
            
            task printTestCompileClassPath {
                doLast { println "implementation: " + sourceSets.test.compileClasspath.asPath }
            }
        """)

        val result = build("printTestRuntimeClassPath", "printTestCompileClassPath")
        val compileClasspath = result.output.lines().find { it.startsWith("implementation:") }.orEmpty()
        val runtimeClasspath = result.output.lines().find { it.startsWith("runtimeOnly:") }.orEmpty()

        assertTrue(compileClasspath.contains("ant.jar"))
        assertTrue(runtimeClasspath.contains("ant.jar"))
    }

    @Test
    fun `use test compile classpath for non-builtin plugins if Gradle lte 2_12`() {
        writeTestFile()

        buildFile.groovy("""
            intellij.plugins = ['copyright', 'org.jetbrains.postfixCompletion:0.8-beta']
            
            task printTestRuntimeClassPath {
                doLast { println 'runtimeOnly: ' + sourceSets.test.runtimeClasspath.asPath }
            }
            task printTestCompileClassPath {
                doLast { println 'implementation: ' + sourceSets.test.compileClasspath.asPath }
            }
        """)

        val result = build("printTestRuntimeClassPath", "printTestCompileClassPath")
        result.output.lines().let { lines ->
            val runtimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()
            val compileClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()

            assertTrue(compileClasspath.contains("copyright.jar"))
            assertTrue(runtimeClasspath.contains("copyright.jar"))
            assertTrue(compileClasspath.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
            assertTrue(runtimeClasspath.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        }
    }

    @Test
    fun `resolve plugins in Gradle lte 4_3`() {
        writeTestFile()

        buildFile.groovy("""
            intellij.plugins = ['org.jetbrains.postfixCompletion:0.8-beta', 'copyright']
            
            task printMainRuntimeClassPath {
                doLast { println 'runtimeOnly: ' + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath {
                doLast { println 'implementation: ' + sourceSets.main.compileClasspath.asPath }
            }
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val runtimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()
            val compileClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()

            assertTrue(compileClasspath.contains("copyright.jar"))
            assertFalse(runtimeClasspath.contains("copyright.jar"))
            assertTrue(compileClasspath.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
            assertFalse(runtimeClasspath.contains("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        }
    }

    @Test
    fun `resolve bundled plugin by its id`() {
        writeTestFile()

        buildFile.groovy("""
            intellij.plugins = ['com.intellij.copyright']

            task printMainRuntimeClassPath {
                doLast { println 'runtimeOnly: ' + sourceSets.main.runtimeClasspath.asPath }
            }
            task printMainCompileClassPath {
                doLast { println 'implementation: ' + sourceSets.main.compileClasspath.asPath }
            }            
        """)

        val result = build("printMainRuntimeClassPath", "printMainCompileClassPath")
        result.output.lines().let { lines ->
            val runtimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()
            val compileClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()

            assertTrue(compileClasspath.contains("copyright.jar"))
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

    @Test
    fun `throws exception if Gradle is lt 6_7`() {
        val message = "Gradle IntelliJ Plugin requires Gradle 6.7 and higher"
        build("6.6", true, "help").output.let {
            assertTrue(it.contains("FAILURE: Build failed with an exception."))
            assertTrue(it.contains(message))
        }

        build("6.7", false, "help").output.let {
            assertTrue(it.contains("BUILD SUCCESSFUL"))
            assertFalse(it.contains(message))
        }
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    fun assertPathParameters(testCommand: ProcessProperties, sandboxPath: String) {
        assertEquals("$sandboxPath/config-test", adjustWindowsPath(testCommand.properties["idea.config.path"].orEmpty()))
        assertEquals("$sandboxPath/system-test", adjustWindowsPath(testCommand.properties["idea.system.path"].orEmpty()))
        assertEquals("$sandboxPath/plugins-test", adjustWindowsPath(testCommand.properties["idea.plugins.path"].orEmpty()))
    }

    private fun parseCommand(output: String) = output.lines()
        .find { it.startsWith("Starting process ") && !it.contains("vm_stat") }
        .let { assertNotNull(it) }
        .substringAfter("Command: ")
        .let(::ProcessProperties)

    class ProcessProperties(command: String) {

        private val jvmArgs = mutableSetOf<String>()

        val properties = mutableMapOf<String, String>()
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
