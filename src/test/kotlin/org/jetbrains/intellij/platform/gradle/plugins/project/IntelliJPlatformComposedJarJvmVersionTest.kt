// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.testkit.runner.TaskOutcome
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.overwrite
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toPlatformJavaVersion
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Verifies JetBrains/intellij-platform-gradle-plugin#1772: the `org.gradle.jvm.version` attribute of the published
 * `intellijPlatformComposedJar` and `intellijPlatformDistribution` variants must match the Java version the project is
 * actually compiled with, for every supported way of configuring the Java level.
 *
 * Unlike a pure attribute check, this test compiles a real Java class and inspects the produced class-file version -
 * both the bytecode written to disk and the copy packaged into the composed Jar - so that the advertised variant
 * attribute cannot drift away from the actual compilation output.
 */
class IntelliJPlatformComposedJarJvmVersionTest : IntelliJPluginTestBase() {

    private val platformMajor
        get() = Version.parse(intellijPlatformBuildNumber).toPlatformJavaVersion().majorVersion.toInt()

    @Test
    fun `composed jar targets the platform Java version when nothing is configured`() {
        assertComposedJarJavaVersion(configuration = "", expectedJavaMajor = platformMajor)
    }

    @Test
    fun `java sourceCompatibility and targetCompatibility drive the composed jar Java version`() {
        assertComposedJarJavaVersion(
            configuration =
                """
                java {
                    sourceCompatibility = JavaVersion.VERSION_17
                    targetCompatibility = JavaVersion.VERSION_17
                }
                """.trimIndent(),
            expectedJavaMajor = 17,
        )
    }

    @Test
    fun `java sourceCompatibility alone drives the composed jar Java version`() {
        assertComposedJarJavaVersion(
            configuration =
                """
                java {
                    sourceCompatibility = JavaVersion.VERSION_17
                }
                """.trimIndent(),
            expectedJavaMajor = 17,
        )
    }

    @Test
    fun `toolchain languageVersion drives the composed jar Java version`() {
        assertComposedJarJavaVersion(
            configuration =
                """
                java {
                    toolchain.languageVersion = org.gradle.jvm.toolchain.JavaLanguageVersion.of(17)
                }
                """.trimIndent(),
            expectedJavaMajor = 17,
        )
    }

    @Test
    fun `compileJava options release drives the composed jar Java version`() {
        assertComposedJarJavaVersion(
            configuration =
                """
                tasks.named<org.gradle.api.tasks.compile.JavaCompile>("compileJava") {
                    options.release = 17
                }
                """.trimIndent(),
            expectedJavaMajor = 17,
        )
    }

    @Test
    fun `withType JavaCompile sourceCompatibility and targetCompatibility drive the composed jar Java version`() {
        assertComposedJarJavaVersion(
            configuration =
                """
                tasks.withType<org.gradle.api.tasks.compile.JavaCompile>().configureEach {
                    sourceCompatibility = "17"
                    targetCompatibility = "17"
                }
                """.trimIndent(),
            expectedJavaMajor = 17,
        )
    }

    @Test
    fun `targetCompatibility below sourceCompatibility fails compilation instead of silently changing source`() {
        writeJavaFile()
        buildFile overwrite buildScript(
            """
            java {
                toolchain.languageVersion = org.gradle.jvm.toolchain.JavaLanguageVersion.of(21)
            }
            tasks.named<org.gradle.api.tasks.compile.JavaCompile>("compileJava") {
                targetCompatibility = "17"
            }
            val compileJava = tasks.named<org.gradle.api.tasks.compile.JavaCompile>("compileJava").get()
            println("compileJava.sourceCompatibility=" + compileJava.sourceCompatibility)
            println("compileJava.targetCompatibility=" + compileJava.targetCompatibility)
            println("compileJava.release=" + compileJava.options.release.orNull)
            """.trimIndent(),
        )

        buildAndFail("compileJava") {
            assertContains(output, "compileJava.sourceCompatibility=21")
            assertContains(output, "compileJava.targetCompatibility=17")
            assertContains(output, "compileJava.release=null")
            assertContains(output, "source release 21 requires target release 21")
            assertEquals(TaskOutcome.FAILED, task(":compileJava")?.outcome)
        }
    }

    private fun assertComposedJarJavaVersion(configuration: String, expectedJavaMajor: Int) {
        writeJavaFile()
        buildFile overwrite buildScript(configuration)

        val composedJarPathMarker = "composedJar.path="
        var composedJarPath = ""

        build("composedJar") {
            assertContains(output, "platformMajor=$platformMajor")
            // The published variant attributes must match the compiled bytecode version.
            assertContains(output, "composedJar.jvmVersion=$expectedJavaMajor")
            assertContains(output, "distribution.jvmVersion=$expectedJavaMajor")

            composedJarPath = output.lineSequence()
                .first { it.startsWith(composedJarPathMarker) }
                .substringAfter(composedJarPathMarker)
                .trim()
        }

        // Java class-file major version = Java feature version + 44 (e.g. Java 17 -> 61, Java 21 -> 65).
        val expectedClassFileMajor = expectedJavaMajor + 44

        // 1. The bytecode compiled to disk.
        val compiledClass = dir.resolve("build/classes/java/main/App.class")
        assertEquals(expectedClassFileMajor, classFileMajor(compiledClass.readBytes()))

        // 2. The bytecode packaged into the composed Jar.
        Path(composedJarPath).toZip().use { zip ->
            val bytes = zip.getInputStream(zip.getEntry("App.class")).use { it.readBytes() }
            assertEquals(expectedClassFileMajor, classFileMajor(bytes))
        }
    }

    // Class file layout: magic (4 bytes), minor version (2 bytes), major version (2 bytes, big-endian).
    private fun classFileMajor(bytes: ByteArray) = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)

    private fun buildScript(additionalConfiguration: String) = //language=kotlin
        """
        import org.gradle.api.JavaVersion
        import org.gradle.api.attributes.java.TargetJvmVersion
        import org.gradle.jvm.tasks.Jar

        version = "1.0.0"

        plugins {
            id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            id("org.jetbrains.intellij.platform")
        }

        repositories {
            mavenCentral()

            intellijPlatform {
                defaultRepositories()
            }
        }

        dependencies {
            intellijPlatform {
                create("$intellijPlatformType", "$intellijPlatformVersion")
            }
        }

        intellijPlatform {
            buildSearchableOptions = false
            instrumentCode = false

            caching {
                ides {
                    enabled = true
                    path = File("${idesCacheDir.invariantSeparatorsPathString}")
                }
            }
        }

        $additionalConfiguration

        val composedJarConfiguration = configurations.named("intellijPlatformComposedJar")
        val distributionConfiguration = configurations.named("intellijPlatformDistribution")
        val composedJarTask = tasks.named<Jar>("composedJar")

        // Query outgoing JVM attributes while the build script is still being evaluated.
        println("platformMajor=$platformMajor")
        println("composedJar.jvmVersion=" + composedJarConfiguration.get().attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE))
        println("distribution.jvmVersion=" + distributionConfiguration.get().attributes.getAttribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE))
        gradle.projectsEvaluated {
            println("composedJar.path=" + composedJarTask.get().archiveFile.get().asFile.absolutePath)
        }
        """.trimIndent()
}
