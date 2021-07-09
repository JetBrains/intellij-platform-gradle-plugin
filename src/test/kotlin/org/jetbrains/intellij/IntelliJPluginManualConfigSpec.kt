package org.jetbrains.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IntelliJPluginManualConfigSpec : IntelliJPluginSpecBase() {

    @Test
    fun `configure sdk manually test`() {
        writeTestFile()

        buildFile.groovy("""
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                version = '14.1.4'
                configureDefaultDependencies = false
            }
            
            afterEvaluate {
                dependencies {
                    compileOnly        DependenciesUtils.intellij(project) { include('openapi.jar') }
                    implementation     DependenciesUtils.intellij(project) { include('asm-all.jar') }
                    runtimeOnly        DependenciesUtils.intellij(project) { exclude('idea.jar') }
                    testImplementation DependenciesUtils.intellij(project) { include('boot.jar') }
                    testRuntimeOnly    DependenciesUtils.intellij(project)
                } 
            }
            task printMainCompileClassPath { doLast { println 'implementation: ' + sourceSets.main.compileClasspath.asPath } }
            task printMainRuntimeClassPath { doLast { println 'runtimeOnly: ' + sourceSets.main.runtimeClasspath.asPath } }
            task printTestCompileClassPath { doLast { println 'testImplementation: ' + sourceSets.test.compileClasspath.asPath } }
            task printTestRuntimeClassPath { doLast { println 'testRuntimeOnly: ' + sourceSets.test.runtimeClasspath.asPath } }
        """)

        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("implementation:") } ?: ""
            val mainRuntimeClasspath = lines.find { it.startsWith("runtimeOnly:") } ?: ""
            val testClasspath = lines.find { it.startsWith("testImplementation:") } ?: ""
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntimeOnly:") } ?: ""

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
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                version = '14.1.4'
                configureDefaultDependencies = false
                plugins = ['junit', 'testng', 'copyright']
            }
            afterEvaluate {
                dependencies {
                    compileOnly        DependenciesUtils.intellijPlugin(project, 'junit') { include('junit-rt.jar') }
                    implementation     DependenciesUtils.intellijPlugin(project, 'junit')  { include('idea-junit.jar') }
                    runtimeOnly        DependenciesUtils.intellijPlugin(project, 'testng') { exclude('testng-plugin.jar') }
                    testImplementation DependenciesUtils.intellijPlugin(project, 'testng') { include("testng.jar") }
                    testRuntimeOnly    DependenciesUtils.intellijPlugins(project, 'junit', 'testng')
                } 
            }
            task printMainCompileClassPath { doLast { println 'implementation: ' + sourceSets.main.compileClasspath.asPath } }
            task printMainRuntimeClassPath { doLast { println 'runtimeOnly: ' + sourceSets.main.runtimeClasspath.asPath } }
            task printTestCompileClassPath { doLast { println 'testImplementation: ' + sourceSets.test.compileClasspath.asPath } }
            task printTestRuntimeClassPath { doLast { println 'testRuntimeOnly: ' + sourceSets.test.runtimeClasspath.asPath } }
        """)

        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("implementation:") } ?: ""
            val mainRuntimeClasspath = lines.find { it.startsWith("runtimeOnly:") } ?: ""
            val testClasspath = lines.find { it.startsWith("testImplementation:") } ?: ""
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntimeOnly:") } ?: ""

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
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['intellij-core', 'jps-build-test']
            }
            afterEvaluate {
                dependencies {
                    implementation     DependenciesUtils.intellijExtra(project, 'jps-build-test') { include('jps-build-test*.jar') }
                    runtimeOnly        DependenciesUtils.intellijExtra(project, 'intellij-core')  { exclude('intellij-core.jar') }
                    testImplementation DependenciesUtils.intellijExtra(project, 'intellij-core')  { include("annotations.jar") }
                    testRuntimeOnly    DependenciesUtils.intellijExtra(project, 'jps-build-test')
                    testRuntimeOnly    DependenciesUtils.intellijExtra(project, 'intellij-core')
                } 
            }
            task printMainCompileClassPath { doLast { println 'implementation: ' + sourceSets.main.compileClasspath.asPath } }
            task printMainRuntimeClassPath { doLast { println 'runtimeOnly: ' + sourceSets.main.runtimeClasspath.asPath } }
            task printTestCompileClassPath { doLast { println 'testImplementation: ' + sourceSets.test.compileClasspath.asPath } }
            task printTestRuntimeClassPath { doLast { println 'testRuntimeOnly: ' + sourceSets.test.runtimeClasspath.asPath } }
        """)


        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("implementation:") } ?: ""
            val mainRuntimeClasspath = lines.find { it.startsWith("runtimeOnly:") } ?: ""
            val testClasspath = lines.find { it.startsWith("testImplementation:") } ?: ""
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntimeOnly:") } ?: ""

            assertTrue(mainClasspath.contains("jps-build-test"))            // included explicitly in compileOnly (note - versioned jar, checking by name only)
            assertTrue(mainRuntimeClasspath.contains("jps-build-test"))
            assertTrue(testClasspath.contains("jps-build-test"))
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
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                configureDefaultDependencies = false
            }
            dependencies {
                compile DependenciesUtils.intellij(project) { include('asm-all.jar') }
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
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij.configureDefaultDependencies = false
            dependencies {
                compile DependenciesUtils.intellijPlugin(project, 'junit')
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
            import org.jetbrains.intellij.DependenciesUtils

            intellij {
                configureDefaultDependencies = false
                plugins = []
            }
            afterEvaluate {
                dependencies {
                    compile DependenciesUtils.intellijPlugin(project, 'junit')
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
            import org.jetbrains.intellij.DependenciesUtils

            intellij {
                configureDefaultDependencies = false
                plugins = ['junit']
            }
            afterEvaluate {
                dependencies {
                    compile DependenciesUtils.intellijPlugins(project, 'testng', 'junit', 'copyright')
                }
            } 
        """)

        val result = buildAndFail("tasks")
        assertTrue(
            result.output.contains("The following plugins: [testng, copyright] are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
        )
    }

    @Test
    fun `configure extra manually fail without afterEvaluate`() {
        writeTestFile()
        buildFile.groovy("""
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['intellij-core']
            }
            dependencies {
                compile DependenciesUtils.intellijExtra(project, 'intellij-core')
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
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['jps-build-test']
            }
            afterEvaluate {
                dependencies {
                    compile DependenciesUtils.intellijExtra(project, 'intellij-core')
                }
            }
        """)

        val result = buildAndFail("tasks")
        assertTrue(
            result.output.contains("intellij extra artifact 'intellij-core' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
        )
    }
}
