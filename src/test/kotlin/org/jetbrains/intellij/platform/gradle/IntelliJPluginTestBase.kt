// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.api.internal.project.ProjectInternal
import org.gradle.kotlin.dsl.support.normaliseLineSeparators
import java.io.FileOutputStream
import java.nio.file.*
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import kotlin.io.path.*
import kotlin.test.BeforeTest
import kotlin.test.assertEquals

abstract class IntelliJPluginTestBase : IntelliJPlatformTestBase() {

    val randomTaskName = "task_" + (1..1000).random()

    @BeforeTest
    override fun setup() {
        super.setup()

        if (gradleScan) {
            settingsFile write //language=kotlin
                    """                    
                    plugins {
                        id("com.gradle.develocity") version "3.17.5"
                    }
                    
                    val isCI = (System.getenv("CI") ?: "false").toBoolean()
                    
                    develocity {
                        server = "https://ge.jetbrains.com"
                    
                        buildScan {
                            termsOfUseAgree = "yes"
                            publishing.onlyIf { isCI }
                        }
                    }
                    """.trimIndent()
        }

        settingsFile write //language=kotlin
                """
                rootProject.name = "projectName"
                
                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
                }
                """.trimIndent()

        buildFile write //language=kotlin
                """
                import java.util.*
                import org.jetbrains.intellij.platform.gradle.*
                import org.jetbrains.intellij.platform.gradle.models.*
                import org.jetbrains.intellij.platform.gradle.tasks.*
                import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.*
                import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask.*
                import org.jetbrains.kotlin.gradle.dsl.*
                
                version = "1.0.0"
                
                plugins {
                    id("java")
                    id("org.jetbrains.intellij.platform")
                    id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
                }
                
                kotlin {
                    jvmToolchain(21)
                }
                
                repositories {
                    mavenCentral()
                    
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        val useInstaller = providers.gradleProperty("intellijPlatform.useInstaller").orElse("true").map { it.toBoolean() }
                        val type = providers.gradleProperty("intellijPlatform.type").orElse("$intellijPlatformType")
                        val version = providers.gradleProperty("intellijPlatform.version").orElse("$intellijPlatformVersion")
                        
                        create(type, version) { this.useInstaller.set(useInstaller) }
                    }
                }
                            
                intellijPlatform {
                    buildSearchableOptions = false
                    instrumentCode = false
                    
                    caching {
                        ides {
                            enabled = false
                            path = File("${gradleHome.invariantSeparatorsPathString}", "ides")
                        }
                    }
                }
                
                tasks {
                    wrapper {
                        gradleVersion = "$gradleVersion"
                    }
                }
                """.trimIndent()

        gradleProperties write //language=properties
                """
                kotlin.stdlib.default.dependency = false
                org.jetbrains.intellij.platform.selfUpdateCheck = false
                """.trimIndent()
    }

    fun writeTestFile() = dir.resolve("src/test/java/AppTest.java") write //language=java
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
        FileOutputStream(file.toFile()).use { outputStream ->
            ZipOutputStream(outputStream).close()
        }
        return file
    }

    protected fun writeJavaFile() = dir.resolve("src/main/java/App.java").ensureExists() write //language=java
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

    protected fun writeKotlinFile() = dir.resolve("src/main/kotlin/App.kt").ensureExists() write //language=kotlin
            """
            object App {
                @JvmStatic
                fun main(args: Array<String>) {
                    println(args.joinToString())
                }
            }
            """.trimIndent()

    protected fun writeKotlinUIFile() = dir.resolve("src/main/kotlin/pack/AppKt.kt").ensureExists() write //language=kotlin
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

    @Suppress("SameParameterValue")
    protected fun assertZipContent(zip: ZipFile, path: String, expectedContent: String) = assertEquals(expectedContent, fileText(zip, path))

    protected fun ZipFile.extract(path: String) = Files.createTempFile("gradle-test", "").apply {
        getInputStream(getEntry(path)).use { inputStream ->
            Files.newOutputStream(this, StandardOpenOption.DELETE_ON_CLOSE)
            Files.copy(inputStream, this, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    protected fun Path.toZip() = ZipFile(toFile())

    protected fun fileText(zipFile: ZipFile, path: String) = zipFile.getInputStream(zipFile.getEntry(path)).use { inputStream ->
        inputStream.bufferedReader().use { it.readText() }.normaliseLineSeparators().trim()
    }

    protected fun collectPaths(zipFile: ZipFile) = zipFile.entries().toList().mapNotNull { it.name }.toSet()

    @OptIn(ExperimentalPathApi::class)
    protected fun collectPaths(directory: Path) = directory
        .walk()
        .filterNot { it.isDirectory() }
        .map { it.relativeTo(directory).invariantSeparatorsPathString }
        .toSet()

    protected fun resourceUrl(path: String) = javaClass.classLoader.getResource(path)

    protected fun resource(path: String) = resourceUrl(path)?.let { url ->
        Paths.get(url.toURI()).invariantSeparatorsPathString
    }

    protected fun resourceContent(path: String) = resource(path)?.let { Path(it).readText() }
}
