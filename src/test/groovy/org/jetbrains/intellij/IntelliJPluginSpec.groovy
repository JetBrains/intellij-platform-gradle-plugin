package org.jetbrains.intellij

import org.gradle.api.plugins.JavaPlugin

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
}
