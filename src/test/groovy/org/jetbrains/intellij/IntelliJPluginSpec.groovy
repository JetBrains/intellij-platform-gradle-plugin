package org.jetbrains.intellij

import groovy.io.FileType
import org.gradle.api.plugins.JavaPlugin
import org.jetbrains.annotations.NotNull

import java.util.zip.ZipFile

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
        def sandboxPath = adjustWindowsPath("${project.buildDirectory.absolutePath}/idea-sandbox")

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
        def sandboxPath = adjustWindowsPath("${dir.root.absolutePath}/customSandbox")
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

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    def 'prepare sandbox task'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << ''
        file('src/main/resources/META-INF/nonIncluded.xml') << ''
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        buildFile << """version='0.42.123'
intellij { 
    version = '14.1.4'
    pluginName = 'myPluginName' 
    plugins = ['copyright'] 
}
dependencies { 
    compile 'joda-time:joda-time:2.8.1'
}

"""
        when:
        def project = run(PrepareSandboxTask.NAME)

        then:
        File sandbox = new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)
        assert collectPaths(sandbox) == ['/plugins/myPluginName/classes/App.class',
                                         '/plugins/myPluginName/classes/META-INF/nonIncluded.xml',
                                         '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                         '/plugins/myPluginName/META-INF/other.xml',
                                         '/plugins/myPluginName/META-INF/plugin.xml']
        assert new File(sandbox, 'plugins/myPluginName/META-INF/plugin.xml').text.trim() == """<idea-plugin version="2">
  <depends config-file="other.xml"/>
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
</idea-plugin>"""
    }

    def 'prepare custom sandbox task'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << ''
        file('src/main/resources/META-INF/nonIncluded.xml') << ''
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        def sandboxPath = adjustWindowsPath("${dir.root.absolutePath}/customSandbox")
        buildFile << """version='0.42.123'
intellij { 
    version = '14.1.4'
    pluginName = 'myPluginName' 
    plugins = ['copyright'] 
    sandboxDirectory = '${sandboxPath}'
}
dependencies { 
    compile 'joda-time:joda-time:2.8.1'
}

"""
        when:
        run(PrepareSandboxTask.NAME)

        then:
        assert collectPaths(new File(sandboxPath)) == ['/plugins/myPluginName/classes/App.class',
                                                       '/plugins/myPluginName/classes/META-INF/nonIncluded.xml',
                                                       '/plugins/myPluginName/lib/joda-time-2.8.1.jar',
                                                       '/plugins/myPluginName/META-INF/other.xml',
                                                       '/plugins/myPluginName/META-INF/plugin.xml']
        assert new File(sandboxPath, 'plugins/myPluginName/META-INF/plugin.xml').text.trim() == """<idea-plugin version="2">
  <depends config-file="other.xml"/>
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
</idea-plugin>"""
    }

    private static ArrayList collectPaths(File directory) {
        assert directory.exists()
        def paths = []
        directory.eachFileRecurse(FileType.FILES) {
            paths << adjustWindowsPath(it.absolutePath.substring(directory.absolutePath.length()))
        }
        paths
    }

    private static String[] list(File parent, String path) {
        new File(parent, path).list()
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    def 'use gradle project name if plugin name is not defined'() {
        given:
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        def project = run(PrepareSandboxTask.NAME)

        then:
        assert collectPaths(new File(project.buildDirectory, IntelliJPlugin.DEFAULT_SANDBOX)) == ["/plugins/${project.name}/META-INF/plugin.xml"]
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    def 'build plugin distribution'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << ''
        file('src/main/resources/META-INF/nonIncluded.xml') << ''
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        buildFile << """version='0.42.123'
intellij { 
    version = '14.1.4'
    pluginName = 'myPluginName' 
    plugins = ['copyright'] 
}
dependencies { 
    compile 'joda-time:joda-time:2.8.1'
}

"""
        when:
        def project = run(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(project.buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        assert distribution.exists()

        def zipFile = new ZipFile(distribution)
        assert zipFile.entries().collect { it.name } == ['myPluginName/',
                                                         'myPluginName/classes/',
                                                         'myPluginName/classes/App.class',
                                                         'myPluginName/classes/META-INF/',
                                                         'myPluginName/classes/META-INF/nonIncluded.xml',
                                                         'myPluginName/lib/',
                                                         'myPluginName/lib/joda-time-2.8.1.jar',
                                                         'myPluginName/META-INF/',
                                                         'myPluginName/META-INF/other.xml',
                                                         'myPluginName/META-INF/plugin.xml']
        zipFile.getInputStream(zipFile.getEntry('myPluginName/META-INF/plugin.xml')).text.trim() == """<idea-plugin version="2">
  <depends config-file="other.xml"/>
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
</idea-plugin>"""
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    def 'use custom sandbox for distribution'() {
        given:
        writeJavaFile()
        file('src/main/resources/META-INF/other.xml') << ''
        file('src/main/resources/META-INF/nonIncluded.xml') << ''
        pluginXml << '<idea-plugin version="2"><depends config-file="other.xml"/></idea-plugin>'
        def sandboxPath = adjustWindowsPath("${dir.root.absolutePath}/customSandbox")
        buildFile << """version='0.42.123'
intellij { 
    version = '14.1.4'
    pluginName = 'myPluginName' 
    plugins = ['copyright'] 
    sandboxDirectory = '${sandboxPath}'
}
dependencies { 
    compile 'joda-time:joda-time:2.8.1'
}

"""
        when:
        def project = run(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        File distribution = new File(project.buildDirectory, 'distributions/myPluginName-0.42.123.zip')
        assert distribution.exists()

        def zipFile = new ZipFile(distribution)
        assert zipFile.entries().collect { it.name } == ['myPluginName/',
                                                         'myPluginName/classes/',
                                                         'myPluginName/classes/App.class',
                                                         'myPluginName/classes/META-INF/',
                                                         'myPluginName/classes/META-INF/nonIncluded.xml',
                                                         'myPluginName/lib/',
                                                         'myPluginName/lib/joda-time-2.8.1.jar',
                                                         'myPluginName/META-INF/',
                                                         'myPluginName/META-INF/other.xml',
                                                         'myPluginName/META-INF/plugin.xml']
        zipFile.getInputStream(zipFile.getEntry('myPluginName/META-INF/plugin.xml')).text.trim() == """<idea-plugin version="2">
  <depends config-file="other.xml"/>
  <version>0.42.123</version>
  <idea-version since-build="141.1532.4" until-build="141.9999"/>
</idea-plugin>"""
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    def 'use gradle project name for distribution if plugin name is not defined'() {
        given:
        buildFile << 'version="0.42.123"'
        pluginXml << '<idea-plugin version="2"></idea-plugin>'

        when:
        def project = run(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME)

        then:
        assert list(project.buildDirectory, "distributions") == ["${project.name}-0.42.123.zip"]
    }


    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    private static void assertPathParameters(@NotNull ProcessProperties testCommand, @NotNull String sandboxPath) {
        assert adjustWindowsPath(testCommand.properties.'idea.config.path') == "${sandboxPath}/config-test"
        assert adjustWindowsPath(testCommand.properties.'idea.system.path') == "${sandboxPath}/system-test"
        assert adjustWindowsPath(testCommand.properties.'idea.plugins.path') == "${sandboxPath}/plugins"
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
