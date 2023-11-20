// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.GradleException
import org.gradle.api.internal.project.ProjectInternal
import org.intellij.lang.annotations.Language
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPOSITORY
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.pathString
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("GroovyUnusedAssignment")
abstract class IntelliJPluginSpecBase : IntelliJPlatformTestBase() {

    val pluginsRepository: String = System.getProperty("plugins.repository", DEFAULT_INTELLIJ_PLUGINS_REPOSITORY)

    val intellijType = System.getProperty("test.intellij.type")
        .takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.intellij.type' isn't provided")
    val intellijVersion = System.getProperty("test.intellij.version")
        .takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.intellij.version' isn't provided")
    val testMarkdownPluginVersion = System.getProperty("test.markdownPlugin.version")
        .takeUnless { it.isNullOrEmpty() }
        ?: throw GradleException("'test.markdownPlugin.version' isn't provided")

    val gradleProperties get() = file("gradle.properties")
    val buildFile get() = file("build.gradle.kts")
    val settingsFile get() = file("settings.gradle.kts")
    val pluginXml get() = file("src/main/resources/META-INF/plugin.xml")
    val buildDirectory get() = dir.resolve("build")

    @BeforeTest
    override fun setup() {
        super.setup()

        if (gradleScan) {
            settingsFile.groovy(
                """                    
                plugins {
                    id("com.gradle.enterprise") version "3.12.6"
                }
                gradleEnterprise {
                    buildScan {
                        server = "https://ge.jetbrains.com"
                        termsOfServiceUrl = "https://ge.jetbrains.com/terms-of-service"
                        termsOfServiceAgree = "yes"
                    }
                }
                """.trimIndent()
            )
        }
        settingsFile.kotlin(
            """
            rootProject.name = "projectName"
            """.trimIndent()
        )

        buildFile.kotlin(
            """
            import java.util.*
            import org.jetbrains.intellij.platform.gradle.*
            import org.jetbrains.intellij.platform.gradle.tasks.*
            
            version = "1.0.0"
            
            plugins {
                id("java")
                id("org.jetbrains.intellij.platform")
                id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            }
            
            kotlin {
                jvmToolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                    vendor = JvmVendorSpec.JETBRAINS
                }
            }
            
            repositories {
                mavenCentral()
                
                intellijPlatform {
                    ivy()
                    releases()
                }
            }
            
            dependencies {
                intellijPlatform {
                    create("$intellijType", "$intellijVersion")
                }
            }
                        
            tasks {
                buildSearchableOptions {
                    enabled = false
                }
            }            
                        
            //            intellij {
            //                pluginsRepositories {
            //                    maven('$pluginsRepository')
            //                }
            //                instrumentCode = false
            //            }
                        
                        // Define tasks with a minimal set of tasks required to build a source set
            //            sourceSets.all {
            //                task(it.getTaskName('build', 'SourceSet'), dependsOn: it.output)
            //            }
            """.trimIndent()
        )

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = false
            org.jetbrains.intellij.platform.buildFeature.selfUpdateCheck = false
            """.trimIndent()
        )
    }

    fun buildFile(
        repositories: String =
            """
            mavenCentral()
            
            intellijPlatform {
                releases()
            }
            """.trimIndent(),
        dependencies: String =
            """
            intellijPlatform {
                intellijIdeaCommunity("$intellijVersion")
            }
            """.trimIndent(),
        tasks: String = "",
        custom: String = "",
    ) {
        buildFile.writeText("")
        buildFile.groovy(
            """
            import java.util.*
            import org.jetbrains.intellij.platform.gradle.*
            import org.jetbrains.intellij.platform.gradle.tasks.*
            
            plugins {
                id("java")
                id("org.jetbrains.intellij.platform")
                id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            }
            
            kotlin {
                jvmToolchain {
                    languageVersion = JavaLanguageVersion.of(17)
                    vendor = JvmVendorSpec.JETBRAINS
                }
            }
            
            repositories {
                $repositories
            }
            
            dependencies {
                $dependencies
            }
            
            tasks {
                $tasks
            }
            
            $custom
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

    fun tasks(groupName: String): List<String> = build(ProjectInternal.TASKS_TASK).output.lines().run {
        val start = indexOfFirst { it.equals("$groupName tasks", ignoreCase = true) } + 2
        drop(start).takeWhile { !it.startsWith('-') }.dropLast(1).map { it.substringBefore(' ') }.filterNot { it.isEmpty() }
    }

    protected fun directory(path: String) = dir.resolve(path).createDirectories()

    protected fun emptyZipFile(path: String): File {
        val split = path.split('/')
        val directory = when {
            split.size > 1 -> directory(split.dropLast(1).joinToString("/"))
            else -> dir
        }
        val file = directory.resolve(split.last())
        val outputStream = FileOutputStream(file.toFile())
        val zipOutputStream = ZipOutputStream(outputStream)
        zipOutputStream.close()
        outputStream.close()
        return file.toFile()
    }

    protected fun file(path: String) =
        path.run { takeIf { startsWith('/') } ?: dir.resolve(this).pathString }.split('/').run { File(dropLast(1).joinToString("/")) to last() }
            .apply { if (!first.exists()) first.mkdirs() }.run { File(first, second) }.apply { createNewFile() }

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

    // TODO: Use Path.invariantSeparatorsPathString instead?
    fun adjustWindowsPath(s: String) = s.replace("\\", "/")

    protected fun assertContains(expected: String, actual: String) {
        // https://stackoverflow.com/questions/10934743/formatting-output-so-that-intellij-idea-shows-diffs-for-two-texts
        assertTrue(
            actual.contains(expected), """
            expected:<$expected> but was:<$actual>
            """.trimIndent()
        )
    }

    @Suppress("SameParameterValue")
    protected fun assertZipContent(zip: ZipFile, path: String, expectedContent: String) = assertEquals(expectedContent, fileText(zip, path))

    protected fun ZipFile.extract(path: String) = Files.createTempFile("gradle-test", "").apply {
        Files.newOutputStream(this, StandardOpenOption.DELETE_ON_CLOSE)
        Files.copy(getInputStream(getEntry(path)), this)
    }

    protected fun Path.toZip() = ZipFile(toFile())

    protected fun fileText(zipFile: ZipFile, path: String) = zipFile.getInputStream(zipFile.getEntry(path)).use {
        it.bufferedReader().use(BufferedReader::readText).replace("\r", "").trim()
    }

    protected fun collectPaths(zipFile: ZipFile) = zipFile.entries().toList().mapNotNull { it.name }.toSet()

    protected fun collectPaths(directory: Path) = collectPaths(directory.toFile())

    protected fun collectPaths(directory: File): Set<String> {
        assert(directory.exists())
        return directory.walkTopDown().filterNot { it.isDirectory }.map {
            adjustWindowsPath(it.canonicalPath.substring(directory.canonicalPath.length))
        }.toSet()
    }

    protected fun resource(path: String) = path.let {
        javaClass.classLoader.getResource(it)?.let { url ->
            Paths.get(url.toURI()).toAbsolutePath().toString().replace('\\', '/')
        }
    }

    protected fun resourceContent(path: String) = resource(path)?.let { File(it).readText() }

    // Methods can be simplified, when following tickets will be handled:
    // https://youtrack.jetbrains.com/issue/KT-24517
    // https://youtrack.jetbrains.com/issue/KTIJ-1001
    fun Path.xml(@Language("XML") content: String) = append(content)
    fun File.xml(@Language("XML") content: String) = append(content)

    fun File.groovy(@Language("Groovy") content: String) = append(content)

    fun File.java(@Language("Java") content: String) = append(content)

    fun File.kotlin(@Language("kotlin") content: String) = append(content)

    fun File.properties(@Language("Properties") content: String) = append(content)

    private fun Path.append(content: String) = apply {
        parent.createDirectories()
        createFile()
        appendText(content)
    }

    private fun File.append(content: String) = appendText(content.trimIndent() + "\n")
}
