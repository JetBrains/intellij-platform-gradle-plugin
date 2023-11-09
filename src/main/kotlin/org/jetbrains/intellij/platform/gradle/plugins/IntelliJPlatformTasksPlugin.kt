// Copyright 2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Property
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.process.JavaForkOptions
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_TASKS_ID
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.TASKS
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.info
import org.jetbrains.intellij.platform.gradle.propertyProviders.IntelliJPlatformArgumentProvider
import org.jetbrains.intellij.platform.gradle.propertyProviders.LaunchSystemArgumentProvider
import org.jetbrains.intellij.platform.gradle.propertyProviders.PluginPathArgumentProvider
import org.jetbrains.intellij.platform.gradle.tasks.*
import java.io.File
import java.nio.file.Path
import java.util.*

abstract class IntelliJPlatformTasksPlugin : IntelliJPlatformAbstractProjectPlugin(PLUGIN_TASKS_ID) {

    override fun Project.configure() {
        with(plugins) {
            apply(IntelliJPlatformBasePlugin::class)
        }

        with(tasks) {
            InitializeIntelliJPlatformPluginTask.register(project)
            SetupDependenciesTask.register(project)
            PatchPluginXmlTask.register(project)
            ListBundledPluginsTask.register(project)
            PrintBundledPluginsTask.register(project)
            DownloadAndroidStudioProductReleasesXmlTask.register(project)
            DownloadIdeaProductReleasesXmlTask.register(project)
            ListProductsReleasesTask.register(project)
            PrintProductsReleasesTask.register(project)

            configurePrepareSandboxTasks()
            configureBuildPluginTask()
            configureRunPluginVerifierTask()
            configureVerifyPluginTask()
            configureVerifyPluginConfigurationTask()

            configureJarTask()
            configureTestIdeTask()
            configureRunIdeTask()

            // Make all tasks depend on [INITIALIZE_INTELLIJ_PLUGIN_TASK_NAME]
            (TASKS - Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN).forEach {
                named(it) { dependsOn(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) }
            }
        }
    }

    private fun TaskContainer.configurePrepareSandboxTasks() =
        configureTask<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX, Tasks.PREPARE_TESTING_SANDBOX, Tasks.PREPARE_UI_TESTING_SANDBOX) {
//            val downloadPluginTaskProvider = project.tasks.named<DownloadRobotServerPluginTask>(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
            val runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME)
            val jarTaskProvider = named<Jar>(JavaPlugin.JAR_TASK_NAME)
            val extension = project.the<IntelliJPlatformExtension>()

//            val ideaDependencyJarFiles = ideaDependencyProvider.map {
//                project.files(it.jarFiles)
//            }
            val pluginJarProvider = extension.instrumentCode.flatMap { instrumentCode ->
                when (instrumentCode) {
                    true -> named<Jar>(Tasks.INSTRUMENTED_JAR)
                    false -> jarTaskProvider
                }
            }.flatMap { it.archiveFile }

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

            pluginName.convention(extension.pluginConfiguration.name)
            pluginJar.convention(pluginJarProvider)
            defaultDestinationDir.convention(sandboxDirectory.dir(Sandbox.PLUGINS))
//                librariesToIgnore.convention(ideaDependencyJarFiles)
//                pluginDependencies.convention(project.provider {
//                    extension.getPluginDependenciesList(project)
//                })
            runtimeClasspathFiles.convention(runtimeConfiguration)

            intoChild(pluginName.map { "$it/lib" }).from(runtimeClasspathFiles.map { files ->
                val librariesToIgnore = librariesToIgnore.get().toSet() + Jvm.current().toolsJar
                val pluginDirectories = pluginDependencies.get().map { it.artifact }

                listOf(pluginJar.asFile) + files.filter { file ->
                    !(librariesToIgnore.contains(file) || pluginDirectories.any { p ->
                        file.toPath() == p || file.canonicalPath.startsWith("$p${File.separator}")
                    })
                }
            }).eachFile {
                name = ensureName(file.toPath())
            }

            dependsOn(runtimeConfiguration)
            dependsOn(jarTaskProvider)
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

    private fun TaskContainer.configureVerifyPluginTask() =
        configureTask<VerifyPluginTask>(Tasks.VERIFY_PLUGIN) {
            ignoreFailures.convention(false)
            ignoreUnacceptableWarnings.convention(false)
            ignoreWarnings.convention(true)

//            pluginDir.convention(
//                project.layout.dir(
//                    prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
//                        prepareSandboxTask.pluginName.map { pluginName ->
//                            prepareSandboxTask.destinationDir.resolve(pluginName)
//                        }
//                    }
//                )
//            )

//            dependsOn(Tasks.PREPARE_SANDBOX)
        }

    private fun TaskContainer.configureVerifyPluginConfigurationTask() =
        configureTask<VerifyPluginConfigurationTask>(Tasks.VERIFY_PLUGIN_CONFIGURATION) {
            info(context, "Configuring plugin configuration verification task")

            val patchPluginXmlTaskProvider = named<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML)
            val runPluginVerifierTaskProvider = named<RunPluginVerifierTask>(Tasks.RUN_PLUGIN_VERIFIER)
            val compileJavaTaskProvider = named<JavaCompile>(JavaPlugin.COMPILE_JAVA_TASK_NAME)
            val downloadDirProvider = runPluginVerifierTaskProvider.flatMap { runPluginVerifierTask ->
                runPluginVerifierTask.downloadDir
            }
//            pluginXmlFiles.convention(patchPluginXmlTaskProvider.flatMap { patchPluginXmlTask ->
//                patchPluginXmlTask.outputFiles
//            })
            sourceCompatibility.convention(compileJavaTaskProvider.map {
                it.sourceCompatibility
            })
            targetCompatibility.convention(compileJavaTaskProvider.map {
                it.targetCompatibility
            })
            pluginVerifierDownloadDir.convention(downloadDirProvider)
            kotlinxCoroutinesLibraryPresent.convention(project.provider {
                listOf(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME, JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).any { configurationName ->
                    project.configurations.getByName(configurationName).dependencies.any {
                        it.group == "org.jetbrains.kotlinx" && it.name.startsWith("kotlinx-coroutines")
                    }
                }
            })

            kotlinPluginAvailable.convention(project.provider {
                project.pluginManager.hasPlugin(IntelliJPluginConstants.KOTLIN_GRADLE_PLUGIN_ID)
            })
            project.pluginManager.withPlugin(IntelliJPluginConstants.KOTLIN_GRADLE_PLUGIN_ID) {
                val kotlinOptionsProvider = project.tasks.named(IntelliJPluginConstants.COMPILE_KOTLIN_TASK_NAME).apply {
                    configure {
                        dependsOn(this@configureTask)
                    }
                }.map {
                    it.withGroovyBuilder { getProperty("kotlinOptions") }.withGroovyBuilder { getProperty("options") }
                }

                kotlinJvmTarget.convention(kotlinOptionsProvider.flatMap {
                    it.withGroovyBuilder { getProperty("jvmTarget") as Property<*> }.map { jvmTarget -> jvmTarget.withGroovyBuilder { getProperty("target") } }
                        .map { value -> value as String }
                })
                kotlinApiVersion.convention(kotlinOptionsProvider.flatMap {
                    it.withGroovyBuilder { getProperty("apiVersion") as Property<*> }.map { value -> value as String }
                })
                kotlinLanguageVersion.convention(kotlinOptionsProvider.flatMap {
                    it.withGroovyBuilder { getProperty("languageVersion") as Property<*> }.map { value -> value as String }
                })
                kotlinVersion.convention(project.provider {
                    project.extensions.getByName("kotlin").withGroovyBuilder { getProperty("coreLibrariesVersion") as String }
                })
                kotlinStdlibDefaultDependency.convention(
                    project.providers.gradleProperty(IntelliJPluginConstants.KOTLIN_STDLIB_DEFAULT_DEPENDENCY_PROPERTY_NAME).map { it.toBoolean() })
                kotlinIncrementalUseClasspathSnapshot.convention(
                    project.providers.gradleProperty(IntelliJPluginConstants.KOTLIN_INCREMENTAL_USE_CLASSPATH_SNAPSHOT).map { it.toBoolean() })
            }

            project.tasks.withType<JavaCompile> {
                dependsOn(this@configureTask)
            }

            dependsOn(patchPluginXmlTaskProvider)
        }

    private fun TaskContainer.configureBuildPluginTask() =
        configureTask<BuildPluginTask>(Tasks.BUILD_PLUGIN) {
            val prepareSandboxTaskProvider = named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
//            val jarSearchableOptionsTaskProvider = named<JarSearchableOptionsTask>(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)

            archiveBaseName.convention(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName
            })

            from(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName.map {
                    prepareSandboxTask.destinationDir.resolve(it)
                }
            })
//            from(jarSearchableOptionsTaskProvider.flatMap { jarSearchableOptionsTask ->
//                jarSearchableOptionsTask.archiveFile
//            }) {
//                into("lib")
//            }
            into(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName
            })

            dependsOn(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)
            dependsOn(Tasks.PREPARE_SANDBOX)

//            project.artifacts.add(Dependency.ARCHIVES_CONFIGURATION, this).let { publishArtifact ->
//                extensions.getByType<DefaultArtifactPublicationSet>().addCandidate(publishArtifact)
//                project.components.add(IntelliJPlatformPluginLibrary())
//            }
        }

    private fun TaskContainer.configureJarTask() =
        configureTask<Jar>(JavaPlugin.JAR_TASK_NAME, Tasks.INSTRUMENTED_JAR) {
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

    // TODO: define `inputs.property` for tasks to consider system properties in terms of the configuration cache
    //       see: https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks/-task-inputs/property.html
    private fun TaskContainer.configureTestIdeTask() =
        configureTask<TestIdeTask>(Tasks.TEST_IDE) {
            val sandboxDirectoryProvider = named<PrepareSandboxTask>(Tasks.PREPARE_TESTING_SANDBOX).get().sandboxDirectory

            enableAssertions = true

            jvmArgumentProviders.addAll(
                listOf(
                    IntelliJPlatformArgumentProvider(intelliJPlatform, coroutinesJavaAgentFile, this),
                    LaunchSystemArgumentProvider(intelliJPlatform, sandboxDirectory, emptyList()),
                    PluginPathArgumentProvider(sandboxDirectory),
                )
            )

            outputs.dir(sandboxDirectoryProvider.dir(Sandbox.SYSTEM)).withPropertyName("System directory")
            inputs.dir(sandboxDirectoryProvider.dir(Sandbox.CONFIG)).withPropertyName("Config Directory").withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(sandboxDirectoryProvider.dir(Sandbox.PLUGINS)).withPropertyName("Plugins directory").withPathSensitivity(PathSensitivity.RELATIVE)
                .withNormalizer(ClasspathNormalizer::class)

//            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
//            systemProperty("idea.force.use.core.classloader", "true")
//            systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))
            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")

            dependsOn(sandboxDirectoryProvider)
//            finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)

//            classpath = instrumentedCodeOutputsProvider.get() + instrumentedTestCodeOutputsProvider.get() + classpath
//            testClassesDirs = instrumentedTestCodeOutputsProvider.get() + testClassesDirs

//            doFirst {
//                classpath += ideaDependencyLibrariesProvider.get() +
//                        ideaConfigurationFiles.get() +
//                        ideaPluginsConfigurationFiles.get() +
//                        ideaClasspathFiles.get()
//            }
        }

    // TODO: define `inputs.property` for tasks to consider system properties in terms of the configuration cache
    //       see: https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.tasks/-task-inputs/property.html
    private fun TaskContainer.configureRunIdeTask() =
        configureTask<RunIdeTask>(Tasks.RUN_IDE) {
//            intelliJPlatform = project.configurations.getByName(Configurations.INTELLIJ_PLATFORM)

            mainClass.set("com.intellij.idea.Main")
            enableAssertions = true

            jvmArgumentProviders.addAll(
                listOf(
                    IntelliJPlatformArgumentProvider(intelliJPlatform, coroutinesJavaAgentFile, this),
                    LaunchSystemArgumentProvider(intelliJPlatform, sandboxDirectory, emptyList()),
                )
            )

//            classpath += intelliJPlatform.map {
//
//            }
//                .map {
//                    project.files(productInfo.getBootClasspath(intellijPlatformDirectory.asPath))
//                }

            systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")

            systemPropertyDefault("idea.auto.reload.plugins", true)
            systemPropertyDefault("idea.classpath.index.enabled", false)
            systemPropertyDefault("idea.is.internal", true)
            systemPropertyDefault("idea.plugin.in.sandbox.mode", true)
            systemPropertyDefault("idea.vendor.name", "JetBrains")
            systemPropertyDefault("ide.no.platform.update", false)
            systemPropertyDefault("jdk.module.illegalAccess.silent", true)

            val os = OperatingSystem.current()
            when {
                os.isMacOsX -> {
                    systemPropertyDefault("idea.smooth.progress", false)
                    systemPropertyDefault("apple.laf.useScreenMenuBar", true)
                    systemPropertyDefault("apple.awt.fileDialogForDirectories", true)
                }

                os.isUnix -> {
                    systemPropertyDefault("sun.awt.disablegrab", true)
                }
            }
        }

    private fun TaskContainer.configureRunPluginVerifierTask() =
        configureTask<RunPluginVerifierTask>(Tasks.RUN_PLUGIN_VERIFIER) {
//            val listProductsReleasesTaskProvider = named<ListProductsReleasesTask>(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)
            val buildPluginTaskProvider = named<BuildPluginTask>(Tasks.BUILD_PLUGIN)
            val userHomeProvider = project.providers.systemProperty("user.home")

            failureLevel.convention(EnumSet.of(RunPluginVerifierTask.FailureLevel.COMPATIBILITY_PROBLEMS))
            distributionFile.convention(buildPluginTaskProvider.flatMap { it.archiveFile })
            verificationReportsDir.convention(project.layout.buildDirectory.dir("reports/pluginVerifier").map { it.asFile.canonicalPath })
            verificationReportsFormats.convention(
                EnumSet.of(
                    RunPluginVerifierTask.VerificationReportsFormats.PLAIN,
                    RunPluginVerifierTask.VerificationReportsFormats.HTML,
                )
            )
            downloadDir.convention(ideDownloadDir().map {
                it.toFile().invariantSeparatorsPath
            })
            downloadPath.convention(userHomeProvider.map {
                val userHomePath = Path.of(it)
                with(downloadDir.get()) {
                    when {
                        startsWith("~/") -> userHomePath.resolve(removePrefix("~/"))
                        equals("~") -> userHomePath
                        else -> Path.of(this)
                    }
                }
            })
            teamCityOutputFormat.convention(false)
            subsystemsToCheck.convention("all")
//            productsReleasesFile.convention(listProductsReleasesTaskProvider.flatMap { listProductsReleasesTask ->
//                listProductsReleasesTask.outputFile.asFile
//            })
            offline.convention(project.gradle.startParameter.isOffline)

            dependsOn(Tasks.BUILD_PLUGIN)
            dependsOn(Tasks.VERIFY_PLUGIN)
            dependsOn(Tasks.LIST_PRODUCTS_RELEASES)

            val isIdeVersionsEmpty = localPaths.flatMap { localPaths ->
                ideVersions.map { ideVersions ->
                    localPaths.isEmpty() && ideVersions.isEmpty()
                }
            }
//            listProductsReleasesTaskProvider.get().onlyIf { isIdeVersionsEmpty.get() }
        }

    private fun JavaForkOptions.systemPropertyDefault(name: String, defaultValue: Any) {
        if (!systemProperties.containsKey(name)) {
            systemProperty(name, defaultValue)
        }
    }
}
