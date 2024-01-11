// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.GradleException
import org.gradle.api.internal.project.ProjectInternal
import org.intellij.lang.annotations.Language
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.DEFAULT_INTELLIJ_PLUGINS_REPOSITORY
import java.io.BufferedReader
import java.io.FileOutputStream
import java.nio.file.*
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    val gradleProperties get() = dir.resolve("gradle.properties")
    val buildFile get() = dir.resolve("build.gradle.kts")
    val settingsFile get() = dir.resolve("settings.gradle.kts")
    val pluginXml get() = dir.resolve("src/main/resources/META-INF/plugin.xml")
    val buildDirectory get() = dir.resolve("build")

    @BeforeTest
    override fun setup() {
        super.setup()

        if (gradleScan) {
            settingsFile.kotlin(
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
            
            plugins {
                id("org.gradle.toolchains.foojay-resolver-convention") version "0.7.0"
            }
            """.trimIndent()
        )

        buildFile.kotlin(
            """
            import java.util.*
            import org.jetbrains.intellij.platform.gradle.*
            import org.jetbrains.intellij.platform.gradle.tasks.*
            import org.jetbrains.intellij.platform.gradle.tasks.RunPluginVerifierTask.*
            
            version = "1.0.0"
            
            plugins {
                id("java")
                id("org.jetbrains.intellij.platform")
                id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            }
            
            kotlin {
                jvmToolchain {
                    languageVersion = JavaLanguageVersion.of(17)
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
                        
            intellijPlatform {
                buildSearchableOptions = false
            }
            """.trimIndent()
        )

        if (gradleVersion.toVersion() >= "8.4".toVersion()) {
            buildFile.kotlin(
                """
                kotlin {
                    jvmToolchain {
                        vendor = JvmVendorSpec.JETBRAINS
                    }
                }
                """.trimIndent()
            )
        }

        gradleProperties.properties(
            """
            kotlin.stdlib.default.dependency = false
            org.jetbrains.intellij.platform.buildFeature.selfUpdateCheck = false
            systemProp.org.gradle.unsafe.kotlin.assignment = true
            """.trimIndent()
        )
    }

    fun writeTestFile() = dir.resolve("src/test/java/AppTest.java").java(
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

    protected fun emptyZipFile(path: String): Path {
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
        return file
    }

    protected fun writeJavaFile() = dir.resolve("src/main/java/App.java").ensureFileExists().java(
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

    protected fun writeKotlinFile() = dir.resolve("src/main/kotlin/App.kt").ensureFileExists().kotlin(
        """
        object App {
            @JvmStatic
            fun main(args: Array<String>) {
                println(args.joinToString())
            }
        }
        """.trimIndent()
    )

    protected fun writeKotlinUIFile() = dir.resolve("src/main/kotlin/pack/AppKt.kt").ensureFileExists().kotlin(
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

    protected fun assertContains(expected: String, actual: String) {
        // https://stackoverflow.com/questions/10934743/formatting-output-so-that-intellij-idea-shows-diffs-for-two-texts
        assertTrue(
            actual.contains(expected),
            """
            expected:<$expected> but was:<$actual>
            """.trimIndent()
        )
    }

    @Suppress("SameParameterValue")
    protected fun assertZipContent(zip: ZipFile, path: String, expectedContent: String) = assertEquals(expectedContent, fileText(zip, path))

    protected fun ZipFile.extract(path: String) = Files.createTempFile("gradle-test", "").apply {
        Files.newOutputStream(this, StandardOpenOption.DELETE_ON_CLOSE)
        Files.copy(getInputStream(getEntry(path)), this, StandardCopyOption.REPLACE_EXISTING)
    }

    protected fun Path.toZip() = ZipFile(toFile())

    protected fun fileText(zipFile: ZipFile, path: String) = zipFile.getInputStream(zipFile.getEntry(path)).use {
        it.bufferedReader().use(BufferedReader::readText).replace("\r", "").trim()
    }

    protected fun collectPaths(zipFile: ZipFile) = zipFile.entries().toList().mapNotNull { it.name }.toSet()

    @OptIn(ExperimentalPathApi::class)
    protected fun collectPaths(directory: Path) = directory
        .walk()
        .filterNot { it.isDirectory() }
        .map { it.relativeTo(directory).invariantSeparatorsPathString }
        .toSet()

    protected fun resource(path: String) = path.let {
        javaClass.classLoader.getResource(it)?.let { url ->
            Paths.get(url.toURI()).invariantSeparatorsPathString
        }
    }

    protected fun resourceContent(path: String) = resource(path)?.let { Path(it).readText() }

    // Methods can be simplified, when following tickets will be handled:
    // https://youtrack.jetbrains.com/issue/KT-24517
    // https://youtrack.jetbrains.com/issue/KTIJ-1001
    fun Path.xml(@Language("XML") content: String) = append(content)

    fun Path.java(@Language("Java") content: String) = append(content)

    fun Path.kotlin(@Language("kotlin") content: String) = append(content)

    fun Path.properties(@Language("Properties") content: String) = append(content)

    private fun Path.append(content: String) = ensureFileExists().also { appendText(content + "\n") }
}
