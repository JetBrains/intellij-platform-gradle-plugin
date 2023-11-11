// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.apply
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_TASKS_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.tasks.*

abstract class IntelliJPlatformTasksPlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_TASKS_ID) {

    override fun Project.configure() {
        with(plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        listOf(
            InitializeIntelliJPlatformPluginTask::register,
            SetupDependenciesTask::register,
            PatchPluginXmlTask::register,
            ListBundledPluginsTask::register,
            PrintBundledPluginsTask::register,
            DownloadAndroidStudioProductReleasesXmlTask::register,
            DownloadIdeaProductReleasesXmlTask::register,
            ListProductsReleasesTask::register,
            PrintProductsReleasesTask::register,
            PrepareSandboxTask::register,
            BuildSearchableOptionsTask::register,
            JarSearchableOptionsTask::register,
            BuildPluginTask::register,
            RunPluginVerifierTask::register,
            VerifyPluginConfigurationTask::register,
            VerifyPluginTask::register,
            RunIdeTask::register,
            TestIdeTask::register,
        ).forEach { it.invoke(project) }

        configureJarTask()

        with(tasks) {


            // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
            (TASKS - Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN).forEach {
                named(it) { dependsOn(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) }
            }
        }
    }

    private fun Project.configureJarTask() =
        project.registerTask<Jar>(JavaPlugin.JAR_TASK_NAME, Tasks.INSTRUMENTED_JAR) {
            val initializeIntelliJPlatformPluginTaskProvider =
                project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
            val gradleVersion = project.provider {
                project.gradle.gradleVersion
            }
            val projectVersion = project.provider {
                project.version
            }
            val buildSdk = project.provider {
//                extension.localPath.flatMap {
//                    ideaDependencyProvider.map { ideaDependency ->
//                        ideaDependency.classes.toPath().let {
//                            // Fall back on build number if product-info.json is not present, this is the case for recent versions of Android Studio.
//                            it.productInfo().run { "$productCode-$projectVersion" }
//                        }
//                    }
//                }.orElse(extension.getVersionType().zip(extension.getVersionNumber()) { type, version ->
//                    "$type-$version"
//                })
                ""
            }

            exclude("**/classpath.index")

// TODO: make it lazy
//            manifest.attributes(
//                "Created-By" to gradleVersion.map { version -> "Gradle $version" },
//                "Build-JVM" to Jvm.current(),
//                "Version" to projectVersion,
//                "Build-Plugin" to IntelliJPluginConstants.PLUGIN_NAME,
//                "Build-Plugin-Version" to initializeIntelliJPlatformPluginTaskProvider.flatMap {
//                    it.pluginVersion
//                }.get(), // FIXME
//                "Build-OS" to OperatingSystem.current(),
//                "Build-SDK" to buildSdk.get(),
//            )

            dependsOn(initializeIntelliJPlatformPluginTaskProvider)
        }
}
