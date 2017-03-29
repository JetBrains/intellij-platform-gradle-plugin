package org.jetbrains.intellij

import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.annotations.NotNull

class IntelliJPluginSpec extends IntelliJPluginSpecBase {
    def 'intellij-specific tasks'() {
        when:
        buildFile << ""

        then:
        tasks(IntelliJPlugin.GROUP_NAME) == [IntelliJPlugin.BUILD_PLUGIN_TASK_NAME,
                                             IntelliJPlugin.PATCH_PLUGIN_XML_TASK_NAME,
                                             IntelliJPlugin.PREPARE_SANDBOX_TASK_NAME,
                                             IntelliJPlugin.PREPARE_TESTING_SANDBOX_TASK_NAME,
                                             IntelliJPlugin.PUBLISH_PLUGIN_TASK_NAME,
                                             IntelliJPlugin.RUN_IDE_TASK_NAME,
                                             IntelliJPlugin.RUN_IDEA_TASK_NAME]
    }

    def 'instrument code with nullability annotations'() {
        given:
        buildFile << 'intellij { instrumentCode = true }'
        writeJavaFile()

        when:
        def result = build('buildSourceSet', '--info')

        then:
        result.output.contains('Added @NotNull assertions to 1 files')
    }

    def 'instrument tests with nullability annotations'() {
        given:
        writeTestFile()

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
        file('src/main/kotlin/pack/App.form') << """<?xml version="1.0" encoding="UTF-8"?>
<form xmlns="http://www.intellij.com/uidesigner/form/" version="1" bind-to-class="pack.App">
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
        file('src/main/kotlin/pack/App.kt') << """package pack
import javax.swing.JPanel
class App {
    private lateinit var panel: JPanel
    init {
        panel.toString()
    }
}"""

        when:
        def result = build('buildSourceSet', '--info')

        then:
        result.output.contains('Compiling forms and instrumenting code')
    }

    def 'instrumentation does not invalidate compile tasks'() {
        given:
        buildFile << 'intellij { instrumentCode = true }'
        writeJavaFile()

        when:
        build('buildSourceSet')
        def result = build('buildSourceSet')

        then:
        result.task(":$JavaPlugin.CLASSES_TASK_NAME").outcome == TaskOutcome.UP_TO_DATE
    }

    def 'download idea dependencies'() {
        given:
        def cacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.3')

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        if (cacheDir.exists()) {
            return // it was already cached. test is senseless until gradle clean
        }
        cacheDir.list() as Set == ['b52fd6ecd1178b17bbebe338a9efe975c95f7037', 'e71057c345e163250c544ee6b56411ce9ac7e23'] as Set
        new File(cacheDir, 'b52fd6ecd1178b17bbebe338a9efe975c95f7037').list() as Set == ['ideaIC-14.1.3.pom'] as Set
        new File(cacheDir, 'e71057c345e163250c544ee6b56411ce9ac7e23').list() as Set == ['ideaIC-14.1.3', 'ideaIC-14.1.3.zip'] as Set
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
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        if (cacheDir.exists() || ideaCommunityCacheDir.exists()) {
            return // it was already cached. test is senseless until gradle clean
        }
        cacheDir.list() as Set == ['af6b922431b0283c8bfe6bca871978f9d734d9c7', 'dc34a10b97955d320d1b7a46a1ce165f6d2744c0'] as Set
        new File(cacheDir, 'dc34a10b97955d320d1b7a46a1ce165f6d2744c0').list() as Set == ['ideaIC-14.1.5.pom'] as Set
        new File(cacheDir, 'af6b922431b0283c8bfe6bca871978f9d734d9c7').list() as Set == ['ideaIC-14.1.5', 'ideaIC-14.1.5.zip'] as Set

        // do not download ideaIC dist
        ideaCommunityCacheDir.list() as Set == ['f58943066d699049a2e802660d554190e613a403'] as Set
        new File(cacheDir, 'f58943066d699049a2e802660d554190e613a403').list() as Set == ['ideaIC-14.1.5-sources.jar'] as Set
    }

    def 'download sources if option is enabled'() {
        given:
        buildFile << 'intellij { downloadSources = true }'

        when:
        build(BasePlugin.ASSEMBLE_TASK_NAME)

        then:
        def cacheDir = new File(gradleHome, 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/ideaIC/14.1.3')
        cacheDir.list() as Set == ['b6e282e0e4f49b6cdcb62f180f141ff1a7464ba2', 'b52fd6ecd1178b17bbebe338a9efe975c95f7037', 'e71057c345e163250c544ee6b56411ce9ac7e23'] as Set
        new File(cacheDir, 'b52fd6ecd1178b17bbebe338a9efe975c95f7037').list() as Set == ['ideaIC-14.1.3.pom'] as Set
        new File(cacheDir, 'e71057c345e163250c544ee6b56411ce9ac7e23').list() as Set == ['ideaIC-14.1.3', 'ideaIC-14.1.3.zip'] as Set
        new File(cacheDir, 'b6e282e0e4f49b6cdcb62f180f141ff1a7464ba2').list() as Set == ['ideaIC-14.1.3-sources.jar'] as Set
    }

    def 'patch test tasks'() {
        given:
        writeTestFile()

        when:
        def result = build(JavaPlugin.TEST_TASK_NAME, '--info')
        def sandboxPath = adjustWindowsPath("$buildDirectory.canonicalPath/idea-sandbox")

        then:
        def testCommand = parseCommand(result.output)
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
    jvmArgs '-XX:MaxPermSize=256m'    
}
"""
        when:
        def result = build(JavaPlugin.TEST_TASK_NAME, '--info')

        then:
        def testCommand = parseCommand(result.output)
        testCommand.xms == '200m'
        testCommand.xmx == '500m'
        testCommand.permGen == '256m'
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
