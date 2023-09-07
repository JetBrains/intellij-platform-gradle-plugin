// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin.TEST_TASK_NAME
import org.gradle.testkit.runner.BuildResult
import org.jetbrains.intellij.IntelliJPluginConstants.MARKETPLACE_HOST
import org.jetbrains.intellij.IntelliJPluginConstants.MINIMAL_SUPPORTED_GRADLE_VERSION
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.test.createLocalIdeIfNotExists
import org.junit.Assume.assumeFalse
import java.io.File
import java.nio.file.Path
import kotlin.test.*

@Suppress("GroovyAssignabilityCheck", "ComplexRedundantLet")
class IntelliJPluginSpec : IntelliJPluginSpecBase() {

    @Test
    fun `intellij-specific tasks`() {
        assumeFalse(Version.parse(gradleVersion) < Version.parse("6.9"))
        assertEquals(
            TASKS.joinToString("\n"),
            tasks(PLUGIN_GROUP_NAME).joinToString("\n"),
        )
    }

    @Test
    fun `patch test tasks`() {
        buildFile.groovy(
            """
            intellij {
                version = '14.1.4' 
            }
            """.trimIndent()
        )

        writeTestFile()

        build(TEST_TASK_NAME, "--info").let {
            val sandboxPath = adjustWindowsPath("${buildDirectory.canonicalPath}/idea-sandbox")
            val testCommand = parseCommand(it.output)

            assertPathParameters(testCommand, sandboxPath)
            assertEquals(testCommand.properties.get("idea.required.plugins.id"), "")

            assertEquals("boot.jar", File(testCommand.xclasspath).name)
            assertEquals("lib", File(testCommand.xclasspath).parentFile.name)
            assertEquals("256m", testCommand.xms)
            assertEquals("512m", testCommand.xmx)
        }
    }

    @Test
    fun `use compile only classpath for non-builtin plugins if Gradle lte 2_12`() {
        writeTestFile()

        buildFile.groovy(
            """
            intellij.plugins = ['copyright', 'org.jetbrains.postfixCompletion:0.8-beta']
            """.trimIndent()
        )

        val (compileClasspath, runtimeClasspath) = collectMainClassPaths()

        assertAddedToCompileClassPathOnly(compileClasspath, runtimeClasspath, "copyright.jar")
        assertAddedToCompileClassPathOnly(compileClasspath, runtimeClasspath, "org.jetbrains.postfixCompletion-0.8-beta.jar")
    }

    @Test
    fun `add external zip-plugins to compile only classpath`() {
        writeTestFile()

        buildFile.groovy(
            """
            intellij.plugins = ['org.intellij.plugins.markdown:$testMarkdownPluginVersion']
            """.trimIndent()
        )

        val (compileClasspath, runtimeClasspath) = collectMainClassPaths()

        assertTrue(compileClasspath.contains("markdown.jar"))
        assertTrue(compileClasspath.contains("kotlin-reflect-1.5.10-release-931.jar"))
        assertTrue(compileClasspath.contains("kotlin-stdlib-jdk8.jar"))
        assertFalse(runtimeClasspath.contains("markdown.jar"))
        assertFalse(runtimeClasspath.contains("kotlin-reflect-1.5.10-release-931.jar"))
        assertFalse(runtimeClasspath.contains("kotlin-stdlib-jdk8.jar"))
    }

    @Test
    fun `add local plugin to compile only classpath`() {
        val repositoryInstance = PluginRepositoryFactory.create(MARKETPLACE_HOST, null)
        val plugin = repositoryInstance.downloader.download("org.jetbrains.postfixCompletion", "0.8-beta", dir, null)

        buildFile.groovy(
            """
            intellij.plugins = ['copyright', "${adjustWindowsPath(plugin?.canonicalPath.orEmpty())}"]
            """.trimIndent()
        )

        val (compileClasspath, runtimeClasspath) = collectMainClassPaths()

        assertAddedToCompileClassPathOnly(compileClasspath, runtimeClasspath, "intellij-postfix.jar")
    }

    @Test
    fun `add builtin plugin dependencies to classpath`() {
        buildFile.groovy(
            """
            intellij.plugins = ['com.jetbrains.changeReminder']
            """.trimIndent()
        )

        val (compileClasspath, runtimeClasspath) = collectSourceSetClassPaths()

        assertAddedToCompileAndRuntimeClassPaths(compileClasspath, runtimeClasspath, "vcs-changeReminder.jar")
        assertAddedToCompileAndRuntimeClassPaths(compileClasspath, runtimeClasspath, "git4idea.jar")
    }

    @Test
    fun `add ant dependencies to classpath`() {
        val (compileClasspath, runtimeClasspath) = collectSourceSetClassPaths()

        assertAddedToCompileAndRuntimeClassPaths(compileClasspath, runtimeClasspath, "ant.jar")
    }

    @Test
    fun `use test compile classpath for non-builtin plugins if Gradle lte 2_12`() {
        writeTestFile()
        buildFile.groovy(
            """
            intellij.plugins = ['copyright', 'org.jetbrains.postfixCompletion:0.8-beta']
            """.trimIndent()
        )

        val (compileClasspath, runtimeClasspath) = collectSourceSetClassPaths()

        assertAddedToCompileAndRuntimeClassPaths(compileClasspath, runtimeClasspath, "copyright.jar")
        assertAddedToCompileAndRuntimeClassPaths(compileClasspath, runtimeClasspath, "org.jetbrains.postfixCompletion-0.8-beta.jar")
    }

    @Test
    fun `ide dependencies are added to test fixtures compile only classpath`() {
        writeTestFile()
        val originalBuildFile = buildFile.readText()
        buildFile.writeText("")
        buildFile.groovy(
            """
            plugins {
                id "java-test-fixtures"
            }
            """.trimIndent()
        )
        buildFile.groovy(originalBuildFile)
        buildFile.groovy(
            """
            intellij.plugins = ['org.jetbrains.postfixCompletion:0.8-beta', 'copyright']
            """.trimIndent()
        )

        val (compileClasspath, runtimeClasspath) = collectSourceSetClassPaths("testFixtures")

        assertAddedToCompileClassPathOnly(compileClasspath, runtimeClasspath, "copyright.jar")
        assertAddedToCompileClassPathOnly(compileClasspath, runtimeClasspath, "org.jetbrains.postfixCompletion-0.8-beta.jar")
    }

    @Test
    fun `resolve plugins in Gradle lte 4_3`() {
        writeTestFile()

        buildFile.groovy(
            """
            intellij.plugins = ['org.jetbrains.postfixCompletion:0.8-beta', 'copyright']
            """.trimIndent()
        )

        val (compileClasspath, runtimeClasspath) = collectMainClassPaths()

        assertAddedToCompileClassPathOnly(compileClasspath, runtimeClasspath, "copyright.jar")
        assertAddedToCompileClassPathOnly(compileClasspath, runtimeClasspath, "org.jetbrains.postfixCompletion-0.8-beta.jar")
    }

    @Test
    fun `resolve bundled plugin by its id`() {
        writeTestFile()

        buildFile.groovy(
            """
            intellij.plugins = ['com.intellij.copyright']
            """.trimIndent()
        )

        val (compileClasspath, runtimeClasspath) = collectMainClassPaths()

        assertAddedToCompileClassPathOnly(compileClasspath, runtimeClasspath, "copyright.jar")
    }

    @Test
    fun `add bundled zip plugin source artifacts from src directory when downloadSources = true`() {
        buildFile.groovy(
            """
            intellij {
                type = 'GO'
                version = '2021.2.4'
                plugins = ['org.jetbrains.plugins.go']
                downloadSources = true
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:go:goland-GO").let {
            assertContainsOnlySourceArtifacts(
                it,
                "lib/src/go-openapi-src-goland-GO-212.5457.54-withSources-unzipped.com.jetbrains.plugins.jar " +
                        "(unzipped.com.jetbrains.plugins:go:goland-GO-212.5457.54-withSources)",
                "ideaIC-goland-GO-212.5457.54-withSources-sources.jar " +
                        "(unzipped.com.jetbrains.plugins:go:goland-GO-212.5457.54-withSources)"
            )
        }
    }

    @Test
    fun `add bundled zip plugin source artifacts from src directory when downloadSources = false`() {
        buildFile.groovy(
            """
            intellij {
                type = 'GO'
                version = '2021.2.4'
                plugins = ['org.jetbrains.plugins.go']
                downloadSources = false
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:go:goland-GO").let {
            assertContainsOnlySourceArtifacts(
                it,
                "lib/src/go-openapi-src-goland-GO-212.5457.54-unzipped.com.jetbrains.plugins.jar " +
                        "(unzipped.com.jetbrains.plugins:go:goland-GO-212.5457.54)"
            )
        }
    }

    @Test
    fun `add external zip plugin source artifacts from src directory when downloadSources = true`() {
        buildFile.groovy(
            """
            intellij {
                type = 'IC'
                version = '2021.2.4'
                plugins = ['org.jetbrains.plugins.go:212.5712.14'] // Go plugin is external for IC
                downloadSources = true
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go").let {
            assertContainsOnlySourceArtifacts(
                it,
                "go/lib/src/go-openapi-src-212.5712.14-unzipped.com.jetbrains.plugins.jar " +
                        "(unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go:212.5712.14)"
            )
        }
    }

    @Test
    fun `add external zip plugin source artifacts from src directory when downloadSources = false`() {
        buildFile.groovy(
            """
            intellij {
                type = 'IC'
                version = '2021.2.4'
                plugins = ['org.jetbrains.plugins.go:212.5712.14'] // Go plugin is external for IC
                downloadSources = false
            }
            """.trimIndent()
        )
        printSourceArtifacts("unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go").let {
            assertContainsOnlySourceArtifacts(
                it,
                "go/lib/src/go-openapi-src-212.5712.14-unzipped.com.jetbrains.plugins.jar " +
                        "(unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go:212.5712.14)"
            )
        }
    }

    // FIXME: test takes too long
    @Test
    fun `add bundled zip plugin source artifacts from src directory when localPath used`() {
        val localPath = createLocalIdeIfNotExists(
            Path.of(gradleHome).parent.resolve("local-ides"),
            "com/jetbrains/intellij/goland/goland/2022.1/goland-2022.1.zip"
        )
        buildFile.writeText("")
        buildFile.groovy(
            """
            plugins {
                id 'java'
                id 'org.jetbrains.intellij'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinPluginVersion'
            }
            intellij {
                localPath = '${adjustWindowsPath(localPath)}'
                plugins = ['org.jetbrains.plugins.go']
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:go:ideaLocal-GO").let {
            assertContainsOnlySourceArtifacts(
                it,
                "lib/src/go-openapi-src-ideaLocal-GO-221.5080.224-unzipped.com.jetbrains.plugins.jar " +
                        "(unzipped.com.jetbrains.plugins:go:ideaLocal-GO-221.5080.224)"
            )
        }
    }

    // FIXME: test takes too long
    @Test
    fun `add external zip plugin source artifacts from src directory when localPath used`() {
        val localPath = createLocalIdeIfNotExists(
            Path.of(gradleHome).parent.resolve("local-ides"),
            "com/jetbrains/intellij/idea/ideaIC/2021.2.4/ideaIC-2021.2.4.zip"
        )

        buildFile.writeText("")
        buildFile.groovy(
            """
            plugins {
                id 'java'
                id 'org.jetbrains.intellij'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinPluginVersion'
            }
            intellij {
                localPath = '${adjustWindowsPath(localPath)}'
                plugins = ['org.jetbrains.plugins.go:212.5712.14']
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go").let {
            assertContainsOnlySourceArtifacts(
                it,
                "go/lib/src/go-openapi-src-212.5712.14-unzipped.com.jetbrains.plugins.jar " +
                        "(unzipped.com.jetbrains.plugins:org.jetbrains.plugins.go:212.5712.14)"
            )
        }
    }

    @Test
    fun `add bundled plugin source artifacts from IDE_ROOT-lib-src directory when downloadSources = true`() {
        buildFile.groovy(
            """
            intellij {
                type = 'IU'
                version = '2021.2.4'
                plugins = ['com.intellij.css']
                downloadSources = true
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:CSS:ideaIU-IU-212.5712.43-withSources").let {
            assertContainsOnlySourceArtifacts(
                it,
                "lib/src/src_css-api-ideaIU-IU-212.5712.43-withSources.zip (unzipped.com.jetbrains.plugins:CSS:ideaIU-IU-212.5712.43-withSources)",
                "ideaIC-ideaIU-IU-212.5712.43-withSources-sources.jar (unzipped.com.jetbrains.plugins:CSS:ideaIU-IU-212.5712.43-withSources)"
            )
        }
    }

    @Test
    fun `add bundled plugin source artifacts from IDE_ROOT-lib-src directory when downloadSources = false`() {
        buildFile.groovy(
            """
            intellij {
                type = 'IU'
                version = '2021.2.4'
                plugins = ['Tomcat']
                downloadSources = false
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:Tomcat:ideaIU-IU-212.5712.43").let {
            assertContainsOnlySourceArtifacts(
                it,
                "lib/src/src_tomcat-ideaIU-IU-212.5712.43.zip (unzipped.com.jetbrains.plugins:Tomcat:ideaIU-IU-212.5712.43)"
            )
        }
    }

    // FIXME: test takes too long
    @Test
    fun `add bundled zip plugin source artifacts from IDE_ROOT-lib-src directory when localPath used`() {
        val localPath = createLocalIdeIfNotExists(
            Path.of(gradleHome).parent.resolve("local-ides"),
            "com/jetbrains/intellij/idea/ideaIU/2021.2.4/ideaIU-2021.2.4.zip"
        )
        buildFile.writeText("")
        buildFile.groovy(
            """
            plugins {
                id 'java'
                id 'org.jetbrains.intellij'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinPluginVersion'
            }
            intellij {
                localPath = '${adjustWindowsPath(localPath)}'
                plugins = ['com.intellij.spring']
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:Spring:ideaLocal-IU-212.5712.43").let {
            assertContainsOnlySourceArtifacts(
                it,
                "lib/src/src_spring-openapi-ideaLocal-IU-212.5712.43.zip (unzipped.com.jetbrains.plugins:Spring:ideaLocal-IU-212.5712.43)"
            )
        }
    }

    @Test
    fun `does not add zip plugin source artifacts from IDE_ROOT-lib-src directory when sources not provided`() {
        buildFile.groovy(
            """
            intellij {
                type = 'GO'
                version = '2021.2.4'
                plugins = ['com.intellij.css']
                downloadSources = true
            }
            """.trimIndent()
        )

        printSourceArtifacts("unzipped.com.jetbrains.plugins:CSS:goland-GO-212.5457.54-withSources").let {
            assertContainsOnlySourceArtifacts(
                it,
                /* no CSS plugin source artifacts in Go distribution */
                "ideaIC-goland-GO-212.5457.54-withSources-sources.jar (unzipped.com.jetbrains.plugins:CSS:goland-GO-212.5457.54-withSources)"
            )
        }
    }

    @Test
    fun `add require plugin id parameter in test tasks`() {
        writeTestFile()

        pluginXml.xml(
            """
            <idea-plugin>
                <name>Name</name>
                <id>com.intellij.mytestid</id>
                <vendor>JetBrains</vendor>
            </idea-plugin>
            """.trimIndent()
        )

        build(TEST_TASK_NAME, "--info").let {
            assertEquals(
                "com.intellij.mytestid",
                parseCommand(it.output).properties["idea.required.plugins.id"],
            )
        }
    }

    @Test
    fun `do not update existing jvm arguments in test tasks`() {
        writeTestFile()

        buildFile.groovy(
            """
            test {
                minHeapSize = "200m"
                maxHeapSize = "500m"
            }
            """.trimIndent()
        )

        build(TEST_TASK_NAME, "--info").let {
            parseCommand(it.output).let { properties ->
                assertEquals("200m", properties.xms)
                assertEquals("500m", properties.xmx)
            }
        }
    }

    @Test
    fun `custom sandbox directory`() {
        writeTestFile()

        val sandboxPath = adjustWindowsPath("${dir.canonicalPath}/customSandbox")
        buildFile.xml(
            """
            intellij {
                sandboxDir = '$sandboxPath'    
            }
            """.trimIndent()
        )
        build(TEST_TASK_NAME, "--info").let {
            assertPathParameters(parseCommand(it.output), sandboxPath)
        }
    }

    @Test
    fun `expect build fails when using unsupported Gradle version`() {
        build(
            gradleVersion = "6.4",
            fail = true,
            assertValidConfigurationCache = true,
            "help",
        ).apply {
            assertContains("Gradle IntelliJ Plugin requires Gradle", output)
            assertContains("FAILURE: Build failed with an exception", output)
        }
    }

    @Test
    @Ignore(
        "Fails when building with 8.x and running on 7.x via unit tests: " +
                "java.lang.NoSuchMethodError: 'org.gradle.internal.buildoption.Option\$Value org.gradle.api.internal.StartParameterInternal.getIsolatedProjects()'"
    )
    fun `expect successful build using minimal supported Gradle version`() {
        val buildResult = build(
            gradleVersion = MINIMAL_SUPPORTED_GRADLE_VERSION,
            fail = false,
            assertValidConfigurationCache = true,
            "help",
        )

        assertContains("BUILD SUCCESSFUL", buildResult.output)
        assertNotContains("Gradle IntelliJ Plugin requires Gradle", buildResult.output)
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    fun assertPathParameters(testCommand: ProcessProperties, sandboxPath: String) {
        assertEquals("$sandboxPath/config-test", adjustWindowsPath(testCommand.properties["idea.config.path"].orEmpty()))
        assertEquals("$sandboxPath/system-test", adjustWindowsPath(testCommand.properties["idea.system.path"].orEmpty()))
        assertEquals("$sandboxPath/plugins-test", adjustWindowsPath(testCommand.properties["idea.plugins.path"].orEmpty()))
    }

    private fun collectMainClassPaths(): Pair<String, String> {
        buildFile.appendPrintMainClassPathsTasks()
        return buildAndGetClassPaths("printMainCompileClassPath", "printMainRuntimeClassPath")
    }

    private fun File.appendPrintMainClassPathsTasks() {
        this.groovy(
            """
            def runtimeOnly = project.provider { sourceSets.main.runtimeClasspath.asPath }
            def implementation = project.provider { sourceSets.main.compileClasspath.asPath }
            
            task printMainRuntimeClassPath {
                dependsOn('$SETUP_DEPENDENCIES_TASK_NAME')
                doLast { println 'runtimeOnly: ' + runtimeOnly.get() }
            }
            task printMainCompileClassPath {
                dependsOn('$SETUP_DEPENDENCIES_TASK_NAME')
                doLast { println 'implementation: ' + implementation.get() }
            }
            """.trimIndent()
        )
    }

    private fun collectSourceSetClassPaths(sourceSetName: String = "test"): Pair<String, String> {
        buildFile.appendPrintClassPathsTasks(sourceSetName)
        return buildAndGetClassPaths(
            "print${sourceSetName.replaceFirstChar { it.uppercase() }}CompileClassPath",
            "print${sourceSetName.replaceFirstChar { it.uppercase() }}RuntimeClassPath"
        )
    }

    private fun File.appendPrintClassPathsTasks(sourceSetName: String) {
        this.groovy(
            """
            def runtimeOnly = project.provider { sourceSets.$sourceSetName.runtimeClasspath.asPath }
            def implementation = project.provider { sourceSets.$sourceSetName.compileClasspath.asPath }
            
            task print${sourceSetName.replaceFirstChar { it.uppercase() }}RuntimeClassPath {
                dependsOn('$SETUP_DEPENDENCIES_TASK_NAME')
                doLast { println 'runtimeOnly: ' + runtimeOnly.get() }
            }
            task print${sourceSetName.replaceFirstChar { it.uppercase() }}CompileClassPath { 
                dependsOn('$SETUP_DEPENDENCIES_TASK_NAME')
                doLast { println 'implementation: ' + implementation.get() }
            }
            """.trimIndent()
        )
    }

    private fun buildAndGetClassPaths(vararg tasks: String) = build(*tasks).run {
        val compileClasspath = output.lines().find { it.startsWith("implementation:") }.orEmpty()
        val runtimeClasspath = output.lines().find { it.startsWith("runtimeOnly:") }.orEmpty()
        compileClasspath to runtimeClasspath
    }

    private fun assertAddedToCompileClassPathOnly(compileClasspath: String, runtimeClasspath: String, jarName: String) {
        assertTrue(
            compileClasspath.contains(jarName),
            "Expected $jarName to be included in the compile classpath: $compileClasspath"
        )
        assertFalse(
            runtimeClasspath.contains(jarName),
            "Expected $jarName to not be included in the runtime classpath: $runtimeClasspath"
        )
    }

    private fun assertAddedToCompileAndRuntimeClassPaths(
        compileClasspath: String,
        runtimeClasspath: String,
        jarName: String,
    ) {
        assertTrue(
            compileClasspath.contains(jarName),
            "Expected $jarName to be included in the compile classpath: $compileClasspath"
        )
        assertTrue(
            runtimeClasspath.contains(jarName),
            "Expected $jarName to be included in the runtime classpath: $runtimeClasspath"
        )
    }

    private fun printSourceArtifacts(pluginComponentId: String): BuildResult {
        buildFile.appendPrintPluginSourceArtifactsTask(pluginComponentId)
        return build("printPluginSourceArtifacts")
    }

    private fun File.appendPrintPluginSourceArtifactsTask(pluginComponentId: String) {
        this.groovy(
            """
                import org.gradle.api.artifacts.result.UnresolvedArtifactResult
                import org.jetbrains.intellij.IntelliJPluginConstants
                
                task printPluginSourceArtifacts {
                    dependsOn(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
                
                    def artifactsProvider = project.provider {
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
                            .findAll {
                                if (it instanceof UnresolvedArtifactResult) {
                                    println "WARNING:"
                                    it.failure.printStackTrace()
                                    return false
                                }
                                return true
                            }
                            .collect { it.id.displayName }
                    }
                
                    doLast {
                        artifactsProvider.get().each { println("source artifact:" + it) }
                    }
                }
                """.trimIndent()
        )
    }

    private fun assertContainsOnlySourceArtifacts(result: BuildResult, vararg expectedSourceArtifacts: String) {
        val sourceArtifactLinePrefix = "source artifact:"
        result.output.lines().let { lines ->
            val actualSourceArtifacts = lines
                .filter { it.startsWith(sourceArtifactLinePrefix) }
                .map { it.removePrefix(sourceArtifactLinePrefix) }
            assertEquals(
                expectedSourceArtifacts.asList(),
                actualSourceArtifacts,
                "Expected and actual source artifacts differ"
            )
        }
    }

    private fun parseCommand(output: String) = output.lines()
        .find { it.startsWith("Starting process ") && !it.contains("vm_stat") && !it.contains("JavaProbe") }
        .let { assertNotNull(it) }
        .substringAfter("Command: ")
        .also {
            println("Command: $it")
        }
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
