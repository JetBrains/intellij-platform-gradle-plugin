package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory

class IntelliJPluginSpec extends IntelliJPluginSpecBase {
    def 'intellij-specific tasks'() {
        when:
        buildFile << ""

        then:
        tasks(IntelliJPlugin.GROUP_NAME) == [IntelliJPlugin.BUILD_PLUGIN_TASK_NAME,
                                             IntelliJPlugin.BUILD_SEARCHABLE_OPTIONS_TASK_NAME,
                                             IntelliJPlugin.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME,
                                             IntelliJPlugin.JAR_SEARCHABLE_OPTIONS_TASK_NAME,
                                             IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME,
                                             IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME,
                                             IntelliJPlugin.PREPARE_TESTING_SANDBOX_TASK_NAME,
                                             IntelliJPlugin.PREPARE_UI_TESTING_SANDBOX_TASK_NAME,
                                             IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME,
                                             IntelliJPlugin.RUN_IDE_TASK_NAME,
                                             IntelliJPlugin.RUN_IDE_FOR_UI_TESTS_TASK_NAME,
                                             IntelliJPlugin.RUN_PLUGIN_VERIFIER_TASK_NAME,
                                             IntelliJPlugin.VERIFY_PLUGIN_TASK_NAME]
    }

    def 'instrument code with nullability annotations'() {
        given:
        buildFile << 'intellij { instrumentCode = true }'
        writeJavaFile()
        disableDebug('Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation')

        when:
        def result = build('buildSourceSet', '--info')

        then:
        result.output.contains('Added @NotNull assertions to 1 files')
    }

    def 'instrument tests with nullability annotations'() {
        given:
        writeTestFile()
        buildFile << 'intellij { instrumentCode = true }'
        disableDebug('Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation')

        when:
        def result = build('buildTestSourceSet', '--info')

        then:
        result.output.contains('Added @NotNull assertions to 1 files')
    }

    def 'do not instrument code if option is set to false'() {
        given:
        buildFile << 'intellij { instrumentCode = false }'
        writeJavaFile()

        when:
        def result = build('buildSourceSet', '--info')

        then:
        !result.output.contains('Added @NotNull')
    }

    def 'do not instrument code on empty source sets'() {
        when:
        def result = build('buildSourceSet', '--info')

        then:
        !result.output.contains('Compiling forms and instrumenting code')
    }

    def 'instrument kotlin forms'() {
        given:
        buildFile << 'intellij { instrumentCode = true }'
        file('src/main/kotlin/pack/AppKt.form') << """<?xml version="1.0" encoding="UTF-8"?>
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
"""
        writeKotlinUIFile()
        disableDebug('Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation')

        when:
        def result = build('buildSourceSet', '--info')

        then:
        result.output.contains('Compiling forms and instrumenting code')
    }

    def 'instrumentation does not invalidate compile tasks'() {
        given:
        buildFile << 'intellij { instrumentCode = true }'
        writeJavaFile()
        disableDebug('Gradle runs ant with another Java, that leads to NoSuchMethodError during the instrumentation')

        when:
        build('buildSourceSet')
        def result = build('buildSourceSet')

        then:
        result.task(":$JavaPlugin.CLASSES_TASK_NAME").outcome == TaskOutcome.UP_TO_DATE
    }

    def 'patch test tasks'() {
        given:
        buildFile << "intellij { version = '14.1.4' }"
        writeTestFile()

        when:
        def result = build(JavaPlugin.TEST_TASK_NAME, '--info')
        def sandboxPath = adjustWindowsPath("$buildDirectory.canonicalPath/idea-sandbox")

        then:
        def testCommand = parseCommand(result.output)
        assertPathParameters(testCommand, sandboxPath)
        !testCommand.properties.containsKey('idea.required.plugins.id')

        new File(testCommand.xclasspath).name == 'boot.jar'
        new File(testCommand.xclasspath).parentFile.name == 'lib'
        testCommand.xms == '256m'
        testCommand.xmx == '512m'
    }

    def 'use compile only classpath for non-builtin plugins if Gradle >= 2.12'() {
        given:
        writeTestFile()
        buildFile << 'intellij.plugins = [\'copyright\', \'org.jetbrains.postfixCompletion:0.8-beta\']\n'
        buildFile << 'task printMainRuntimeClassPath { doLast { println \'runtime: \' + sourceSets.main.runtimeClasspath.asPath } }\n'
        buildFile << 'task printMainCompileClassPath { doLast { println \'compile: \' + sourceSets.main.compileClasspath.asPath } }\n'

        when:
        def result = build('printMainRuntimeClassPath', 'printMainCompileClassPath')
        def compileClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def runtimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }

        then:
        assert compileClasspath.contains('copyright.jar')
        assert !runtimeClasspath.contains('copyright.jar')
        assert compileClasspath.contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
        assert !runtimeClasspath.contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
    }

    def 'add external zip-plugins to compile only classpath'() {
        given:
        writeTestFile()
        buildFile << 'intellij.plugins = [\'org.intellij.plugins.markdown:201.6668.74\']\n'
        buildFile << 'task printMainRuntimeClassPath { doLast { println \'runtime: \' + sourceSets.main.runtimeClasspath.asPath } }\n'
        buildFile << 'task printMainCompileClassPath { doLast { println \'compile: \' + sourceSets.main.compileClasspath.asPath } }\n'

        when:
        def result = build('printMainRuntimeClassPath', 'printMainCompileClassPath')
        def compileClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def runtimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }

        then:
        assert compileClasspath.contains('markdown.jar')
        assert compileClasspath.contains('resources_en.jar')
        assert compileClasspath.contains('kotlin-reflect-1.3.70.jar')
        assert compileClasspath.contains('kotlin-stdlib-1.3.70.jar')
        assert compileClasspath.contains('markdown-0.1.41.jar')
        assert !runtimeClasspath.contains('markdown.jar')
        assert !runtimeClasspath.contains('resources_en.jar')
        assert !runtimeClasspath.contains('kotlin-reflect-1.3.70.jar')
        assert !runtimeClasspath.contains('kotlin-stdlib-1.3.70.jar')
        assert !runtimeClasspath.contains('markdown-0.1.41.jar')
    }

    def 'add local plugin to compile only classpath'() {
        given:
        def repositoryInstance = PluginRepositoryFactory.create("https://plugins.jetbrains.com", null)
        def plugin = repositoryInstance.downloader.download('org.jetbrains.postfixCompletion', '0.8-beta', dir.root, null)

        buildFile << "intellij.plugins = ['copyright', '${adjustWindowsPath(plugin.canonicalPath)}']\n"
        buildFile << 'task printMainRuntimeClassPath { doLast { println \'runtime: \' + sourceSets.main.runtimeClasspath.asPath } }\n'
        buildFile << 'task printMainCompileClassPath { doLast { println \'compile: \' + sourceSets.main.compileClasspath.asPath } }\n'

        when:
        def result = build('printMainRuntimeClassPath', 'printMainCompileClassPath')
        def compileClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def runtimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }

        then:
        assert compileClasspath.contains('intellij-postfix.jar')
        assert !runtimeClasspath.contains('intellij-postfix.jar')
    }

    def 'add builtin plugin dependencies to classpath'() {
        given:
        buildFile << "intellij.plugins = ['com.jetbrains.changeReminder']\n"
        buildFile << 'task printTestRuntimeClassPath { doLast { println \'runtime: \' + sourceSets.test.runtimeClasspath.asPath } }\n'
        buildFile << 'task printTestCompileClassPath { doLast { println \'compile: \' + sourceSets.test.compileClasspath.asPath } }\n'

        when:
        def result = build('printTestRuntimeClassPath', 'printTestCompileClassPath')
        def compileClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def runtimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }

        then:
        assert compileClasspath.contains('vcs-changeReminder.jar')
        assert runtimeClasspath.contains('vcs-changeReminder.jar')
        assert compileClasspath.contains('git4idea.jar')
        assert runtimeClasspath.contains('git4idea.jar')
    }

    def 'add ant dependencies to classpath'() {
        given:
        buildFile << 'task printTestRuntimeClassPath { doLast { println \'runtime: \' + sourceSets.test.runtimeClasspath.asPath } }\n'
        buildFile << 'task printTestCompileClassPath { doLast { println \'compile: \' + sourceSets.test.compileClasspath.asPath } }\n'

        when:
        def result = build('printTestRuntimeClassPath', 'printTestCompileClassPath')
        def compileClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def runtimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }

        then:
        assert compileClasspath.contains('ant.jar')
        assert runtimeClasspath.contains('ant.jar')
    }

    def 'use test compile classpath for non-builtin plugins if Gradle >= 2.12'() {
        given:
        writeTestFile()
        buildFile << 'intellij.plugins = [\'copyright\', \'org.jetbrains.postfixCompletion:0.8-beta\']\n'
        buildFile << 'task printTestRuntimeClassPath { doLast { println \'runtime: \' + sourceSets.test.runtimeClasspath.asPath } }\n'
        buildFile << 'task printTestCompileClassPath { doLast { println \'compile: \' + sourceSets.test.compileClasspath.asPath } }\n'

        when:
        def result = build('printTestRuntimeClassPath', 'printTestCompileClassPath')
        def compileClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def runtimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }

        then:
        assert compileClasspath.contains('copyright.jar')
        assert runtimeClasspath.contains('copyright.jar')
        assert compileClasspath.contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
        assert runtimeClasspath.contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
    }

    def 'resolve plugins in Gradle >= 4.3'() {
        given:
        writeTestFile()
        buildFile << 'intellij.plugins = [\'org.jetbrains.postfixCompletion:0.8-beta\', \'copyright\']\n'
        buildFile << 'task printMainRuntimeClassPath { doLast { println \'runtime: \' + sourceSets.main.runtimeClasspath.asPath } }\n'
        buildFile << 'task printMainCompileClassPath { doLast { println \'compile: \' + sourceSets.main.compileClasspath.asPath } }\n'

        when:
        def result = build('printMainRuntimeClassPath', 'printMainCompileClassPath')
        def compileClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def runtimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }

        then:
        assert compileClasspath.contains('copyright.jar')
        assert !runtimeClasspath.contains('copyright.jar')
        assert compileClasspath.contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
        assert !runtimeClasspath.contains('org.jetbrains.postfixCompletion-0.8-beta.jar')
    }

    def 'resolve bundled plugin by its id'() {
        given:
        writeTestFile()
        buildFile << 'intellij.plugins = [\'com.intellij.copyright\']\n'
        buildFile << 'task printMainRuntimeClassPath { doLast { println \'runtime: \' + sourceSets.main.runtimeClasspath.asPath } }\n'
        buildFile << 'task printMainCompileClassPath { doLast { println \'compile: \' + sourceSets.main.compileClasspath.asPath } }\n'

        when:
        def result = build('printMainRuntimeClassPath', 'printMainCompileClassPath')
        def compileClasspath = result.output.readLines().find { it.startsWith('compile:') }
        def runtimeClasspath = result.output.readLines().find { it.startsWith('runtime:') }

        then:
        assert compileClasspath.contains('copyright.jar')
        assert !runtimeClasspath.contains('copyright.jar')
    }

    def 'add require plugin id parameter in test tasks'() {
        given:
        writeTestFile()
        pluginXml << """
<idea-plugin version="2">
  <name>Plugin name</name>
  <id>com.intellij.mytestid</id>
</idea-plugin>
"""
        when:
        def result = build(JavaPlugin.TEST_TASK_NAME, '--info')

        then:
        parseCommand(result.output).properties.'idea.required.plugins.id' == 'com.intellij.mytestid'
    }

    def 'do not update existing jvm arguments in test tasks'() {
        given:
        writeTestFile()
        buildFile << """
test {
    minHeapSize = "200m"
    maxHeapSize = "500m"
}
"""
        when:
        def result = build(JavaPlugin.TEST_TASK_NAME, '--info')

        then:
        def testCommand = parseCommand(result.output)
        testCommand.xms == '200m'
        testCommand.xmx == '500m'
    }

    def 'custom sandbox directory'() {
        given:
        writeTestFile()
        def sandboxPath = adjustWindowsPath("$dir.root.canonicalPath/customSandbox")
        buildFile << """
intellij {
    sandboxDirectory = '$sandboxPath'    
}
"""
        when:
        def result = build(JavaPlugin.TEST_TASK_NAME, '--info')

        then:
        assertPathParameters(parseCommand(result.output), sandboxPath)
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    private static void assertPathParameters(@NotNull ProcessProperties testCommand, @NotNull String sandboxPath) {
        assert adjustWindowsPath(testCommand.properties.'idea.config.path') == "$sandboxPath/config-test"
        assert adjustWindowsPath(testCommand.properties.'idea.system.path') == "$sandboxPath/system-test"
        assert adjustWindowsPath(testCommand.properties.'idea.plugins.path') == "$sandboxPath/plugins-test"
    }

    private static ProcessProperties parseCommand(@NotNull String output) {
        ProcessProperties testCommand = null
        for (String line : output.readLines()) {
            if (line.startsWith('Starting process ') && !line.contains('vm_stat')) {
                testCommand = ProcessProperties.parse(line.substring(line.indexOf('Command: ') + 'Command: '.length()))
                if (testCommand != null) {
                    break
                }
            }
        }
        assert testCommand != null
        testCommand
    }

    static class ProcessProperties {
        def properties = new HashMap<String, String>()
        String xclasspath = null
        String xmx = null
        String xms = null
        def jvmArgs = new HashSet<String>()

        static ProcessProperties parse(@NotNull String commandLine) {
            boolean first = true
            def result = new ProcessProperties()
            commandLine.split('\\s+').each {
                if (first) {
                    first = false
                    return
                }
                if (it.startsWith('-D')) {
                    def indexOfEquals = it.indexOf('=')
                    if (indexOfEquals == -1) indexOfEquals = it.length()
                    result.properties.put(it.substring(2, indexOfEquals), it.substring(Math.min(indexOfEquals + 1, it.length())))
                } else if (it.startsWith('-Xms')) {
                    result.xms = it.substring(4)
                } else if (it.startsWith('-Xmx')) {
                    result.xmx = it.substring(4)
                } else if (it.startsWith('-Xbootclasspath')) {
                    result.xclasspath = it.substring('-Xbootclasspath'.length())
                } else {
                    result.jvmArgs.add(it)
                }
            }
            result
        }
    }
}
