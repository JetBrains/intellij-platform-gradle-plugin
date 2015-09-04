package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import org.jetbrains.annotations.NotNull

class IntelliJPluginSpec extends IntelliJPluginSpecBase {
    def 'intellij-specific tasks'() {
        when:
        buildFile << ""
        pluginXml << ""

        then:
        tasks(IntelliJPlugin.GROUP_NAME) == ['buildPlugin', 'patchPluginXml', 'prepareSandbox', 'runIdea']
    }

    def 'do not add intellij-specific tasks for project without plugin.xml'() {
        when:
        buildFile << ""

        then:
        tasks(IntelliJPlugin.GROUP_NAME) == null
    }

    def 'instrument code with nullability annotations'() {
        given:
        buildFile << 'intellij { instrumentCode = true }'
        writeJavaFile()

        when:
        run(true, JavaPlugin.COMPILE_JAVA_TASK_NAME)

        then:
        stdout.contains('Added @NotNull assertions to 1 files')
    }

    def 'instrument tests with nullability annotations'() {
        given:
        writeTestFile()

        when:
        run(true, JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME)

        then:
        stdout.contains('Added @NotNull assertions to 1 files')
    }

    def 'do not instrument code if option is set to false'() {
        given:
        buildFile << 'intellij { instrumentCode = false }'
        writeJavaFile()

        when:
        run(true, JavaPlugin.COMPILE_JAVA_TASK_NAME)

        then:
        !stdout.contains('Added @NotNull')
    }

    def 'do not instrument code on empty source sets'() {
        when:
        run(true, JavaPlugin.COMPILE_JAVA_TASK_NAME)

        then:
        !stdout.contains('Compiling forms and instrumenting code')
    }

    def 'patch test tasks'() {
        given:
        writeTestFile()

        when:
        def project = run(true, JavaPlugin.TEST_TASK_NAME)
        def sandboxPath = "${project.buildDirectory.absolutePath}/idea-sandbox"

        then:
        def testCommand = parseCommand(stdout)
        testCommand.properties.'idea.config.path'.equals "${sandboxPath}/config-test".toString()
        testCommand.properties.'idea.system.path'.equals "${sandboxPath}/system-test".toString()
        testCommand.properties.'idea.plugins.path'.equals "${sandboxPath}/plugins".toString()
        !testCommand.properties.containsKey('idea.required.plugins.id')

        '256m'.equals(testCommand.xms)
        '512m'.equals(testCommand.xmx)
        testCommand.xclasspath.endsWith('/lib/boot.jar')
//        '250m'.equals(testCommand.permGen)
    }

    private File writeTestFile() {
        file('src/test/java/AppTest.java') << """
import java.lang.String;
import org.junit.Test;
import org.jetbrains.annotations.NotNull;
public class AppTest {
    @Test
    public void testSomething() {}
    
    private void print(@NotNull String s) { System.out.println(s); }
}
"""
    }

    private File writeJavaFile() {
        file('src/main/java/App.java') << """  
import java.lang.String;
import org.jetbrains.annotations.NotNull;
class App {
    public static void main(@NotNull String[] strings) {
        System.out.println(strings);    
    }
}
"""
    }

    private ProcessProperties parseCommand(@NotNull String output) {
        ProcessProperties testCommand = null
        for (String line : stdout.readLines()) {
            if (line.startsWith('Starting process ')) {
                testCommand = ProcessProperties.parse(line.substring(line.indexOf('Command: ') + 'Command: '.length()))
                break
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
        String permGen = null
        def jvmArgs = new HashSet<String>()

        public static ProcessProperties parse(@NotNull String commandLine) {
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
                } else if (it.startsWith('-XX:MaxPermSize')) {
                    result.permGen = it.substring('-XX:MaxPermSize'.length())
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
