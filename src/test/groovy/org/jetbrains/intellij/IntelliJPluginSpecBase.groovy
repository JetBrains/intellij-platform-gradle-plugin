package org.jetbrains.intellij

import groovy.io.FileType
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.jetbrains.annotations.NotNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.zip.ZipFile

abstract class IntelliJPluginSpecBase extends Specification {
    protected final String gradleHome = System.properties.get('test.gradle.home')
    protected String pluginsRepo = System.properties.get('plugins.repo', IntelliJPlugin.DEFAULT_INTELLIJ_PLUGINS_REPO)

    String getIntellijVersion() {
        return '2020.1'
    }

    @Rule
    final TemporaryFolder dir = new TemporaryFolder()
    private boolean debugEnabled = true

    def setup() {
        file("settings.gradle") << "rootProject.name='projectName'\n"
        buildFile << """
            buildscript {
                repositories { 
                    maven { url 'https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-structure' } 
                    mavenCentral()
                }
                dependencies {
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.60"
                }
            }
            plugins {
                id 'org.jetbrains.intellij'
            }
            sourceCompatibility = 1.8
            targetCompatibility = 1.8
            apply plugin: 'java'
            apply plugin: 'kotlin'
            repositories { mavenCentral() }
            intellij {
                version = '$intellijVersion'
                downloadSources = false
                pluginsRepo = '$pluginsRepo'
                instrumentCode = false
            }

            // Define tasks with a minimal set of tasks required to build a source set
            sourceSets.all {
                task(it.getTaskName('build', 'SourceSet'), dependsOn: it.output)
            }

        """.stripIndent()
    }

    def cleanup() {

    }

    protected File writeTestFile() {
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

    protected disableDebug(String reason) {
        println("Debugging is disable for test with the following reason: $reason")
        debugEnabled = false
    }

    protected BuildResult buildAndFail(String... tasks) {
        return build(true, tasks)
    }

    protected BuildResult build(String... tasks) {
        return build(false, tasks)
    }

    protected BuildResult build(boolean fail, String... tasks) {
        return build('4.9', fail, tasks)
    }

    protected BuildResult build(String gradleVersion, boolean fail, String... tasks) {
        GradleRunner builder = builder(gradleVersion, tasks)
        return fail ? builder.buildAndFail() : builder.build()
    }

    private GradleRunner builder(String gradleVersion, String[] tasks) {
        tasks += ['--stacktrace']
        def builder = GradleRunner.create().withProjectDir(dir.root).withGradleVersion(gradleVersion)
                .withPluginClasspath()
                .withDebug(debugEnabled)
                .withTestKitDir(new File(gradleHome))
                .withArguments(tasks)
        return builder
    }

    protected File getBuildFile() {
        file('build.gradle')
    }

    protected File getBuildDirectory() {
        return new File(dir.root, "build")
    }

    protected File getPluginXml() {
        file('src/main/resources/META-INF/plugin.xml')
    }

    protected List<String> tasks(@NotNull String groupName) {
        def buildResult = build(ProjectInternal.TASKS_TASK)
        List<String> result = new ArrayList<>()
        boolean targetGroupAppeared = false
        for (String line : buildResult.output.readLines()) {
            if (!targetGroupAppeared) {
                targetGroupAppeared = line.equalsIgnoreCase(groupName + " tasks")
            } else if (line != ("-" * (groupName + " tasks").length())) {
                if (!line) break
                def spaceIndex = line.indexOf(' ')
                result.add(spaceIndex > 0 ? line.substring(0, spaceIndex) : line)
            }
        }
        return targetGroupAppeared ? result : null
    }


    protected File directory(String path) {
        new File(dir.root, path).with {
            mkdirs()
            it
        }
    }

    protected File file(String path) {
        def splitted = path.split('/')
        def directory = splitted.size() > 1 ? directory(splitted[0..-2].join('/')) : dir.root
        def file = new File(directory, splitted[-1])
        file.createNewFile()
        file
    }

    protected File writeJavaFile() {
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

    protected File writeKotlinUIFile() {
        file('src/main/kotlin/pack/AppKt.kt') << """package pack
import javax.swing.JPanel
class AppKt {
    private lateinit var panel: JPanel
    init {
        panel.toString()
    }
}"""
    }

    protected static String adjustWindowsPath(@NotNull String s) {
        return s.replaceAll('\\\\', '/')
    }

    protected static void assertFileContent(File file, String expectedContent) {
        assert file.text.trim() == expectedContent.stripIndent()
    }

    protected static File extractFile(ZipFile zipFile, String path) {
        def tmp = File.createTempFile("gradle-test", "")
        tmp.deleteOnExit()
        tmp << zipFile.getInputStream(zipFile.getEntry(path))
        return tmp
    }

    protected static String fileText(ZipFile zipFile, String path) {
        zipFile.getInputStream(zipFile.getEntry(path)).text.trim()
    }

    protected static Set<String> collectPaths(ZipFile zipFile) {
        return zipFile.entries().collect { it.name } as Set
    }

    protected static Set collectPaths(File directory) {
        assert directory.exists()
        def paths = new HashSet()
        directory.eachFileRecurse(FileType.FILES) {
            paths << adjustWindowsPath(it.absolutePath.substring(directory.absolutePath.length()))
        }
        paths
    }
}
