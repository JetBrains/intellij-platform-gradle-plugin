// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.gradle.util.GradleVersion
import org.jetbrains.intellij.platform.gradle.Constants.Sandbox
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.toPlatformJavaVersion
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiModuleProjectRegressionTest : IntelliJPluginTestBase() {

    private val expectedPlatformJavaVersion
        get() = Version.parse(intellijPlatformBuildNumber).toPlatformJavaVersion()

    @Test
    fun `module default compile target follows root request without resolving inherited platform path`() {
        settingsFile write //language=kotlin
                """
                include("backend")
                """.trimIndent()

        buildFile write //language=kotlin
                """
                configurations.named("intellijPlatformDependency") {
                    incoming.beforeResolve {
                        throw org.gradle.api.GradleException("root intellijPlatformDependency was resolved during configuration")
                    }
                }
                """.trimIndent()

        dir.resolve("backend/build.gradle.kts") write //language=kotlin
                """
                import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
                
                plugins {
                    id("org.jetbrains.kotlin.jvm")
                    id("org.jetbrains.intellij.platform.module")
                }
                
                val compileKotlinJvmTarget = tasks.named<KotlinJvmCompile>("compileKotlin").flatMap {
                    it.compilerOptions.jvmTarget
                }
                
                println("backend.compileKotlin.jvmTarget=" + compileKotlinJvmTarget.orNull)
                """.trimIndent()

        build(":backend:help") {
            assertContains(output, "backend.compileKotlin.jvmTarget=JVM_${expectedPlatformJavaVersion.majorVersion}")
        }
    }

    @Test
    fun `module local IntelliJ Platform dependency overrides inherited root local dependency before variant attributes are queried`() {
        settingsFile write //language=kotlin
                """
                include("clion")
                """.trimIndent()

        val clionIdePath = dir.resolve("clion-ide")
        clionIdePath.resolve("product-info.json") write //language=json
                """
                {
                    "name": "CLion",
                    "version": "$intellijPlatformVersion",
                    "buildNumber": "$intellijPlatformBuildNumber",
                    "productCode": "CL"
                }
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginComposedModule(runtimeOnly(project(":clion")))
                    }
                }
                """.trimIndent()

        dir.resolve("clion/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.module")
                }
                
                repositories {
                    mavenCentral()
                
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        local("${clionIdePath.invariantSeparatorsPathString}")
                    }
                }
                """.trimIndent()

        val expectedProjectDependency = when {
            GradleVersion.version(gradleVersion) >= GradleVersion.version("9.6.0") -> "project ':clion'"
            else -> "project :clion"
        }

        build("dependencies", "--configuration", "intellijPlatformRuntimeClasspath") {
            assertContains(output, expectedProjectDependency)
        }
    }

    @Test
    fun `build plugin composes pluginComposedModule dependencies into the main jar`() {
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
        dir.resolve("core/src/main/java/CoreFeature.java") write //language=java
                """
                class CoreFeature {}
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
        dir.resolve("rider/src/main/java/RiderFeature.java") write //language=java
                """
                class RiderFeature {}
                """.trimIndent()
        dir.resolve("clion/src/main/java/ClionFeature.java") write //language=java
                """
                class ClionFeature {}
                """.trimIndent()

        build(Tasks.BUILD_PLUGIN)

        buildDirectory.resolve("distributions/projectName-1.0.0.zip").toZip().use { zip ->
            assertFalse(collectPaths(zip).any { "/lib/modules/" in it }, "Composed modules must not be packaged to lib/modules")

            zip.extract("projectName/lib/projectName-1.0.0.jar").toZip().use { jar ->
                val jarPaths = collectPaths(jar)

                assertTrue("CoreFeature.class" in jarPaths)
                assertTrue("RiderFeature.class" in jarPaths)
                assertTrue("ClionFeature.class" in jarPaths)
            }
        }
    }

    @Test
    fun `split mode run sandbox installs plugin dependencies declared by plugin modules`() {
        settingsFile write //language=kotlin
                """
                include("backend")
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginModule(implementation(project(":backend")))
                    }
                }
                
                intellijPlatform {
                    pluginInstallationTarget = org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.PluginInstallationTarget.BOTH
                }
                """.trimIndent()

        dir.resolve("backend/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.module")
                }
                
                repositories {
                    mavenCentral()
                
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        plugin("org.jetbrains.postfixCompletion", "0.8-beta")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        dir.resolve("backend/src/main/java/BackendFeature.java") write //language=java
                """
                class BackendFeature {}
                """.trimIndent()

        build("prepareSandbox_${Tasks.RUN_IDE_BACKEND}")

        val backendPluginsDirectory = sandboxDirectory
            .resolve("projectName")
            .resolve("$intellijPlatformType-$intellijPlatformVersion")
            .resolve("${Sandbox.PLUGINS}_${Tasks.RUN_IDE_BACKEND}")

        assertExists(backendPluginsDirectory.resolve("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        assertExists(backendPluginsDirectory.resolve("projectName/lib/projectName-1.0.0.jar"))
        assertFalse(backendPluginsDirectory.resolve("frontend").toFile().exists())
    }

    @Test
    fun `split mode run sandboxes keep backend and frontend module plugin dependencies separated`() {
        settingsFile write //language=kotlin
                """
                include("backend", "frontend")
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginModule(implementation(project(":backend")))
                        pluginModule(implementation(project(":frontend")))
                    }
                }
                
                intellijPlatform {
                    pluginInstallationTarget = org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.PluginInstallationTarget.BOTH
                }
                """.trimIndent()

        dir.resolve("backend/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.module")
                }
                
                repositories {
                    mavenCentral()
                
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        plugin("org.jetbrains.postfixCompletion", "0.8-beta")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        dir.resolve("frontend/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.module")
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
                    instrumentCode = false
                    pluginInstallationTarget = org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.PluginInstallationTarget.FRONTEND
                }
                """.trimIndent()

        dir.resolve("backend/src/main/java/BackendFeature.java") write //language=java
                """
                class BackendFeature {}
                """.trimIndent()
        dir.resolve("frontend/src/main/java/FrontendFeature.java") write //language=java
                """
                class FrontendFeature {}
                """.trimIndent()

        build("prepareSandbox_${Tasks.RUN_IDE_BACKEND}", "prepareSandbox_${Tasks.RUN_IDE_FRONTEND}")

        val sandbox = sandboxDirectory
            .resolve("projectName")
            .resolve("$intellijPlatformType-$intellijPlatformVersion")
        val backendPluginsDirectory = sandbox.resolve("${Sandbox.PLUGINS}_${Tasks.RUN_IDE_BACKEND}")
        val frontendPluginsDirectory = sandbox.resolve("${Sandbox.PLUGINS}_${Tasks.RUN_IDE_FRONTEND}").resolve("frontend")

        assertExists(backendPluginsDirectory.resolve("projectName/lib/projectName-1.0.0.jar"))
        assertExists(backendPluginsDirectory.resolve("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        assertFalse(backendPluginsDirectory.resolve("frontend").toFile().exists())

        assertExists(frontendPluginsDirectory.resolve("projectName/lib/projectName-1.0.0.jar"))
        assertFalse(frontendPluginsDirectory.resolve("org.jetbrains.postfixCompletion-0.8-beta.jar").toFile().exists())
        assertFalse(frontendPluginsDirectory.parent.resolve("projectName").toFile().exists())
        assertFalse(frontendPluginsDirectory.parent.resolve("org.jetbrains.postfixCompletion-0.8-beta.jar").toFile().exists())
    }

    @Test
    fun `split mode frontend run sandbox infers frontend module plugin dependencies from bundled frontend module`() {
        settingsFile write //language=kotlin
                """
                include("frontend")
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginModule(implementation(project(":frontend")))
                    }
                }
                
                intellijPlatform {
                    pluginInstallationTarget = org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.PluginInstallationTarget.BOTH
                }
                """.trimIndent()

        dir.resolve("frontend/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.module")
                }
                
                repositories {
                    mavenCentral()
                
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        bundledModule("intellij.platform.frontend")
                        plugin("org.jetbrains.postfixCompletion", "0.8-beta")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        dir.resolve("frontend/src/main/java/FrontendFeature.java") write //language=java
                """
                class FrontendFeature {}
                """.trimIndent()

        build("prepareSandbox_${Tasks.RUN_IDE_FRONTEND}")

        val frontendPluginsDirectory = sandboxDirectory
            .resolve("projectName")
            .resolve("$intellijPlatformType-$intellijPlatformVersion")
            .resolve("${Sandbox.PLUGINS}_${Tasks.RUN_IDE_FRONTEND}")
            .resolve("frontend")

        assertExists(frontendPluginsDirectory.resolve("projectName/lib/projectName-1.0.0.jar"))
        assertExists(frontendPluginsDirectory.resolve("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        assertFalse(frontendPluginsDirectory.parent.resolve("projectName").toFile().exists())
        assertFalse(frontendPluginsDirectory.parent.resolve("org.jetbrains.postfixCompletion-0.8-beta.jar").toFile().exists())
    }

    @Test
    fun `split mode frontend run sandbox inherits module plugin dependencies from root frontend target`() {
        settingsFile write //language=kotlin
                """
                include("frontend")
                """.trimIndent()

        buildFile write //language=kotlin
                """
                dependencies {
                    intellijPlatform {
                        pluginModule(implementation(project(":frontend")))
                    }
                }
                
                intellijPlatform {
                    pluginInstallationTarget = org.jetbrains.intellij.platform.gradle.tasks.aware.SplitModeAware.PluginInstallationTarget.FRONTEND
                }
                """.trimIndent()

        dir.resolve("frontend/build.gradle.kts") write //language=kotlin
                """
                plugins {
                    id("org.jetbrains.intellij.platform.module")
                }
                
                repositories {
                    mavenCentral()
                
                    intellijPlatform {
                        defaultRepositories()
                    }
                }
                
                dependencies {
                    intellijPlatform {
                        plugin("org.jetbrains.postfixCompletion", "0.8-beta")
                    }
                }
                
                intellijPlatform {
                    instrumentCode = false
                }
                """.trimIndent()

        dir.resolve("frontend/src/main/java/FrontendFeature.java") write //language=java
                """
                class FrontendFeature {}
                """.trimIndent()

        build("prepareSandbox_${Tasks.RUN_IDE_FRONTEND}")

        val frontendPluginsDirectory = sandboxDirectory
            .resolve("projectName")
            .resolve("$intellijPlatformType-$intellijPlatformVersion")
            .resolve("${Sandbox.PLUGINS}_${Tasks.RUN_IDE_FRONTEND}")
            .resolve("frontend")

        assertExists(frontendPluginsDirectory.resolve("projectName/lib/projectName-1.0.0.jar"))
        assertExists(frontendPluginsDirectory.resolve("org.jetbrains.postfixCompletion-0.8-beta.jar"))
        assertFalse(frontendPluginsDirectory.parent.resolve("projectName").toFile().exists())
        assertFalse(frontendPluginsDirectory.parent.resolve("org.jetbrains.postfixCompletion-0.8-beta.jar").toFile().exists())
    }
}
