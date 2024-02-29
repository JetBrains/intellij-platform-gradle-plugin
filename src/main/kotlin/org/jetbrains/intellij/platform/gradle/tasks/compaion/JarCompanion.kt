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
import org.jetbrains.intellij.platform.gradle.Constants
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.tasks.InitializeIntelliJPlatformPluginTask
import org.jetbrains.intellij.platform.gradle.tasks.Registrable
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginProjectConfigurationTask
import org.jetbrains.intellij.platform.gradle.tasks.registerTask
import org.jetbrains.intellij.platform.gradle.toIntelliJPlatformType

class JarCompanion {
    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<Jar>(JavaPlugin.JAR_TASK_NAME, Constants.Tasks.INSTRUMENTED_JAR) {
                val extension = project.the<IntelliJPlatformExtension>()
                val initializeIntelliJPlatformPluginTaskProvider =
                    project.tasks.named<InitializeIntelliJPlatformPluginTask>(Constants.Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
                val verifyPluginConfigurationTaskProvider =
                    project.tasks.named<VerifyPluginProjectConfigurationTask>(Constants.Tasks.VERIFY_PLUGIN_PROJECT_CONFIGURATION)
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
                    "Platform-Type" to verifyPluginConfigurationTaskProvider.map { it.productInfo.productCode.toIntelliJPlatformType() },
                    "Platform-Version" to verifyPluginConfigurationTaskProvider.map { it.productInfo.version },
                    "Platform-Build" to verifyPluginConfigurationTaskProvider.map { it.productInfo.buildNumber },
                    "Kotlin-Stdlib-Bundled" to verifyPluginConfigurationTaskProvider.flatMap { it.kotlinStdlibDefaultDependency },
                    "Kotlin-Version" to verifyPluginConfigurationTaskProvider.flatMap { it.kotlinVersion },
                )

                dependsOn(initializeIntelliJPlatformPluginTaskProvider)
                dependsOn(verifyPluginConfigurationTaskProvider)
            }
    }
}
