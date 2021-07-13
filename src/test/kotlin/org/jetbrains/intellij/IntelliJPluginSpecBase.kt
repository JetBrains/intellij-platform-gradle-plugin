package org.jetbrains.intellij

import org.apache.commons.io.FileUtils
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files.createTempDirectory
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

abstract class IntelliJPluginSpecBase {

    private var debugEnabled = true
    private val kotlinPluginVersion = System.getProperty("test.kotlin.version")
    private val gradleDefault = System.getProperty("test.gradle.default")
    private val gradleVersion = System.getProperty("test.gradle.version", gradleDefault)
    private val gradleArguments = System.getProperty("test.gradle.arguments", "")
        .split(' ').filter(String::isNotEmpty).toTypedArray()

    val gradleHome: String = System.getProperty("test.gradle.home")

    val pluginsRepository: String = System.getProperty("plugins.repository", IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPOSITORY)
    val intellijVersion = "2020.1"
    val dir: File = createTempDirectory("tmp").toFile()

    private val gradleProperties = file("gradle.properties")
    val buildFile = file("build.gradle")
    val pluginXml = file("src/main/resources/META-INF/plugin.xml")
    val buildDirectory = File(dir, "build")

    @BeforeTest
    open fun setUp() {
        file("settings.gradle").groovy("rootProject.name = 'projectName'")

        buildFile.groovy("""
            plugins {
                id 'java'
                id 'org.jetbrains.intellij'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinPluginVersion'
            }
            sourceCompatibility = 1.8
            targetCompatibility = 1.8
            repositories {
                mavenCentral()
            }
            intellij {
                version = '$intellijVersion'
                downloadSources = false
                pluginsRepositories {
                    maven('$pluginsRepository')
                }
                instrumentCode = false
            }

            // Define tasks with a minimal set of tasks required to build a source set
            sourceSets.all {
                task(it.getTaskName('build', 'SourceSet'), dependsOn: it.output)
            }
        """)

        gradleProperties.groovy("""
            kotlin.stdlib.default.dependency = false
        """)
    }

    fun writeTestFile() = file("src/test/java/AppTest.java").java("""
        import java.lang.String;
        import org.junit.Test;
        import org.jetbrains.annotations.NotNull;

        public class AppTest {
            @Test
            public void testSomething() {}
        
            private void print(@NotNull String s) { System.out.println(s); }
        }
    """)

    protected fun disableDebug(reason: String) {
        println("Debugging is disable for test with the following reason: $reason")
        debugEnabled = false
    }

    protected fun buildAndFail(vararg tasks: String): BuildResult = build(true, *tasks)

    protected fun build(vararg tasks: String): BuildResult = build(false, *tasks)

    protected fun build(fail: Boolean, vararg tasks: String): BuildResult = build(gradleVersion, fail, *tasks)

    protected fun build(gradleVersion: String, fail: Boolean = false, vararg tasks: String): BuildResult =
        builder(gradleVersion, *tasks).run {
            when (fail) {
                true -> buildAndFail()
                false -> build()
            }
        }

    private fun builder(gradleVersion: String, vararg tasks: String) =
        GradleRunner.create()
            .withProjectDir(dir)
            .withGradleVersion(gradleVersion)
            .forwardOutput()
            .withPluginClasspath()
            .withDebug(debugEnabled)
            .withTestKitDir(File(gradleHome))
            .withArguments(*tasks, "--stacktrace", "--configuration-cache", *gradleArguments)//, "-Dorg.gradle.debug=true")

    fun tasks(groupName: String): List<String> = build(ProjectInternal.TASKS_TASK).output.lines().run {
        val start = indexOfFirst { it.equals("$groupName tasks", ignoreCase = true) } + 2
        drop(start).takeWhile(String::isNotEmpty).map { it.substringBefore(' ') }
    }

    protected fun directory(path: String) = File(dir, path).apply { mkdirs() }

    protected fun emptyZipFile(path: String): File {
        val splitted = path.split('/')
        val directory = when {
            splitted.size > 1 -> directory(splitted.dropLast(1).joinToString("/"))
            else -> dir
        }
        val file = File(directory, splitted.last())
        val outputStream = FileOutputStream(file)
        val zipOutputStream = ZipOutputStream(outputStream)
        zipOutputStream.close()
        outputStream.close()
        return file
    }

    protected fun file(path: String) = path
        .run { takeIf { startsWith('/') } ?: "${dir.path}/$this" }
        .split('/')
        .run { File(dropLast(1).joinToString("/")) to last() }
        .apply { if (!first.exists()) first.mkdirs() }
        .run { File(first, second) }
        .apply { createNewFile() }

    protected fun writeJavaFile() = file("src/main/java/App.java").java("""
        import java.lang.String;
        import java.util.Arrays;
        import org.jetbrains.annotations.NotNull;

        class App {
            public static void main(@NotNull String[] strings) {
                System.out.println(Arrays.toString(strings));
            }
        }
    """)

    protected fun writeKotlinFile() = file("src/main/kotlin/App.kt").kotlin("""
        object App {
            @JvmStatic
            fun main(args: Array<String>) {
                println(args.joinToString())
            }
        }
    """)

    protected fun writeKotlinUIFile() = file("src/main/kotlin/pack/AppKt.kt").kotlin("""
        package pack

        import javax.swing.JPanel

        class AppKt {
            private lateinit var panel: JPanel
            init {
                panel.toString()
            }
        }
    """)

    fun adjustWindowsPath(s: String) = s.replace("\\", "/")

    protected fun assertFileContent(file: File?, @Language("xml") expectedContent: String) =
        assertEquals(expectedContent.trimIndent().trim(), file?.readText()?.replace("\r", "")?.trim())

    protected fun assertZipContent(zip: ZipFile, path: String, expectedContent: String) =
        assertEquals(expectedContent.trimIndent(), fileText(zip, path))

    protected fun extractFile(zipFile: ZipFile, path: String): File =
        File.createTempFile("gradle-test", "").apply {
            deleteOnExit()
            FileUtils.copyInputStreamToFile(zipFile.getInputStream(zipFile.getEntry(path)), this)
        }

    protected fun fileText(zipFile: ZipFile, path: String) = zipFile
        .getInputStream(zipFile.getEntry(path))
        .bufferedReader()
        .use(BufferedReader::readText)
        .replace("\r", "")
        .trim()

    protected fun collectPaths(zipFile: ZipFile) = zipFile.entries().toList().mapNotNull { it.name }.toSet()

    protected fun collectPaths(directory: File): Set<String> {
        assert(directory.exists())
        return directory.walkTopDown().filterNot { it.isDirectory }.map {
            adjustWindowsPath(it.absolutePath.substring(directory.absolutePath.length))
        }.toSet()
    }

    // Methods can be simplified, when following tickets will be handled:
    // https://youtrack.jetbrains.com/issue/KT-24517
    // https://youtrack.jetbrains.com/issue/KTIJ-1001
    fun File.xml(@Language("XML") content: String) = append(content)

    fun File.groovy(@Language("Groovy") content: String) = append(content)

    fun File.java(@Language("Java") content: String) = append(content)

    fun File.kotlin(@Language("kotlin") content: String) = append(content)

    private fun File.append(content: String) = appendText(content.trimIndent() + "\n")
}
