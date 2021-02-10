package org.jetbrains.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntelliJPluginManualConfigSpec : IntelliJPluginSpecBase() {

    @Test
    fun `configure sdk manually test`() {
        writeTestFile()

        buildFile.groovy("""
            intellij { version = '14.1.4' }
            intellij.configureDefaultDependencies = false
            afterEvaluate {
                dependencies {
                    compileOnly intellij { include('openapi.jar') }
                    compile     intellij { include('asm-all.jar') }
                    runtime     intellij { exclude('idea.jar') }
                    testCompile intellij { include('boot.jar') }
                    testRuntime intellij()
                } 
            }
            task printMainCompileClassPath { doLast { println 'compile: ' + sourceSets.main.compileClasspath.asPath } }
            task printMainRuntimeClassPath { doLast { println 'runtime: ' + sourceSets.main.runtimeClasspath.asPath } }
            task printTestCompileClassPath { doLast { println 'testCompile: ' + sourceSets.test.compileClasspath.asPath } }
            task printTestRuntimeClassPath { doLast { println 'testRuntime: ' + sourceSets.test.runtimeClasspath.asPath } }
        """)

        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val mainRuntimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""
            val testClasspath = lines.find { it.startsWith("testCompile:") } ?: ""
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntime:") } ?: ""

            assertTrue(mainClasspath.contains("openapi.jar"))           // included explicitly in compileOnly
            assertTrue(mainRuntimeClasspath.contains("openapi.jar"))    // includes all but idea.jar
            assertFalse(testClasspath.contains("openapi.jar"))
            assertTrue(testRuntimeClasspath.contains("openapi.jar"))    // includes all

            assertTrue(mainClasspath.contains("asm-all.jar"))           // included explicitly
            assertTrue(testClasspath.contains("asm-all.jar"))
            assertTrue(testRuntimeClasspath.contains("asm-all.jar"))    // includes all

            assertFalse(mainClasspath.contains("boot.jar"))
            assertTrue(testClasspath.contains("boot.jar"))              // included explicitly
            assertTrue(testRuntimeClasspath.contains("boot.jar"))       // includes all

            assertFalse(mainClasspath.contains("idea.jar"))
            assertFalse(mainRuntimeClasspath.contains("idea.jar"))      // excluded explicitly
            assertFalse(testClasspath.contains("idea.jar"))
            assertTrue(testRuntimeClasspath.contains("idea.jar"))       // includes all

            assertTrue(mainRuntimeClasspath.contains("idea_rt.jar"))    // includes all but idea.jar
        }
    }

    @Test
    fun `configure plugins manually test`() {
        writeTestFile()
        buildFile.groovy("""
            intellij {
                version = '14.1.4'
                configureDefaultDependencies = false
                plugins = ['junit', 'testng', 'copyright']
            }
            afterEvaluate {
                dependencies {
                    compileOnly intellijPlugin('junit')  { include('junit-rt.jar') }
                    compile     intellijPlugin('junit')  { include('idea-junit.jar') }
                    runtime     intellijPlugin('testng') { exclude('testng-plugin.jar') }
                    testCompile intellijPlugin('testng') { include("testng.jar") }
                    testRuntime intellijPlugins('junit', 'testng')
                } 
            }
            task printMainCompileClassPath { doLast { println 'compile: ' + sourceSets.main.compileClasspath.asPath } }
            task printMainRuntimeClassPath { doLast { println 'runtime: ' + sourceSets.main.runtimeClasspath.asPath } }
            task printTestCompileClassPath { doLast { println 'testCompile: ' + sourceSets.test.compileClasspath.asPath } }
            task printTestRuntimeClassPath { doLast { println 'testRuntime: ' + sourceSets.test.runtimeClasspath.asPath } }
        """)


        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val mainRuntimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""
            val testClasspath = lines.find { it.startsWith("testCompile:") } ?: ""
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntime:") } ?: ""

            assertTrue(mainClasspath.contains("junit-rt.jar"))              // included explicitly in compileOnly
            assertFalse(mainRuntimeClasspath.contains("junit-rt.jar"))
            assertFalse(testClasspath.contains("junit-rt.jar"))
            assertTrue(testRuntimeClasspath.contains("junit-rt.jar"))       // includes all

            assertTrue(mainClasspath.contains("idea-junit.jar"))            // included explicitly in compile
            assertTrue(testClasspath.contains("idea-junit.jar"))            // inherited from compile
            assertTrue(testRuntimeClasspath.contains("idea-junit.jar"))     // includes all

            assertFalse(mainClasspath.contains("testng-plugin.jar"))
            assertFalse(mainRuntimeClasspath.contains("testng-plugin.jar")) // excluded explicitly
            assertFalse(testClasspath.contains("testng-plugin.jar"))
            assertTrue(testRuntimeClasspath.contains("testng-plugin.jar"))  // includes all

            assertFalse(mainClasspath.contains("testng.jar"))
            assertTrue(mainRuntimeClasspath.contains("testng.jar"))         // includes testng
            assertTrue(testClasspath.contains("testng.jar"))                // included explicitly
            assertTrue(testRuntimeClasspath.contains("testng.jar"))         // includes all

            assertFalse(mainClasspath.contains("copyright.jar"))            // not included (same for all below)
            assertFalse(mainRuntimeClasspath.contains("copyright.jar"))
            assertFalse(testClasspath.contains("copyright.jar"))
            assertFalse(testRuntimeClasspath.contains("copyright.jar"))
        }
    }

    @Test
    fun `configure extra dependencies manually test`() {
        writeTestFile()
        buildFile.groovy("""
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['intellij-core', 'jps-build-test']
            }
            afterEvaluate {
                dependencies {
                    compileOnly intellijExtra('jps-build-test') { include('jps-build-test*.jar') }
                    runtime     intellijExtra('intellij-core')  { exclude('intellij-core.jar') }
                    testCompile intellijExtra('intellij-core')  { include("annotations.jar") }
                    testRuntime intellijExtra('jps-build-test')
                    testRuntime intellijExtra('intellij-core')
                } 
            }
            task printMainCompileClassPath { doLast { println 'compile: ' + sourceSets.main.compileClasspath.asPath } }
            task printMainRuntimeClassPath { doLast { println 'runtime: ' + sourceSets.main.runtimeClasspath.asPath } }
            task printTestCompileClassPath { doLast { println 'testCompile: ' + sourceSets.test.compileClasspath.asPath } }
            task printTestRuntimeClassPath { doLast { println 'testRuntime: ' + sourceSets.test.runtimeClasspath.asPath } }
        """)


        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("compile:") } ?: ""
            val mainRuntimeClasspath = lines.find { it.startsWith("runtime:") } ?: ""
            val testClasspath = lines.find { it.startsWith("testCompile:") } ?: ""
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntime:") } ?: ""

            assertTrue(mainClasspath.contains("jps-build-test"))            // included explicitly in compileOnly (note - versioned jar, checking by name only)
            assertFalse(mainRuntimeClasspath.contains("jps-build-test"))
            assertFalse(testClasspath.contains("jps-build-test"))
            assertTrue(testRuntimeClasspath.contains("jps-build-test"))     // includes all

            assertFalse(mainClasspath.contains("intellij-core.jar"))
            assertFalse(mainRuntimeClasspath.contains("intellij-core.jar")) // excluded explicitly
            assertFalse(testClasspath.contains("intellij-core.jar"))        // not included
            assertTrue(testRuntimeClasspath.contains("intellij-core.jar"))  // includes all

            assertFalse(mainClasspath.contains("annotations.jar"))
            assertTrue(testClasspath.contains("annotations.jar"))           // included explicitly
            assertTrue(testRuntimeClasspath.contains("annotations.jar"))    // includes all

            assertFalse(mainClasspath.contains("intellij-core-analysis-deprecated.jar"))
            assertTrue(mainRuntimeClasspath.contains("intellij-core-analysis-deprecated.jar")) // includes intellij-core
            assertFalse(testClasspath.contains("intellij-core-analysis-deprecated.jar"))
            assertTrue(testRuntimeClasspath.contains("intellij-core-analysis-deprecated.jar")) // includes all
        }
    }

    @Test
    fun `configure sdk manually fail without afterEvaluate`() {
        writeTestFile()
        buildFile.groovy("""
            intellij.configureDefaultDependencies = false
            dependencies {
                compile intellij { include('asm-all.jar') }
            } 
        """)

        val result = buildAndFail("tasks")
        assertTrue(
            result.output.contains("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
        )
    }

    @Test
    fun `configure plugins manually fail without afterEvaluate`() {
        writeTestFile()
        buildFile.groovy("""
            intellij.configureDefaultDependencies = false
            dependencies {
                compile intellijPlugin('junit')
            } 
        """)

        val result = buildAndFail("tasks")
        assertTrue(
            result.output.contains("intellij plugin 'junit' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
        )
    }

    @Test
    fun `configure plugins manually fail on unconfigured plugin`() {
        writeTestFile()
        buildFile.groovy("""
            intellij {
                configureDefaultDependencies = false
                plugins = []
            }
            afterEvaluate {
                dependencies {
                    compile intellijPlugin('junit')
                }
            } 
        """)

        val result = buildAndFail("tasks")
        assertTrue(
            result.output.contains("intellij plugin 'junit' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
        )
    }

    @Test
    fun `configure plugins manually fail on some unconfigured plugins`() {
        writeTestFile()
        buildFile.groovy("""
            intellij {
                configureDefaultDependencies = false
                plugins = ['junit']
            }
            afterEvaluate {
                dependencies {
                    compile intellijPlugins('testng', 'junit', 'copyright')
                }
            } 
        """)

        val result = buildAndFail("tasks")
        assertTrue(
            result.output.contains("intellij plugins [testng, copyright] are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
        )
    }

    @Test
    fun `configure extra manually fail without afterEvaluate`() {
        writeTestFile()
        buildFile.groovy("""
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['intellij-core']
            }
            dependencies {
                compile intellijExtra('intellij-core')
            }
        """)

        val result = buildAndFail("tasks")
        assertTrue(
            result.output.contains("intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block")
        )
    }

    @Test
    fun `configure extra manually fail on unconfigured extra dependency`() {
        writeTestFile()
        buildFile.groovy("""
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['jps-build-test']
            }
            afterEvaluate {
                dependencies {
                    compile intellijExtra('intellij-core')
                }
            }
        """)

        val result = buildAndFail("tasks")
        assertTrue(
            result.output.contains("intellij extra artifact 'intellij-core' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
        )
    }
}
