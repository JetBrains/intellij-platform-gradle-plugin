// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test

class MultiModuleProjectRegressionTest : IntelliJPluginTestBase() {

    @Test
    fun `build plugin succeeds for composed multi-module project`() {
        settingsFile overwrite //language=kotlin
                """
                rootProject.name = "projectName"

                plugins {
                    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
                }

                include("core", "rider", "clion")
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginComposedModule(implementation(project(":core")))
                        pluginComposedModule(implementation(project(":rider")))
                        pluginComposedModule(implementation(project(":clion")))
                        jetbrainsRuntime()
                        testFramework(TestFrameworkType.Bundled)
                        pluginVerifier()
                        zipSigner()
                    }
                }
                """.trimIndent()

        pluginXml write //language=xml
                """
                <idea-plugin>
                    <name>Root Plugin</name>
                    <vendor>JetBrains</vendor>
                    <depends>com.intellij.modules.platform</depends>
                </idea-plugin>
                """.trimIndent()

        dir.resolve("core/build.gradle.kts") write //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.*

                val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type").orElse("$intellijPlatformType")
                val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version").orElse("$intellijPlatformVersion")

                version = "1.0.0"

                plugins {
                    id("org.jetbrains.kotlin.jvm")
                    id("org.jetbrains.intellij.platform")
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
                        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
                        testFramework(TestFrameworkType.Bundled)
                        pluginVerifier()
                        zipSigner()
                    }
                }

                intellijPlatform {
                    buildSearchableOptions = false
                    instrumentCode = false
                }
                """.trimIndent()

        dir.resolve("core/src/main/resources/META-INF/plugin.xml") write //language=xml
                """
                <idea-plugin>
                    <name>Core Plugin</name>
                    <vendor>JetBrains</vendor>
                    <depends>com.intellij.modules.platform</depends>
                </idea-plugin>
                """.trimIndent()

        val moduleBuildFile = //language=kotlin
                """
                import org.jetbrains.intellij.platform.gradle.*

                val intellijPlatformTypeProperty = providers.gradleProperty("intellijPlatform.type").orElse("$intellijPlatformType")
                val intellijPlatformVersionProperty = providers.gradleProperty("intellijPlatform.version").orElse("$intellijPlatformVersion")

                version = "1.0.0"

                plugins {
                    id("org.jetbrains.kotlin.jvm")
                    id("org.jetbrains.intellij.platform.module")
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
                        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
                        pluginModule(implementation(project(":core")))
                    }
                }

                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        dir.resolve("rider/build.gradle.kts") write moduleBuildFile
        dir.resolve("clion/build.gradle.kts") write moduleBuildFile

        build(Tasks.BUILD_PLUGIN)
    }
}
