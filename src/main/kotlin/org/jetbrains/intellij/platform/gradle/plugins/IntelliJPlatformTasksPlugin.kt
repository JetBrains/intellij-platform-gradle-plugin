// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_TASKS_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.utils.toIntelliJPlatformType

abstract class IntelliJPlatformTasksPlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_TASKS_ID) {

    override fun Project.configure() {
        with(plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        listOf(
            InitializeIntelliJPlatformPluginTask::register,
            SetupDependenciesTask::register,
            PatchPluginXmlTask::register,
            VerifyPluginConfigurationTask::register,
            PrintBundledPluginsTask::register,
            PrintProductsReleasesTask::register,
            ::configureProcessResourcesTask,
            ::configureJarTask,
            PrepareSandboxTask::register,
            BuildSearchableOptionsTask::register,
            JarSearchableOptionsTask::register,
            BuildPluginTask::register,
            SignPluginTask::register,
            RunPluginVerifierTask::register,
            VerifyPluginSignatureTask::register,
            VerifyPluginTask::register,
            PublishPluginTask::register,
            RunIdeTask::register,
            TestIdeTask::register,
        ).forEach { it.invoke(project) }

        with(tasks) {
            // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
            (TASKS - Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN).forEach {
                named(it) { dependsOn(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) }
            }
        }
    }

    private fun configureProcessResourcesTask(project: Project) =
        project.registerTask<ProcessResources>(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) {
            val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML)

            from(patchPluginXmlTaskProvider) {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                into("META-INF")
            }

            dependsOn(patchPluginXmlTaskProvider)
        }

    private fun configureJarTask(project: Project) =
        project.registerTask<Jar>(JavaPlugin.JAR_TASK_NAME, Tasks.INSTRUMENTED_JAR) {
            val initializeIntelliJPlatformPluginTaskProvider =
                project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
            val verifyPluginConfigurationTaskProvider =
                project.tasks.named<VerifyPluginConfigurationTask>(Tasks.VERIFY_PLUGIN_CONFIGURATION)
            val gradleVersionProvider = project.provider { project.gradle.gradleVersion }
            val versionProvider = project.provider { project.version }

            exclude("**/classpath.index")

            manifest.attributes(
                "Created-By" to gradleVersionProvider.map { "Gradle $it" },
                "Version" to versionProvider,
                "Build-JVM" to Jvm.current(),
                "Build-OS" to OperatingSystem.current(),
                "Build-Plugin" to IntelliJPluginConstants.PLUGIN_NAME,
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
