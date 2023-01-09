// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import org.apache.commons.io.FileUtils
import org.gradle.api.GradleException
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.intellij.lang.annotations.Language
import org.jetbrains.intellij.IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPOSITORY
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files.createTempDirectory
import java.nio.file.Paths
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.test.*

@Suppress("GroovyUnusedAssignment")
abstract class IntelliJPluginSpecBase {

    private var debugEnabled = true
    private val gradleDefault = System.getProperty("test.gradle.default")
    protected val gradleArguments = System.getProperty("test.gradle.arguments", "").split(' ').filter(String::isNotEmpty).toMutableList()
    protected val kotlinPluginVersion: String = System.getProperty("test.kotlin.version")
    protected val gradleVersion: String = System.getProperty("test.gradle.version").takeUnless { it.isNullOrEmpty() } ?: gradleDefault

    val gradleHome: String = System.getProperty("test.gradle.home")

    val pluginsRepository: String = System.getProperty("plugins.repository", DEFAULT_INTELLIJ_PLUGINS_REPOSITORY)
    val intellijVersion = System.getProperty("test.intellij.version").takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.intellij.version' isn't provided")
    val testMarkdownPluginVersion = System.getProperty("test.markdownPlugin.version").takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.markdownPlugin.version' isn't provided")
    var dir: File = createTempDirectory("tmp").toFile()

    val gradleProperties get() = file("gradle.properties")
    val buildFile get() = file("build.gradle")
    val pluginXml get() = file("src/main/resources/META-INF/plugin.xml")
    val buildDirectory get() = File(dir, "build")

    @BeforeTest
    open fun setUp() {
        dir = createTempDirectory("tmp").toFile()

        file("settings.gradle").groovy("rootProject.name = 'projectName'")

        buildFile.groovy(
            """
            plugins {
                id 'java'
                id 'org.jetbrains.intellij'
                id 'org.jetbrains.kotlin.jvm' version '$kotlinPluginVersion'
            }
            sourceCompatibility = 11
            targetCompatibility = 11
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
            """.trimIndent()
        )

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = false
            org.jetbrains.intellij.buildFeature.selfUpdateCheck = false
            """.trimIndent()
        )
    }

    fun writeTestFile() = file("src/test/java/AppTest.java").java(
        """
        import java.lang.String;
        import org.junit.Test;
        import org.jetbrains.annotations.NotNull;
        
        public class AppTest {
            @Test
            public void testSomething() {}
        
            private void print(@NotNull String s) { System.out.println(s); }
        }
        """.trimIndent()
    )

    @Suppress("SameParameterValue")
    protected fun disableDebug(reason: String) {
        println("Debugging is disable for test with the following reason: $reason")
        debugEnabled = false
    }

    protected fun build(vararg tasksList: String) = build(
        tasks = tasksList,
    )

    protected fun buildAndFail(vararg tasksList: String) = build(
        fail = true,
        tasks = tasksList,
    )

    protected fun build(
        gradleVersion: String = this@IntelliJPluginSpecBase.gradleVersion,
        fail: Boolean = false,
        assertValidConfigurationCache: Boolean = true,
        vararg tasks: String,
    ): BuildResult = builder(gradleVersion, *tasks)
        .run {
            when (fail) {
                true -> buildAndFail()
                false -> build()
            }
        }
        .also {
            if (assertValidConfigurationCache) {
                assertNotContains("Configuration cache problems found in this build.", it.output)
            }
        }

    private fun builder(gradleVersion: String, vararg tasks: String) =
        GradleRunner.create()
            .withProjectDir(dir)
            .withGradleVersion(gradleVersion)
            .forwardOutput()
            .withPluginClasspath()
//            .withDebug(debugEnabled)
            .withTestKitDir(File(gradleHome))
            .withArguments(
                *tasks,
                "--stacktrace",
                "--configuration-cache",
                *gradleArguments.toTypedArray()
            )//, "-Dorg.gradle.debug=true")

    fun tasks(groupName: String): List<String> = build(ProjectInternal.TASKS_TASK).output.lines().run {
        val start = indexOfFirst { it.equals("$groupName tasks", ignoreCase = true) } + 2
        drop(start).takeWhile { !it.startsWith('-') }.dropLast(1).map { it.substringBefore(' ') }
            .filterNot { it.isEmpty() }
    }

    protected fun directory(path: String) = File(dir, path).apply { mkdirs() }

    protected fun emptyZipFile(path: String): File {
        val split = path.split('/')
        val directory = when {
            split.size > 1 -> directory(split.dropLast(1).joinToString("/"))
            else -> dir
        }
        val file = File(directory, split.last())
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

    protected fun writeJavaFile() = file("src/main/java/App.java").java(
        """
        import java.lang.String;
        import java.util.Arrays;
        import org.jetbrains.annotations.NotNull;
        
        class App {
            public static void main(@NotNull String[] strings) {
                System.out.println(Arrays.toString(strings));
            }
        }
        """.trimIndent()
    )

    protected fun writeKotlinFile() = file("src/main/kotlin/App.kt").kotlin(
        """
        object App {
            @JvmStatic
            fun main(args: Array<String>) {
                println(args.joinToString())
            }
        }
        """.trimIndent()
    )

    protected fun writeKotlinUIFile() = file("src/main/kotlin/pack/AppKt.kt").kotlin(
        """
        package pack
        
        import javax.swing.JPanel
        
        class AppKt {
            private lateinit var panel: JPanel
            init {
                panel.toString()
            }
        }
        """.trimIndent()
    )

    fun adjustWindowsPath(s: String) = s.replace("\\", "/")

    protected fun assertContains(expected: String, actual: String) {
        // https://stackoverflow.com/questions/10934743/formatting-output-so-that-intellij-idea-shows-diffs-for-two-texts
        assertTrue(
            actual.contains(expected),
            """
            expected:<$expected> but was:<$actual>
            """.trimIndent()
        )
    }

    protected fun assertNotContains(expected: String, actual: String) {
        // https://stackoverflow.com/questions/10934743/formatting-output-so-that-intellij-idea-shows-diffs-for-two-texts
        assertFalse(
            actual.contains(expected),
            """
            expected:<$expected> but was:<$actual>
            """.trimIndent()
        )
    }

    protected fun assertFileContent(file: File?, @Language("xml") expectedContent: String) =
        assertEquals(expectedContent.trim(), file?.readText()?.replace("\r", "")?.trim())

    @Suppress("SameParameterValue")
    protected fun assertZipContent(zip: ZipFile, path: String, expectedContent: String) =
        assertEquals(expectedContent, fileText(zip, path))

    @Suppress("SameParameterValue")
    protected fun extractFile(zipFile: ZipFile, path: String): File =
        File.createTempFile("gradle-test", "").apply {
            deleteOnExit()
            FileUtils.copyInputStreamToFile(zipFile.getInputStream(zipFile.getEntry(path)), this)
        }

    protected fun fileText(zipFile: ZipFile, path: String) = zipFile
        .getInputStream(zipFile.getEntry(path))
        .use {
            it.bufferedReader()
                .use(BufferedReader::readText)
                .replace("\r", "")
                .trim()
        }

    protected fun collectPaths(zipFile: ZipFile) = zipFile.entries().toList().mapNotNull { it.name }.toSet()

    protected fun collectPaths(directory: File): Set<String> {
        assert(directory.exists())
        return directory.walkTopDown().filterNot { it.isDirectory }.map {
            adjustWindowsPath(it.canonicalPath.substring(directory.canonicalPath.length))
        }.toSet()
    }

    protected fun resolveResourcePath(path: String) = path.let {
        javaClass.classLoader.getResource(it)?.let { url ->
            Paths.get(url.toURI()).toAbsolutePath().toString().replace('\\', '/')
        }
    }

    // Methods can be simplified, when following tickets will be handled:
    // https://youtrack.jetbrains.com/issue/KT-24517
    // https://youtrack.jetbrains.com/issue/KTIJ-1001
    fun File.xml(@Language("XML") content: String) = append(content)

    fun File.groovy(@Language("Groovy") content: String) = append(content)

    fun File.java(@Language("Java") content: String) = append(content)

    fun File.kotlin(@Language("kotlin") content: String) = append(content)

    fun File.properties(@Language("Properties") content: String) = append(content)

    private fun File.append(content: String) = appendText(content.trimIndent() + "\n")
}
