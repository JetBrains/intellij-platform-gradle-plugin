// Copyright 2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks.compaion

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.gradle.kotlin.dsl.withGroovyBuilder
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.Constants.GradleProperties
import org.jetbrains.intellij.platform.gradle.Constants.KOTLIN_GRADLE_PLUGIN_ID
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.InitializeIntelliJPlatformPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.registerTask
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType

class JarCompanion {
    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<Jar>(JavaPlugin.JAR_TASK_NAME, Tasks.INSTRUMENTED_JAR) {
                val extension = project.the<IntelliJPlatformExtension>()
                val productInfoProvider = project.provider {
                    extension.productInfo
                }
                val kotlinStdlibBundled = project.providers
                    .gradleProperty(GradleProperties.KOTLIN_STDLIB_DEFAULT_DEPENDENCY)
                    .map { it.toBoolean() }
                val kotlinVersionProvider = project.provider {
                    when {
                        project.pluginManager.hasPlugin(KOTLIN_GRADLE_PLUGIN_ID) ->
                            project.extensions
                                .getByName("kotlin")
                                .withGroovyBuilder { getProperty("coreLibrariesVersion") as String }

                        else -> ""
                    }
                }

                val initializeIntelliJPlatformPluginTaskProvider =
                    project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
                val gradleVersionProvider = project.provider { project.gradle.gradleVersion }
                val versionProvider = project.provider { project.version }

                exclude("**/classpath.index")

                archiveBaseName.convention(extension.projectName)

                manifest.attributes(
                    "Created-By" to gradleVersionProvider.map { "Gradle $it" },
                    "Version" to versionProvider,
                    "Build-JVM" to Jvm.current(),
                    "Build-OS" to OperatingSystem.current(),
                    "Build-Plugin" to Constants.PLUGIN_NAME,
                    "Build-Plugin-Version" to initializeIntelliJPlatformPluginTaskProvider.flatMap { it.pluginVersion },
                    "Platform-Type" to productInfoProvider.map { it.productCode.toIntelliJPlatformType() },
                    "Platform-Version" to productInfoProvider.map { it.version },
                    "Platform-Build" to productInfoProvider.map { it.buildNumber },
                    "Kotlin-Stdlib-Bundled" to kotlinStdlibBundled,
                    "Kotlin-Version" to kotlinVersionProvider,
                )

                dependsOn(initializeIntelliJPlatformPluginTaskProvider)
            }
    }
}
