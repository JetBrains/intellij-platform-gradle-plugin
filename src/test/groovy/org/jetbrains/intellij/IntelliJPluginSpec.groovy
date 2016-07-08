package org.jetbrains.intellij

import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.jetbrains.annotations.NotNull

class IntelliJPluginSpec extends IntelliJPluginSpecBase {
    def 'intellij-specific tasks'() {
        when:
        buildFile << ""
        pluginXml << "<idea-plugin version=\"2\"></idea-plugin>"

        then:
        tasks(IntelliJPlugin.GROUP_NAME) == ['buildPlugin', 'prepareSandbox', 'prepareTestsSandbox', 'publishPlugin', 'runIdea']
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
        run(true, JavaPlugin.CLASSES_TASK_NAME)

        then:
        stdout.contains('Added @NotNull assertions to 1 files')
    }

    def 'instrument tests with nullability annotations'() {
        given:
        writeTestFile()

        when:
        run(true, JavaPlugin.TEST_CLASSES_TASK_NAME)

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

    def 'instrumentation does not invalidate compile tasks'() {
        given:
        buildFile << 'intellij { instrumentCode = true }'
        writeJavaFile()

        when:
        run(true, JavaPlugin.CLASSES_TASK_NAME)
        run(true, JavaPlugin.CLASSES_TASK_NAME)

        then:
        stdout.contains(':classes UP-TO-DATE')
    }

    def 'download idea dependencies'() {
        given:
        def cacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.3')
        when:
        run(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        if (cacheDir.exists()) {
            return // it was already cached. test is senseless until gradle clean
        }
        assert cacheDir.list() as Set == ['b52fd6ecd1178b17bbebe338a9efe975c95f7037', 'e71057c345e163250c544ee6b56411ce9ac7e23'] as Set
        assert new File(cacheDir, 'b52fd6ecd1178b17bbebe338a9efe975c95f7037').list() as Set == ['ideaIC-14.1.3.pom'] as Set
        assert new File(cacheDir, 'e71057c345e163250c544ee6b56411ce9ac7e23').list() as Set == ['ideaIC-14.1.3', 'ideaIC-14.1.3.zip'] as Set
    }

    def 'download ultimate idea dependencies'() {
        given:
        def cacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIU/14.1.5')
        def ideaCommunityCacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.5')
        buildFile << """intellij { 
            version 'IU-14.1.5'
            downloadSources true 
}"""
        when:
        run(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        if (cacheDir.exists() || ideaCommunityCacheDir.exists()) {
            return // it was already cached. test is senseless until gradle clean
        }
        assert cacheDir.list() as Set == ['af6b922431b0283c8bfe6bca871978f9d734d9c7', 'dc34a10b97955d320d1b7a46a1ce165f6d2744c0'] as Set
        assert new File(cacheDir, 'dc34a10b97955d320d1b7a46a1ce165f6d2744c0').list() as Set == ['ideaIC-14.1.5.pom'] as Set
        assert new File(cacheDir, 'af6b922431b0283c8bfe6bca871978f9d734d9c7').list() as Set == ['ideaIC-14.1.5', 'ideaIC-14.1.5.zip'] as Set
        
        // do not download ideaIC dist
        assert ideaCommunityCacheDir.list() as Set == ['f58943066d699049a2e802660d554190e613a403'] as Set
        assert new File(cacheDir, 'f58943066d699049a2e802660d554190e613a403').list() as Set == ['ideaIC-14.1.5-sources.jar'] as Set
    }

    def 'download sources if option is enabled'() {
        given:
        buildFile << 'intellij { downloadSources = true }'

        when:
        run(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def cacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.3')
        assert cacheDir.list() as Set == ['b6e282e0e4f49b6cdcb62f180f141ff1a7464ba2', 'b52fd6ecd1178b17bbebe338a9efe975c95f7037', 'e71057c345e163250c544ee6b56411ce9ac7e23'] as Set
        assert new File(cacheDir, 'b52fd6ecd1178b17bbebe338a9efe975c95f7037').list() as Set == ['ideaIC-14.1.3.pom'] as Set
        assert new File(cacheDir, 'e71057c345e163250c544ee6b56411ce9ac7e23').list() as Set == ['ideaIC-14.1.3', 'ideaIC-14.1.3.zip'] as Set
        assert new File(cacheDir, 'b6e282e0e4f49b6cdcb62f180f141ff1a7464ba2').list() as Set == ['ideaIC-14.1.3-sources.jar'] as Set
    }

    def 'patch test tasks'() {
        given:
        writeTestFile()

        when:
        def project = run(true, JavaPlugin.TEST_TASK_NAME)
        def sandboxPath = adjustWindowsPath("$project.buildDirectory.absolutePath/idea-sandbox")

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
        def sandboxPath = adjustWindowsPath("$dir.root.absolutePath/customSandbox")
        buildFile << """
intellij {
    sandboxDirectory = '$sandboxPath'    
}
"""
        when:
        run(true, JavaPlugin.TEST_TASK_NAME)

        then:
        assertPathParameters(parseCommand(stdout), sandboxPath)
    }

    @SuppressWarnings("GrEqualsBetweenInconvertibleTypes")
    private static void assertPathParameters(@NotNull ProcessProperties testCommand, @NotNull String sandboxPath) {
        assert adjustWindowsPath(testCommand.properties.'idea.config.path') == "$sandboxPath/config-test"
        assert adjustWindowsPath(testCommand.properties.'idea.system.path') == "$sandboxPath/system-test"
        assert adjustWindowsPath(testCommand.properties.'idea.plugins.path') == "$sandboxPath/plugins-test"
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
