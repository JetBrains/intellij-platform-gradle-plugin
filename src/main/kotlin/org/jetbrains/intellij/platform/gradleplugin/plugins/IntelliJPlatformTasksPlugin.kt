// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.kotlin.dsl.apply
import org.jetbrains.intellij.platform.gradleplugin.BuildFeature.SELF_UPDATE_CHECK
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_TASKS_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradleplugin.isBuildFeatureEnabled
import org.jetbrains.intellij.platform.gradleplugin.sourceSets
import org.jetbrains.intellij.platform.gradleplugin.tasks.InitializeIntelliJPluginTask
import org.jetbrains.intellij.platform.gradleplugin.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.platform.gradleplugin.tasks.SetupDependenciesTask
import java.time.LocalDate
import kotlin.io.path.exists

abstract class IntelliJPlatformTasksPlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_TASKS_ID) {

    override fun Project.configure() {
        with(plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        with(tasks) {
            configureTask<SetupDependenciesTask>(Tasks.SETUP_DEPENDENCIES)

            configureTask<InitializeIntelliJPluginTask>(INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME) {
                offline.convention(project.gradle.startParameter.isOffline)
                selfUpdateCheck.convention(project.isBuildFeatureEnabled(SELF_UPDATE_CHECK))
                selfUpdateLockPath.convention(provider {
                    temporaryDir.toPath().resolve(LocalDate.now().toString())
                })
                coroutinesJavaAgentPath.convention(provider {
                    temporaryDir.toPath().resolve("coroutines-javaagent.jar")
                })

                onlyIf {
                    !selfUpdateLockPath.get().exists() || !coroutinesJavaAgentPath.get().exists()
                }
            }

            configureTask<PatchPluginXmlTask>(PATCH_PLUGIN_XML_TASK_NAME) {
                inputFile.convention(provider {
                    sourceSets.getByName(MAIN_SOURCE_SET_NAME).resources.srcDirs
                        .map { it.resolve("META-INF/plugin.xml") }
                        .firstOrNull { it.exists() }
                })
                outputFile.convention(inputFile.map {
                    temporaryDir.resolve(it.name)
                })

                intelliJPlatformExtension.pluginConfiguration.let {
                    pluginId.convention(it.id)
                    pluginName.convention(it.name)
                    pluginVersion.convention(it.version)
                    pluginDescription.convention(it.description)
                    changeNotes.convention(it.changeNotes)

                    it.productDescriptor.let { productDescriptor ->
                        productDescriptorCode.convention(productDescriptor.code)
                        productDescriptorReleaseDate.convention(productDescriptor.releaseDate)
                        productDescriptorReleaseVersion.convention(productDescriptor.releaseVersion)
                        productDescriptorOptional.convention(productDescriptor.optional)
                    }

                    it.ideaVersion.let { ideaVersion ->
                        sinceBuild.convention(ideaVersion.sinceBuild)
                        untilBuild.convention(ideaVersion.untilBuild)
                    }

                    it.vendor.let { vendor ->
                        vendorName.convention(vendor.name)
                        vendorEmail.convention(vendor.email)
                        vendorUrl.convention(vendor.url)
                    }
                }

//                val buildNumberProvider = ideaDependencyProvider.map { it.buildNumber }

//                version.convention(project.provider {
//                    project.version.toString()
//                })
//                pluginXmlFiles.convention(project.provider {
//                    sourcePluginXmlFiles(project).map(Path::toFile)
//                })
//                destinationDir.convention(project.layout.buildDirectory.dir(IntelliJPluginConstants.PLUGIN_XML_DIR_NAME))
//                outputFiles.convention(pluginXmlFiles.map {
//                    it.map { file ->
//                        destinationDir.get().asFile.resolve(file.name)
//                    }
//                })
//                sinceBuild.convention(project.provider {
//                    if (extension.updateSinceUntilBuild.get()) {
//                        val ideVersion = IdeVersion.createIdeVersion(buildNumberProvider.get())
//                        "${ideVersion.baselineVersion}.${ideVersion.build}"
//                    } else {
//                        null
//                    }
//                })
//                untilBuild.convention(project.provider {
//                    if (extension.updateSinceUntilBuild.get()) {
//                        if (extension.sameSinceUntilBuild.get()) {
//                            "${sinceBuild.get()}.*"
//                        } else {
//                            val ideVersion = IdeVersion.createIdeVersion(buildNumberProvider.get())
//                            "${ideVersion.baselineVersion}.*"
//                        }
//                    } else {
//                        null
//                    }
//                })
            }
            // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
            (TASKS - INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME).forEach {
//                named(it) { dependsOn(INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME) }
            }
        }
    }
}

