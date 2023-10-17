// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradleplugin.plugins

import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSet.MAIN_SOURCE_SET_NAME
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.jetbrains.intellij.platform.gradleplugin.*
import org.jetbrains.intellij.platform.gradleplugin.BuildFeature.SELF_UPDATE_CHECK
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Configurations
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.PLUGIN_TASKS_ID
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.platform.gradleplugin.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradleplugin.tasks.*
import java.io.File
import java.time.LocalDate
import kotlin.io.path.exists

abstract class IntelliJPlatformTasksPlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_TASKS_ID) {

    override fun Project.configure() {
        with(plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        with(tasks) {
            configureSetupDependenciesTask()
//            configureBuildPluginTask()
            configureInitializeIntelliJPlatformPluginTask()
            configurePatchPluginXmlTask()
            configureJarTasks()
//            configurePrepareSandboxTasks()

            configureRunIdeTasks()

            // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
            (TASKS - Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN).forEach {
                named(it) { dependsOn(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) }
            }
        }
    }

    private fun TaskContainer.configureSetupDependenciesTask() =
        configureTask<SetupDependenciesTask>(Tasks.SETUP_DEPENDENCIES)

    private fun TaskContainer.configureBuildPluginTask() =
        configureTask<BuildPluginTask>(Tasks.BUILD_PLUGIN) {
            val prepareSandboxTaskProvider = named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
            val jarSearchableOptionsTaskProvider = named<JarSearchableOptionsTask>(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)

            archiveBaseName.convention(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName
            })

            from(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName.map {
                    prepareSandboxTask.destinationDir.resolve(it)
                }
            })
            from(jarSearchableOptionsTaskProvider.flatMap { jarSearchableOptionsTask ->
                jarSearchableOptionsTask.archiveFile
            }) {
                into("lib")
            }
            into(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName
            })

            dependsOn(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)
            dependsOn(Tasks.PREPARE_SANDBOX)

            project.artifacts.add(Dependency.ARCHIVES_CONFIGURATION, this).let { publishArtifact ->
                extensions.getByType<DefaultArtifactPublicationSet>().addCandidate(publishArtifact)
                project.components.add(IntelliJPlatformPluginLibrary())
            }
        }

    private fun TaskContainer.configureInitializeIntelliJPlatformPluginTask() =
        configureTask<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
            offline.convention(project.gradle.startParameter.isOffline)
            selfUpdateCheck.convention(project.isBuildFeatureEnabled(SELF_UPDATE_CHECK))
            selfUpdateLockPath.convention(project.provider {
                temporaryDir.toPath().resolve(LocalDate.now().toString())
            })
            coroutinesJavaAgentPath.convention(project.provider {
                temporaryDir.toPath().resolve("coroutines-javaagent.jar")
            })

            onlyIf {
                !selfUpdateLockPath.get().exists() || !coroutinesJavaAgentPath.get().exists()
            }
        }

    private fun TaskContainer.configurePatchPluginXmlTask() =
        configureTask<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML) {
            inputFile.convention(project.provider {
                project.sourceSets.getByName(MAIN_SOURCE_SET_NAME).resources.srcDirs
                    .map { it.resolve("META-INF/plugin.xml") }
                    .firstOrNull { it.exists() }
            })
            outputFile.convention(inputFile.map {
                temporaryDir.resolve(it.name)
            })

            project.intelliJPlatformExtension.pluginConfiguration.let {
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
        }

    private fun TaskContainer.configureJarTasks() =
        configureTask<Jar>(JavaPlugin.JAR_TASK_NAME, Tasks.INSTRUMENTED_JAR) {
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

            manifest.attributes(
                "Created-By" to gradleVersion.map { version -> "Gradle $version" },
                "Build-JVM" to Jvm.current(),
                "Version" to projectVersion,
                "Build-Plugin" to IntelliJPluginConstants.PLUGIN_NAME,
                "Build-Plugin-Version" to getCurrentPluginVersion().or("0.0.0"),
                "Build-OS" to OperatingSystem.current(),
                "Build-SDK" to buildSdk.get(),
            )
        }

    private fun TaskContainer.configurePrepareSandboxTasks() =
        configureTask<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX, Tasks.PREPARE_TESTING_SANDBOX, Tasks.PREPARE_UI_TESTING_SANDBOX) {
            val downloadPluginTaskProvider = project.tasks.named<DownloadRobotServerPluginTask>(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
            val runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)

//            val ideaDependencyJarFiles = ideaDependencyProvider.map {
//                project.files(it.jarFiles)
//            }
            val pluginJarProvider = project.intelliJPlatformExtension.instrumentCode.flatMap { instrumentCode ->
                when (instrumentCode) {
                    true -> named<Jar>(Tasks.INSTRUMENTED_JAR)
                    false -> named<Jar>(JavaPlugin.JAR_TASK_NAME)
                }
            }.flatMap { jarTask -> jarTask.archiveFile }

            testSuffix.convention(
                when (name) {
                    Tasks.PREPARE_TESTING_SANDBOX -> "-test"
                    Tasks.PREPARE_UI_TESTING_SANDBOX -> "-uiTest"
                    Tasks.PREPARE_SANDBOX -> ""
                    else -> ""
                }
            )

//            project.tasks.register<PrepareSandboxTask>() {
//                PREPARE_UI_TESTING_SANDBOX
//                from(downloadPluginTaskProvider.flatMap { downloadPluginTask ->
//                    downloadPluginTask.outputDir
//                })
//
//                dependsOn(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
//            }

//            pluginName.convention(extension.pluginName)
            pluginJar.convention(pluginJarProvider)
//            defaultDestinationDir.convention(extension.sandboxDir.flatMap {
//                testSuffix.map { testSuffixValue ->
//                    project.file("$it/plugins$testSuffixValue")
//                }
//            })
//            configDir.convention(extension.sandboxDir.flatMap {
//                testSuffix.map { testSuffixValue ->
//                    "$it/config$testSuffixValue"
//                }
//            })
//                librariesToIgnore.convention(ideaDependencyJarFiles)
//                pluginDependencies.convention(project.provider {
//                    extension.getPluginDependenciesList(project)
//                })
            runtimeClasspathFiles.convention(runtimeConfiguration)

            intoChild(pluginName.map { "$it/lib" })
                .from(runtimeClasspathFiles.map { files ->
                    val librariesToIgnore = librariesToIgnore.get().toSet() + Jvm.current().toolsJar
                    val pluginDirectories = pluginDependencies.get().map { it.artifact }

                    listOf(pluginJar.get().asFile) + files.filter { file ->
                        !(librariesToIgnore.contains(file) || pluginDirectories.any { p ->
                            file.toPath() == p || file.canonicalPath.startsWith("$p${File.separator}")
                        })
                    }
                })
                .eachFile {
                    name = ensureName(file.toPath())
                }

            dependsOn(runtimeConfiguration)
//            dependsOn(jarTaskProvider)
//            dependsOn(instrumentedJarTaskProvider)

//            project.afterEvaluate `{
//                extension.plugins.get().filterIsInstance<Project>().forEach { dependency ->
//                    if (dependency.state.executed) {
//                        configureProjectPluginTasksDependency(dependency, this@withType)
//                    } else {
//                        dependency.afterEvaluate {
//                            configureProjectPluginTasksDependency(dependency, this@withType)
//                        }
//                    }
//                }
//            }`
        }

    private fun TaskContainer.configureRunIdeTasks() =
        configureTask<RunIdeTask>(Tasks.RUN_IDE) {
            val initializeIntelliJPlatformPluginTaskProvider = named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)

            intelliJPlatform = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM)
            coroutinesJavaAgentPath.convention(initializeIntelliJPlatformPluginTaskProvider.flatMap {
                it.coroutinesJavaAgentPath
            })
        }
}
