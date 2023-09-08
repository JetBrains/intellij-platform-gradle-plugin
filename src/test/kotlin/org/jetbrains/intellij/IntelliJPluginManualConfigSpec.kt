// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Suppress("GroovyAssignabilityCheck", "ComplexRedundantLet")
class IntelliJPluginManualConfigSpec : IntelliJPluginSpecBase() {

    @Test
    fun `configure sdk manually test`() {
        writeTestFile()

        buildFile.groovy(
            """
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
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
            
            def implementation = project.provider { sourceSets.main.compileClasspath.asPath }
            def runtimeOnly = project.provider { sourceSets.main.runtimeClasspath.asPath }
            def testImplementation = project.provider { sourceSets.test.compileClasspath.asPath }
            def testRuntimeOnly = project.provider { sourceSets.test.runtimeClasspath.asPath }
            
            task printMainCompileClassPath { doLast { println 'implementation: ' + implementation.get() } }
            task printMainRuntimeClassPath { doLast { println 'runtimeOnly: ' + runtimeOnly.get() } }
            task printTestCompileClassPath { doLast { println 'testImplementation: ' + testImplementation.get() } }
            task printTestRuntimeClassPath { doLast { println 'testRuntimeOnly: ' + testRuntimeOnly.get() } }
            """.trimIndent()
        )

        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()
            val mainRuntimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()
            val testClasspath = lines.find { it.startsWith("testImplementation:") }.orEmpty()
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntimeOnly:") }.orEmpty()

            with ("annotations-24.0.1.jar") {
                assertTrue(mainClasspath.contains(this))
                assertFalse(mainRuntimeClasspath.contains(this))
                assertTrue(testClasspath.contains(this))
                assertTrue(testRuntimeClasspath.contains(this))
            }

            with("app.jar") {
                assertFalse(mainClasspath.contains(this))
                assertTrue(mainRuntimeClasspath.contains(this))
                assertFalse(testClasspath.contains(this))
                assertTrue(testRuntimeClasspath.contains(this))
            }
        }
    }

    @Test
    fun `configure plugins manually test`() {
        writeTestFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
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
            
            def implementation = project.provider { sourceSets.main.compileClasspath.asPath }
            def runtimeOnly = project.provider { sourceSets.main.runtimeClasspath.asPath }
            def testImplementation = project.provider { sourceSets.test.compileClasspath.asPath }
            def testRuntimeOnly = project.provider { sourceSets.test.runtimeClasspath.asPath }
            
            task printMainCompileClassPath { doLast { println 'implementation: ' + implementation.get() } }
            task printMainRuntimeClassPath { doLast { println 'runtimeOnly: ' + runtimeOnly.get() } }
            task printTestCompileClassPath { doLast { println 'testImplementation: ' + testImplementation.get() } }
            task printTestRuntimeClassPath { doLast { println 'testRuntimeOnly: ' + testRuntimeOnly.get() } }
            """.trimIndent()
        )

        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()
            val mainRuntimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()
            val testClasspath = lines.find { it.startsWith("testImplementation:") }.orEmpty()
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntimeOnly:") }.orEmpty()

            with("junit-rt.jar") {
                assertTrue(mainClasspath.contains(this))
                assertFalse(mainRuntimeClasspath.contains(this))
                assertTrue(testClasspath.contains(this))
                assertTrue(testRuntimeClasspath.contains(this))
            }
            with("idea-junit.jar") {
                assertTrue(mainClasspath.contains(this))
                assertTrue(mainRuntimeClasspath.contains(this))
                assertTrue(testClasspath.contains(this))
                assertTrue(testRuntimeClasspath.contains(this))
            }
            with("testng-plugin.jar") {
                assertFalse(mainClasspath.contains(this))
                assertFalse(mainRuntimeClasspath.contains(this))
                assertFalse(testClasspath.contains(this))
                assertTrue(testRuntimeClasspath.contains(this))
            }
            with("testng.jar") {
                assertFalse(mainClasspath.contains(this))
                assertFalse(mainRuntimeClasspath.contains(this))
                assertFalse(testClasspath.contains(this))
                assertFalse(testRuntimeClasspath.contains(this))
            }
            with("copyright.jar") {
                assertFalse(mainClasspath.contains(this))
                assertFalse(mainRuntimeClasspath.contains(this))
                assertFalse(testClasspath.contains(this))
                assertFalse(testRuntimeClasspath.contains(this))
            }
        }
    }

    @Test
    fun `configure extra dependencies manually test`() {
        writeTestFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['jps-build-test']
            }
            afterEvaluate {
                dependencies {
                    implementation     DependenciesUtils.intellijExtra(project, 'jps-build-test') { include('jps-build-test*.jar') }
                    testRuntimeOnly    DependenciesUtils.intellijExtra(project, 'jps-build-test')
                } 
            }
            
            def implementation = project.provider { sourceSets.main.compileClasspath.asPath }
            def runtimeOnly = project.provider { sourceSets.main.runtimeClasspath.asPath }
            def testImplementation = project.provider { sourceSets.test.compileClasspath.asPath }
            def testRuntimeOnly = project.provider { sourceSets.test.runtimeClasspath.asPath }
            
            task printMainCompileClassPath { doLast { println 'implementation: ' + implementation.get() } }
            task printMainRuntimeClassPath { doLast { println 'runtimeOnly: ' + runtimeOnly.get() } }
            task printTestCompileClassPath { doLast { println 'testImplementation: ' + testImplementation.get() } }
            task printTestRuntimeClassPath { doLast { println 'testRuntimeOnly: ' + testRuntimeOnly.get() } }
            """.trimIndent()
        )

        build(
            "printMainCompileClassPath",
            "printTestCompileClassPath",
            "printTestRuntimeClassPath",
            "printMainRuntimeClassPath",
        ).output.lines().let { lines ->
            val mainClasspath = lines.find { it.startsWith("implementation:") }.orEmpty()
            val mainRuntimeClasspath = lines.find { it.startsWith("runtimeOnly:") }.orEmpty()
            val testClasspath = lines.find { it.startsWith("testImplementation:") }.orEmpty()
            val testRuntimeClasspath = lines.find { it.startsWith("testRuntimeOnly:") }.orEmpty()

            assertTrue(mainClasspath.contains("jps-build-test"))            // included explicitly in compileOnly (note - versioned jar, checking by name only)
            assertTrue(mainRuntimeClasspath.contains("jps-build-test"))
            assertTrue(testClasspath.contains("jps-build-test"))
            assertTrue(testRuntimeClasspath.contains("jps-build-test"))     // includes all
        }
    }

    @Test
    fun `configure sdk manually fail without afterEvaluate`() {
        writeTestFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                configureDefaultDependencies = false
            }
            dependencies {
                compile DependenciesUtils.intellij(project) { include('asm-all.jar') }
            } 
            """.trimIndent()
        )

        buildAndFail("tasks").let {
            assertContains(
                "intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block",
                it.output
            )
        }
    }

    @Test
    fun `configure plugins manually fail without afterEvaluate`() {
        writeTestFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij.configureDefaultDependencies = false
            dependencies {
                compile DependenciesUtils.intellijPlugin(project, 'junit')
            } 
            """.trimIndent()
        )

        buildAndFail("tasks").let {
            assertContains(
                "intellij plugin 'junit' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block",
                it.output
            )
        }
    }

    @Test
    fun `configure plugins manually fail on unconfigured plugin`() {
        writeTestFile()
        buildFile.groovy(
            """
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
            """.trimIndent()
        )

        buildAndFail("tasks").let {
            assertContains(
                "intellij plugin 'junit' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block",
                it.output
            )
        }
    }

    @Test
    fun `configure plugins manually fail on some unconfigured plugins`() {
        writeTestFile()
        buildFile.groovy(
            """
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
            """.trimIndent()
        )

        buildAndFail("tasks").let {
            assertContains(
                "The following plugins: [testng, copyright] are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block",
                it.output
            )
        }
    }

    @Test
    fun `configure extra manually fail without afterEvaluate`() {
        writeTestFile()
        buildFile.groovy(
            """
            import org.jetbrains.intellij.DependenciesUtils
            
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['intellij-core']
            }
            dependencies {
                compile DependenciesUtils.intellijExtra(project, 'intellij-core')
            }
            """.trimIndent()
        )

        buildAndFail("tasks").let {
            assertContains(
                "intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block",
                it.output
            )
        }
    }

    @Test
    fun `configure extra manually fail on unconfigured extra dependency`() {
        writeTestFile()
        buildFile.groovy(
            """
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
            """.trimIndent()
        )

        buildAndFail("tasks").let {
            assertContains(
                "intellij extra artifact 'intellij-core' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block",
                it.output
            )
        }
    }
}
