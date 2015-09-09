package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin
import org.jetbrains.annotations.NotNull

class IntelliJPluginSpec extends IntelliJPluginSpecBase {
    def 'intellij-specific tasks'() {
        when:
        buildFile << ""
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"

        then:
        tasks(IntelliJPlugin.GROUP_NAME) == ['buildPlugin', 'patchPluginXml', 'prepareSandbox', 'runIdea']
    }

    def 'do not add intellij-specific tasks for project without plugin.xml'() {
        when:
        buildFile << ""

        then:
        tasks(IntelliJPlugin.GROUP_NAME) == null
        stdout.contains('specific tasks will be unavailable')
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
        assertPathParameters(testCommand, sandboxPath)
        !testCommand.properties.containsKey('idea.required.plugins.id')

        testCommand.xclasspath.endsWith('/lib/boot.jar')
        testCommand.xms == '256m'
        testCommand.xmx == '512m'
        testCommand.permGen == '250m'
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
        run(true, JavaPlugin.TEST_TASK_NAME)

        then:
        parseCommand(stdout).properties.'idea.required.plugins.id' == 'com.intellij.mytestid'
    }

    def 'do not update existing jvm arguments in test tasks'() {
        given:
        writeTestFile()
        buildFile << """
test {
    minHeapSize = "200m"
    maxHeapSize = "500m"
    jvmArgs '-XX:MaxPermSize=256m'    
}
"""
        when:
        run(true, JavaPlugin.TEST_TASK_NAME)

        then:
        def testCommand = parseCommand(stdout)
        testCommand.xms == '200m'
        testCommand.xmx == '500m'
        testCommand.permGen == '256m'
    }

    def 'custom sandbox directory'() {
        given:
        writeTestFile()
        def sandboxPath = "${dir.root.absolutePath}/customSandbox"
        buildFile << """
intellij {
    sandboxDirectory = '${sandboxPath}'    
}
"""
        when:
        run(true, JavaPlugin.TEST_TASK_NAME)

        then:
        assertPathParameters(parseCommand(stdout), sandboxPath)
    }

    private static void assertPathParameters(@NotNull ProcessProperties testCommand, @NotNull String sandboxPath) {
        assert testCommand.properties.'idea.config.path' == "${sandboxPath}/config-test".toString()
        assert testCommand.properties.'idea.system.path' == "${sandboxPath}/system-test".toString()
        assert testCommand.properties.'idea.plugins.path' == "${sandboxPath}/plugins".toString()
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

    private static ProcessProperties parseCommand(@NotNull String output) {
        ProcessProperties testCommand = null
        for (String line : output.readLines()) {
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
                } else if (it.startsWith('-XX:MaxPermSize=')) {
                    result.permGen = it.substring('-XX:MaxPermSize='.length())
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
