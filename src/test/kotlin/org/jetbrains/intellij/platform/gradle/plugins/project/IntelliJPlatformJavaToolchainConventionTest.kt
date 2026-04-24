// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.overwrite
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toPlatformJavaVersion
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertContains

class IntelliJPlatformJavaToolchainConventionTest : IntelliJPluginTestBase() {

    private val inspectTask = "help"

    private val expectedPlatformJavaVersion
        get() = Version.parse(intellijPlatformBuildNumber).toPlatformJavaVersion()

    @Test
    fun `default Java toolchain follows target platform for root plugin`() {
        buildFile overwrite buildScript("org.jetbrains.intellij.platform")

        build(inspectTask) {
            assertContains(output, "compileJava.release=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "compileKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
            assertContains(output, "compileTestKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
        }
    }

    @Test
    fun `default Java toolchain follows target platform for module plugin`() {
        buildFile overwrite buildScript("org.jetbrains.intellij.platform.module")

        build(inspectTask) {
            assertContains(output, "compileJava.release=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "compileKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
            assertContains(output, "compileTestKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
        }
    }

    @Test
    fun `early Java extension reads do not break IntelliJ compile conventions`() {
        buildFile overwrite buildScript(
            pluginId = "org.jetbrains.intellij.platform",
            preDependenciesConfiguration =
                """
                val earlyJava = the<JavaPluginExtension>()
                println("early.targetCompatibility=" + earlyJava.targetCompatibility)
                """.trimIndent(),
        )

        build(inspectTask) {
            assertContains(output, "early.targetCompatibility=")
            assertContains(output, "compileJava.release=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "compileKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
            assertContains(output, "compileTestKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
        }
    }

    @Test
    fun `early Java extension reads do not block explicit Java toolchain override`() {
        buildFile overwrite buildScript(
            pluginId = "org.jetbrains.intellij.platform",
            preDependenciesConfiguration =
                """
                val earlyJava = the<JavaPluginExtension>()
                println("early.targetCompatibility=" + earlyJava.targetCompatibility)
                """.trimIndent(),
            additionalConfiguration =
                """
                java {
                    toolchain.languageVersion = org.gradle.jvm.toolchain.JavaLanguageVersion.of(17)
                }
                """.trimIndent(),
        )

        build(inspectTask) {
            assertContains(output, "early.targetCompatibility=")
            assertContains(output, "javaToolchain=17")
            assertContains(output, "compileJava.release=17")
            assertContains(output, "compileKotlin.jvmTarget=JVM_17")
            assertContains(output, "compileTestKotlin.jvmTarget=JVM_17")
        }
    }

    @Test
    fun `explicit Kotlin jvmToolchain overrides platform convention`() {
        buildFile overwrite buildScript(
            pluginId = "org.jetbrains.intellij.platform",
            additionalConfiguration =
                """
                kotlin {
                    jvmToolchain(17)
                }
                """.trimIndent(),
        )

        build(inspectTask) {
            assertContains(output, "javaToolchain=17")
            assertContains(output, "compileJava.release=17")
            assertContains(output, "compileKotlin.jvmTarget=JVM_17")
            assertContains(output, "compileTestKotlin.jvmTarget=JVM_17")
        }
    }

    @Test
    fun `early Java extension reads do not block explicit Kotlin jvmToolchain override`() {
        buildFile overwrite buildScript(
            pluginId = "org.jetbrains.intellij.platform",
            preDependenciesConfiguration =
                """
                val earlyJava = the<JavaPluginExtension>()
                println("early.targetCompatibility=" + earlyJava.targetCompatibility)
                """.trimIndent(),
            additionalConfiguration =
                """
                kotlin {
                    jvmToolchain(17)
                }
                """.trimIndent(),
        )

        build(inspectTask) {
            assertContains(output, "early.targetCompatibility=")
            assertContains(output, "javaToolchain=17")
            assertContains(output, "compileJava.release=17")
            assertContains(output, "compileKotlin.jvmTarget=JVM_17")
            assertContains(output, "compileTestKotlin.jvmTarget=JVM_17")
        }
    }

    @Test
    fun `explicit compileJava release overrides platform convention`() {
        buildFile overwrite buildScript(
            pluginId = "org.jetbrains.intellij.platform",
            additionalConfiguration =
                """
                tasks.named<JavaCompile>("compileJava") {
                    options.release = 17
                }
                """.trimIndent(),
        )

        build(inspectTask) {
            assertContains(output, "compileJava.release=17")
            assertContains(output, "compileKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
            assertContains(output, "compileTestKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
        }
    }

    @Test
    fun `explicit Kotlin jvmTarget overrides platform convention`() {
        buildFile overwrite buildScript(
            pluginId = "org.jetbrains.intellij.platform",
            additionalConfiguration =
                """
                tasks.named<KotlinJvmCompile>("compileKotlin") {
                    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
                }
                tasks.named<KotlinJvmCompile>("compileTestKotlin") {
                    compilerOptions.jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
                }
                """.trimIndent(),
        )

        build(inspectTask) {
            assertContains(output, "compileJava.release=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "compileKotlin.jvmTarget=JVM_17")
            assertContains(output, "compileTestKotlin.jvmTarget=JVM_17")
        }
    }

    @Test
    fun `explicit Java toolchain overrides platform convention`() {
        buildFile overwrite buildScript(
            pluginId = "org.jetbrains.intellij.platform",
            additionalConfiguration =
                """
                java {
                    toolchain.languageVersion = org.gradle.jvm.toolchain.JavaLanguageVersion.of(17)
                }
                """.trimIndent(),
        )

        build(inspectTask) {
            assertContains(output, "javaToolchain=17")
            assertContains(output, "compileJava.release=17")
            assertContains(output, "compileKotlin.jvmTarget=JVM_17")
            assertContains(output, "compileTestKotlin.jvmTarget=JVM_17")
        }
    }

    private fun buildScript(
        pluginId: String,
        preDependenciesConfiguration: String = "",
        additionalConfiguration: String = "",
    ) = //language=kotlin
        """
        import org.gradle.api.plugins.JavaPluginExtension
        import org.gradle.api.tasks.compile.JavaCompile
        import org.gradle.kotlin.dsl.the
        
        import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
        
        version = "1.0.0"
        
        plugins {
            id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            id("$pluginId")
        }

        $preDependenciesConfiguration
        
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
        
        val java = the<JavaPluginExtension>()
        val compileJavaRelease = tasks.named<JavaCompile>("compileJava").flatMap { it.options.release }
        val compileKotlinJvmTarget = tasks.named<KotlinJvmCompile>("compileKotlin").flatMap { it.compilerOptions.jvmTarget }
        val compileTestKotlinJvmTarget = tasks.named<KotlinJvmCompile>("compileTestKotlin").flatMap { it.compilerOptions.jvmTarget }
        
        println("javaToolchain=" + java.toolchain.languageVersion.orNull)
        println("compileJava.release=" + compileJavaRelease.orNull)
        println("compileKotlin.jvmTarget=" + compileKotlinJvmTarget.orNull)
        println("compileTestKotlin.jvmTarget=" + compileTestKotlinJvmTarget.orNull)
        """.trimIndent()

    private fun expectedJvmTarget(javaVersion: JavaVersion) = when (javaVersion) {
        JavaVersion.VERSION_1_8 -> "JVM_1_8"
        else -> "JVM_${javaVersion.majorVersion}"
    }
}
