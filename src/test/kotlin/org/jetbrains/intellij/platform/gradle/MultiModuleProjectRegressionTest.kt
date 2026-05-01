// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle

import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MultiModuleProjectRegressionTest : IntelliJPluginTestBase() {

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
                        create(intellijPlatformTypeProperty, intellijPlatformVersionProperty)
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
}
