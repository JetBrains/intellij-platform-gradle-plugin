package org.jetbrains.intellij

class IntelliJPluginManualConfigSpec extends IntelliJPluginSpecBase {
    def 'configure sdk manually test'() {
        given:
        writeTestFile()
        buildFile << """
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
            """.stripIndent()

        when:
        def result = build('printMainCompileClassPath', 'printTestCompileClassPath', 'printTestRuntimeClassPath', 'printMainRuntimeClassPath')
        def mainClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def mainRuntimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }
        def testClasspath = result.output.readLines().find { it.startsWith('testCompile:') }
        def testRuntimeClasspath = result.output.readLines().find { it.startsWith('testRuntime:') }

        then:
        assert  mainClasspath.contains('openapi.jar')           // included explicitly in compileOnly 
        assert  mainRuntimeClasspath.contains('openapi.jar')    // includes all but idea.jar
        assert !testClasspath.contains('openapi.jar')
        assert  testRuntimeClasspath.contains('openapi.jar')    // includes all

        assert  mainClasspath.contains('asm-all.jar')           // included explicitly 
        assert  testClasspath.contains('asm-all.jar')
        assert  testRuntimeClasspath.contains('asm-all.jar')    // includes all

        assert !mainClasspath.contains('boot.jar')
        assert  testClasspath.contains('boot.jar')              // included explicitly
        assert  testRuntimeClasspath.contains('boot.jar')       // includes all

        assert !mainClasspath.contains('idea.jar')
        assert !mainRuntimeClasspath.contains('idea.jar')       // excluded explicitly
        assert !testClasspath.contains('idea.jar')
        assert  testRuntimeClasspath.contains('idea.jar')       // includes all

        assert mainRuntimeClasspath.contains('idea_rt.jar')     // includes all but idea.jar
    }

    def 'configure plugins manually test'() {
        given:
        writeTestFile()
        buildFile << """
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
            """.stripIndent()

        when:
        def result = build('printMainCompileClassPath', 'printTestCompileClassPath', 'printTestRuntimeClassPath', 'printMainRuntimeClassPath')
        def mainClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def mainRuntimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }
        def testClasspath = result.output.readLines().find { it.startsWith('testCompile:') }
        def testRuntimeClasspath = result.output.readLines().find { it.startsWith('testRuntime:') }

        then:
        assert  mainClasspath.contains('junit-rt.jar')          // included explicitly in compileOnly
        assert !mainRuntimeClasspath.contains('junit-rt.jar')
        assert !testClasspath.contains('junit-rt.jar')
        assert  testRuntimeClasspath.contains('junit-rt.jar')   // includes all

        assert  mainClasspath.contains('idea-junit.jar')        // included explicitly in compile
        assert  testClasspath.contains('idea-junit.jar')        // inherited from compile
        assert  testRuntimeClasspath.contains('idea-junit.jar') // includes all

        assert !mainClasspath.contains('testng-plugin.jar')
        assert !mainRuntimeClasspath.contains('testng-plugin.jar') // excluded explicitly
        assert !testClasspath.contains('testng-plugin.jar')
        assert  testRuntimeClasspath.contains('testng-plugin.jar') // includes all

        assert !mainClasspath.contains('testng.jar')
        assert  mainRuntimeClasspath.contains('testng.jar')     // includes testng
        assert  testClasspath.contains('testng.jar')            // included explicitly
        assert  testRuntimeClasspath.contains('testng.jar')     // includes all

        assert !mainClasspath.contains('copyright.jar')         // not included (same for all below)
        assert !mainRuntimeClasspath.contains('copyright.jar')
        assert !testClasspath.contains('copyright.jar')
        assert !testRuntimeClasspath.contains('copyright.jar')
    }

    def 'configure extra dependencies manually test'() {
        given:
        writeTestFile()
        buildFile << """
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
            """.stripIndent()

        when:
        def result = build('printMainCompileClassPath', 'printTestCompileClassPath', 'printTestRuntimeClassPath', 'printMainRuntimeClassPath')
        def mainClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def mainRuntimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }
        def testClasspath = result.output.readLines().find { it.startsWith('testCompile:') }
        def testRuntimeClasspath = result.output.readLines().find { it.startsWith('testRuntime:') }

        then:
        assert  mainClasspath.contains('jps-build-test')            // included explicitly in compileOnly (note - versioned jar, checking by name only)
        assert !mainRuntimeClasspath.contains('jps-build-test')
        assert !testClasspath.contains('jps-build-test')
        assert  testRuntimeClasspath.contains('jps-build-test')     // includes all

        assert !mainClasspath.contains('intellij-core.jar')
        assert !mainRuntimeClasspath.contains('intellij-core.jar')  // excluded explicitly
        assert !testClasspath.contains('intellij-core.jar')         // not included
        assert  testRuntimeClasspath.contains('intellij-core.jar')  // includes all

        assert !mainClasspath.contains('annotations.jar')
        assert  testClasspath.contains('annotations.jar')           // included explicitly
        assert  testRuntimeClasspath.contains('annotations.jar')    // includes all

        assert !mainClasspath.contains('intellij-core-analysis-deprecated.jar')
        assert  mainRuntimeClasspath.contains('intellij-core-analysis-deprecated.jar') // includes intellij-core
        assert !testClasspath.contains('intellij-core-analysis-deprecated.jar')
        assert  testRuntimeClasspath.contains('intellij-core-analysis-deprecated.jar') // includes all
    }

    def 'configure sdk manually fail without afterEvaluate'() {
        given:
        writeTestFile()
        buildFile << """
            intellij.configureDefaultDependencies = false
            dependencies {
                compile intellij { include('asm-all.jar') }
            } 
            """.stripIndent()

        when:
        def result = buildAndFail('tasks')

        then:
        result.output.contains('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
    }

    def 'configure plugins manually fail without afterEvaluate'() {
        given:
        writeTestFile()
        buildFile << """
            intellij.configureDefaultDependencies = false
            dependencies {
                compile intellijPlugin('junit')
            } 
            """.stripIndent()

        when:
        def result = buildAndFail('tasks')

        then:
        result.output.contains('intellij plugin \'junit\' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block')
    }

    def 'configure plugins manually fail on unconfigured plugin'() {
        given:
        writeTestFile()
        buildFile << """
            intellij {
                configureDefaultDependencies = false
                plugins = []
            }
            afterEvaluate {
                dependencies {
                    compile intellijPlugin('junit')
                }
            } 
            """.stripIndent()

        when:
        def result = buildAndFail('tasks')

        then:
        result.output.contains('intellij plugin \'junit\' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block')
    }

    def 'configure plugins manually fail on some unconfigured plugins'() {
        given:
        writeTestFile()
        buildFile << """
            intellij {
                configureDefaultDependencies = false
                plugins = ['junit']
            }
            afterEvaluate {
                dependencies {
                    compile intellijPlugins('testng', 'junit', 'copyright')
                }
            } 
            """.stripIndent()

        when:
        def result = buildAndFail('tasks')

        then:
        result.output.contains('intellij plugins [testng, copyright] are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block')
    }

    def 'configure extra manually fail without afterEvaluate'() {
        given:
        writeTestFile()
        buildFile << """
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['intellij-core']
            }
            dependencies {
                compile intellijExtra('intellij-core')
            }
            """.stripIndent()

        when:
        def result = buildAndFail('tasks')

        then:
        result.output.contains('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
    }

    def 'configure extra manually fail on unconfigured extra dependency'() {
        given:
        writeTestFile()
        buildFile << """
            intellij {
                configureDefaultDependencies = false
                extraDependencies = ['jps-build-test']
            }
            afterEvaluate {
                dependencies {
                    compile intellijExtra('intellij-core')
                }
            }
            """.stripIndent()

        when:
        def result = buildAndFail('tasks')

        then:
        result.output.contains('intellij extra artifact \'intellij-core\' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block')
    }
}
