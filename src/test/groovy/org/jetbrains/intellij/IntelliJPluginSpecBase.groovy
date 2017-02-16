package org.jetbrains.intellij

import groovy.io.FileType
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.tooling.GradleConnector
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.internal.consumer.DefaultGradleConnector
import org.gradle.tooling.model.GradleProject
import org.jetbrains.annotations.NotNull
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

import java.util.zip.ZipFile

abstract class IntelliJPluginSpecBase extends Specification {
    @Rule
    final TemporaryFolder dir = new TemporaryFolder()
    protected final String gradleHome = System.properties.get('test.gradle.home')

    def setup() {
        String localRepoPath = System.properties.get("local.repo")
        assert localRepoPath != null
        String intellijRepo = System.properties.get('intellij.repo', '')
        assert gradleHome != null
        file("settings.gradle") << "rootProject.name='projectName'\n"
        buildFile << """\
            buildscript {
                repositories { 
                    maven { url "${adjustWindowsPath(localRepoPath)}" }
                    maven { url 'http://dl.bintray.com/jetbrains/intellij-plugin-service' } 
                    mavenCentral()
                }
                dependencies {
                    classpath group: 'org.jetbrains.intellij.plugins', name: 'gradle-intellij-plugin', version: 'latest.release'
                    classpath "org.jetbrains.kotlin:kotlin-gradle-plugin:1.0.6"
                }
            }
            apply plugin: 'kotlin'
            apply plugin: 'org.jetbrains.intellij'
            repositories { mavenCentral() }
            intellij {
                version = '14.1.3'
                downloadSources = false
                intellijRepo = '$intellijRepo'
            }

            // Define tasks with a minmal set of tasks required to build a source set
            sourceSets.all {
                task(it.getTaskName('build', 'SourceSet'), dependsOn: it.output)
            }

        """.stripIndent()
    }

    def cleanup() {

    }

    private final OutputStream standardError = new ByteArrayOutputStream()
    private final ByteArrayOutputStream standardOutput = new ByteArrayOutputStream()

    protected GradleProject run(boolean infoLogging, String... tasks) {
        GradleConnector gradleConnector = (GradleConnector.newConnector()
                .useGradleUserHomeDir(new File(gradleHome))
                .forProjectDirectory(dir.root) as DefaultGradleConnector)
        ProjectConnection connection = gradleConnector.connect()
        try {
            standardOutput.reset()

            def build = connection.newBuild()
            if (infoLogging) build.withArguments("--info")
            build.setStandardError(standardError)
                    .setStandardOutput(standardOutput)
                    .forTasks(tasks).run()
            return connection.getModel(GradleProject)
        }
        finally {
            connection?.close()
        }
    }

    protected GradleProject run(String... tasks) {
        return run(false, tasks)
    }

    protected String getStdout() {
        standardOutput.toString()
    }

    protected String getStderr() {
        standardError.toString()
    }

    protected File getBuildFile() {
        file('build.gradle')
    }

    protected File getPluginXml() {
        file('src/main/resources/META-INF/plugin.xml')
    }

    protected List<String> tasks(@NotNull String groupName) {
        run(ProjectInternal.TASKS_TASK)
        List<String> result = new ArrayList<>()
        boolean targetGroupAppeared = false
        for (String line : stdout.readLines()) {
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
