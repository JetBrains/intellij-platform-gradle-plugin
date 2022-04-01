package org.jetbrains.intellij

import com.jetbrains.plugin.structure.base.utils.create
import com.jetbrains.plugin.structure.base.utils.createDir
import com.jetbrains.plugin.structure.base.utils.exists
import com.jetbrains.plugin.structure.base.utils.isDirectory
import com.jetbrains.plugin.structure.base.utils.outputStream
import com.jetbrains.plugin.structure.base.utils.readText
import com.jetbrains.plugin.structure.base.utils.toSystemIndependentName
import com.jetbrains.plugin.structure.base.utils.writeText
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import java.io.BufferedReader
import java.nio.file.Files
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.streams.toList
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

@Suppress("GroovyUnusedAssignment")
abstract class IntelliJPluginSpecBase {

    private var debugEnabled = true
    private val kotlinPluginVersion = System.getProperty("test.kotlin.version")
    private val gradleDefault = System.getProperty("test.gradle.default")
    private val gradleArguments = System.getProperty("test.gradle.arguments", "")
        .split(' ').filter(String::isNotEmpty).toTypedArray()
    protected val gradleVersion: String = System.getProperty("test.gradle.version").takeUnless { it.isNullOrEmpty() } ?: gradleDefault

    val gradleHome: Path = Path.of(System.getProperty("test.gradle.home"))

    val pluginsRepository: String = System.getProperty("plugins.repository", IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPOSITORY)
    val intellijVersion = "2020.1"
    val dir: Path by lazy { createTempDirectory("tmp") }

    private val gradleProperties = createFile("gradle.properties")
    val buildFile = createFile("build.gradle")
    val pluginXml = createFile("src/main/resources/META-INF/plugin.xml")
    val buildDirectory: Path by lazy { dir.resolve("build") }

    @BeforeTest
    open fun setUp() {
        createFile("settings.gradle").groovy(
            """
            rootProject.name = 'projectName'
        """
        )

        buildFile.groovy(
            """
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
                buildSearchableOptions {
                    enabled = false
                }
    
                // Define tasks with a minimal set of tasks required to build a source set
                sourceSets.all {
                    task(it.getTaskName('build', 'SourceSet'), dependsOn: it.output)
                }
            """
        )

        gradleProperties.groovy(
            """
                kotlin.stdlib.default.dependency = false
            """
        )
    }

    fun writeTestFile() = createFile("src/test/java/AppTest.java").java(
        """
        import java.lang.String;
        import org.junit.Test;
        import org.jetbrains.annotations.NotNull;

        public class AppTest {
            @Test
            public void testSomething() {}
        
            private void print(@NotNull String s) { System.out.println(s); }
        }
    """
    )

    @Suppress("SameParameterValue")
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
            .withProjectDir(dir.toFile())
            .withGradleVersion(gradleVersion)
            .forwardOutput()
            .withPluginClasspath()
            .withDebug(debugEnabled)
            .withTestKitDir(gradleHome.toFile())
            .withArguments(*tasks, "--stacktrace", "--configuration-cache", *gradleArguments)//, "-Dorg.gradle.debug=true")

    fun tasks(groupName: String): List<String> = build(ProjectInternal.TASKS_TASK).output.lines().run {
        val start = indexOfFirst { it.equals("$groupName tasks", ignoreCase = true) } + 2
        drop(start).takeWhile(String::isNotEmpty).map { it.substringBefore(' ') }
    }

    protected fun directory(path: String) = dir.resolve(path).createDir()

    protected fun emptyZipFile(path: String) {
        val splitted = path.split('/')
        val directory = when {
            splitted.size > 1 -> directory(splitted.dropLast(1).joinToString("/"))
            else -> dir
        }
        val file = directory.resolve(splitted.last())
        val outputStream = file.outputStream()
        val zipOutputStream = ZipOutputStream(outputStream)
        zipOutputStream.close()
        outputStream.close()
    }

    protected fun file(path: String) = createFile(path).toFile()

    protected fun createFile(path: String): Path = when {
        path.startsWith('/') -> Path.of(path)
        else -> dir.resolve(path)
    }.apply { if (!exists()) create() }

    protected fun writeJavaFile() = createFile("src/main/java/App.java").java(
        """
        import java.lang.String;
        import java.util.Arrays;
        import org.jetbrains.annotations.NotNull;

        class App {
            public static void main(@NotNull String[] strings) {
                System.out.println(Arrays.toString(strings));
            }
        }
    """
    )

    protected fun writeKotlinFile() = createFile("src/main/kotlin/App.kt").kotlin(
        """
        object App {
            @JvmStatic
            fun main(args: Array<String>) {
                println(args.joinToString())
            }
        }
    """
    )

    protected fun writeKotlinUIFile() = createFile("src/main/kotlin/pack/AppKt.kt").kotlin(
        """
        package pack

        import javax.swing.JPanel

        class AppKt {
            private lateinit var panel: JPanel
            init {
                panel.toString()
            }
        }
    """
    )

    protected fun assertFileContent(path: Path, @Language("xml") expectedContent: String) =
        assertEquals(expectedContent.trimIndent().trim(), path.readText().replace("\r", "").trim())

    @Suppress("SameParameterValue")
    protected fun assertZipContent(zipPath: Path, path: String, expectedContent: String) =
        assertEquals(expectedContent.trimIndent(), fileText(zipPath, path))

    @Suppress("SameParameterValue")
    protected fun extractFile(zipPath: Path, path: String): Path =
        Files.createTempFile("gradle-test", "").apply {
            ZipFile(zipPath.toFile()).run { getInputStream(getEntry(path)) }.copyTo(outputStream())
        }

    protected fun fileText(zipPath: Path, path: String) = ZipFile(zipPath.toFile()).run {
        getInputStream(getEntry(path))
    }
        .bufferedReader()
        .use(BufferedReader::readText)
        .replace("\r", "")
        .trim()

    protected fun collectPathsFromZip(zipPath: Path) = ZipFile(zipPath.toFile()).entries().toList().mapNotNull { it.name }.sorted()

    protected fun collectPaths(path: Path) = Files.walk(path).filter { !it.isDirectory }.map {
        path.relativize(it).toString()
    }.toList().sorted()

    fun Path?.toSystemIndependentString() = toString().toSystemIndependentName()

    // Methods can be simplified, when following tickets will be handled:
    // https://youtrack.jetbrains.com/issue/KT-24517
    // https://youtrack.jetbrains.com/issue/KTIJ-1001
    fun Path.xml(@Language("XML") content: String) = append(content)

    fun Path.groovy(@Language("Groovy") content: String) = append(content)

    fun Path.java(@Language("Java") content: String) = append(content)

    fun Path.kotlin(@Language("kotlin") content: String) = append(content)

    private fun Path.append(content: String) = writeText(readText() + content.trimIndent() + "\n")
}
