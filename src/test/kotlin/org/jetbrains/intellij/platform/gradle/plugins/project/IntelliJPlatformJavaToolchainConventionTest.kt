// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins.project

import org.gradle.api.JavaVersion
import org.jetbrains.intellij.platform.gradle.IntelliJPluginTestBase
import org.jetbrains.intellij.platform.gradle.buildFile
import org.jetbrains.intellij.platform.gradle.overwrite
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toPlatformJavaVersion
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
            assertContains(output, "javaToolchain=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "sourceCompatibility=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "targetCompatibility=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "compileKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
            assertContains(output, "compileTestKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
        }
    }

    @Test
    fun `default Java toolchain follows target platform for module plugin`() {
        buildFile overwrite buildScript("org.jetbrains.intellij.platform.module")

        build(inspectTask) {
            assertContains(output, "javaToolchain=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "sourceCompatibility=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "targetCompatibility=${expectedPlatformJavaVersion.majorVersion}")
            assertContains(output, "compileKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
            assertContains(output, "compileTestKotlin.jvmTarget=${expectedJvmTarget(expectedPlatformJavaVersion)}")
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
            assertContains(output, "sourceCompatibility=17")
            assertContains(output, "targetCompatibility=17")
            assertContains(output, "compileKotlin.jvmTarget=JVM_17")
            assertContains(output, "compileTestKotlin.jvmTarget=JVM_17")
        }
    }

    private fun buildScript(
        pluginId: String,
        additionalConfiguration: String = "",
    ) = //language=kotlin
        """
        import org.gradle.api.plugins.JavaPluginExtension
        import org.gradle.kotlin.dsl.the

        import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

        version = "1.0.0"

        plugins {
            id("org.jetbrains.kotlin.jvm") version "$kotlinPluginVersion"
            id("$pluginId")
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

        $additionalConfiguration

        val java = the<JavaPluginExtension>()
        val compileKotlinJvmTarget = tasks.named<KotlinJvmCompile>("compileKotlin").flatMap { it.compilerOptions.jvmTarget }
        val compileTestKotlinJvmTarget = tasks.named<KotlinJvmCompile>("compileTestKotlin").flatMap { it.compilerOptions.jvmTarget }

        println("javaToolchain=" + java.toolchain.languageVersion.orNull)
        println("sourceCompatibility=" + java.sourceCompatibility)
        println("targetCompatibility=" + java.targetCompatibility)
        println("compileKotlin.jvmTarget=" + compileKotlinJvmTarget.orNull)
        println("compileTestKotlin.jvmTarget=" + compileTestKotlinJvmTarget.orNull)
        """.trimIndent()

    private fun expectedJvmTarget(javaVersion: JavaVersion) = when (javaVersion) {
        JavaVersion.VERSION_1_8 -> "JVM_1_8"
        else -> "JVM_${javaVersion.majorVersion}"
    }
}
