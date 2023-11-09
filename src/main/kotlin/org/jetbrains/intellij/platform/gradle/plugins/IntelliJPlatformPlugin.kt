// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.plugins

import com.jetbrains.plugin.structure.base.utils.extension
import com.jetbrains.plugin.structure.base.utils.hasExtension
import com.jetbrains.plugin.structure.base.utils.isDirectory
import com.jetbrains.plugin.structure.base.utils.nameWithoutExtension
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.JAR_TASK_NAME
import org.gradle.api.plugins.JavaPlugin.PROCESS_RESOURCES_TASK_NAME
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaToolchainService
import org.gradle.kotlin.dsl.*
import org.gradle.kotlin.dsl.support.serviceOf
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.intellij.platform.gradle.*
import org.jetbrains.intellij.platform.gradle.BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING
import org.jetbrains.intellij.platform.gradle.BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING
import org.jetbrains.intellij.platform.gradle.IntelliJPlatformType.*
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.DEFAULT_IDEA_VERSION
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.DOWNLOAD_ZIP_SIGNER_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Extensions.INTELLIJ_PLATFORM
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.IDEA_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.INSTRUMENTED_JAR_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.INSTRUMENTED_JAR_PREFIX
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.INSTRUMENT_CODE_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.INSTRUMENT_TEST_CODE_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.INTELLIJ_DEPENDENCIES
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.JAVA_COMPILER_ANT_TASKS_MAVEN_METADATA
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.MARKETPLACE_HOST
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PERFORMANCE_TEST_CONFIGURATION_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.RELEASE_SUFFIX_EAP
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.RELEASE_SUFFIX_EAP_CANDIDATE
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.RUN_IDE_PERFORMANCE_TEST_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Sandbox
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.VERIFY_PLUGIN_SIGNATURE_TASK_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.VERSION_LATEST
import org.jetbrains.intellij.platform.gradle.dependency.*
import org.jetbrains.intellij.platform.gradle.model.MavenMetadata
import org.jetbrains.intellij.platform.gradle.model.XmlExtractor
import org.jetbrains.intellij.platform.gradle.model.productInfo
import org.jetbrains.intellij.platform.gradle.performanceTest.ProfilerName
import org.jetbrains.intellij.platform.gradle.tasks.*
import org.jetbrains.intellij.platform.gradle.tasks.base.RunIdeBase
import org.jetbrains.intellij.platform.gradle.utils.ArchiveUtils
import org.jetbrains.intellij.platform.gradle.utils.DependenciesDownloader
import org.jetbrains.intellij.platform.gradle.utils.ivyRepository
import org.jetbrains.intellij.platform.gradle.utils.mavenRepository
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import java.io.File
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

abstract class IntelliJPlatformPlugin : Plugin<Project> {

    private lateinit var archiveUtils: ArchiveUtils
    private lateinit var dependenciesDownloader: DependenciesDownloader
    private lateinit var context: String

    override fun apply(project: Project) {
        context = project.logCategory()

        info(context, "Configuring plugin: org.jetbrains.intellij.platform")

        checkGradleVersion()
        project.applyPlugins()

        return

        project.applyConfigurations()

        archiveUtils = project.objects.newInstance()
        dependenciesDownloader = project.objects.newInstance(project.gradle.startParameter.isOffline)

        project.plugins.apply(JavaPlugin::class)

        val extension = project.extensions.create<IntelliJPluginExtension>(INTELLIJ_PLATFORM, dependenciesDownloader).apply {
            version.convention(project.provider {
                if (!localPath.isSpecified()) {
                    throw GradleException("The value for the 'intellij.version' property was not specified, see: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-version")
                }
                null
            })
            pluginName.convention(project.provider {
                project.name
            })
            updateSinceUntilBuild.convention(true)
            sameSinceUntilBuild.convention(false)
            instrumentCode.convention(true)
            sandboxDir.convention(
                project.layout.buildDirectory
                    .dir(Sandbox.CONTAINER)
                    .map { it.asFile.canonicalPath }
            )
            configureDefaultDependencies.convention(true)
            type.convention(IntellijIdeaCommunity.toString())
        }

        val gradleProjectJavaToolchainSpec = project.extensions.getByType<JavaPluginExtension>().toolchain
        val gradleProjectJavaService = project.serviceOf<JavaToolchainService>()

        val ideaDependencyProvider = null as Provider<IdeaDependency> // prepareIdeaDependencyProvider(project, extension).memoize()
        configureTasks(project, extension, ideaDependencyProvider)
    }

    private fun Project.applyPlugins() {
        plugins.apply(IntelliJPlatformBasePlugin::class)
        plugins.apply(IntelliJPlatformTasksPlugin::class)
    }

    private fun configureTasks(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring plugin")
//        project.tasks.withType<RunIdeBase> {
//            prepareConventionMappingsForRunIdeTask(project, extension, ideaDependencyProvider, Tasks.PREPARE_SANDBOX)
//        }
//        project.tasks.withType<RunIdeForUiTestTask> {
//            prepareConventionMappingsForRunIdeTask(project, extension, ideaDependencyProvider, Tasks.PREPARE_UI_TESTING_SANDBOX)
//        }

        configureClassPathIndexCleanupTask(project, ideaDependencyProvider)
        configureInstrumentation(project, extension, ideaDependencyProvider)
        configureDownloadRobotServerPluginTask(project)
        configureRunIdeTask(project)
        configureRunIdePerformanceTestTask(project, extension)
        configureRunIdeForUiTestsTask(project)
        configureBuildSearchableOptionsTask(project)
        configureJarSearchableOptionsTask(project)
        configureDownloadZipSignerTask(project)
        configureSignPluginTask(project)
        configurePublishPluginTask(project)
        configureProcessResources(project)
        assert(!project.state.executed) { "afterEvaluate is a no-op for an executed project" }

//        project.pluginManager.withPlugin(KOTLIN_GRADLE_PLUGIN_ID) {
////            project.tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
////                dependsOn(Tasks.VERIFY_PLUGIN_CONFIGURATION)
////            }
//        }
//
//        project.tasks.withType<JavaCompile> {
//            dependsOn(Tasks.VERIFY_PLUGIN_CONFIGURATION)
//        }

        project.afterEvaluate {
            configureProjectAfterEvaluate(this, extension, ideaDependencyProvider)
        }
    }

    private fun prepareIdeaDependencyProvider(project: Project, extension: IntelliJPluginExtension) = project.provider {
        val configureDefaultDependencies = extension.configureDefaultDependencies.get()
        val extraDependencies = extension.extraDependencies.get()
        val ideaDependencyCachePath = extension.ideaDependencyCachePath.orNull.orEmpty()
        val localPath = extension.localPath.orNull
        val type = extension.getVersionType().orNull
        val version = extension.getVersionNumber().orNull

        val ideaConfiguration = project.configurations.getByName(IDEA_CONFIGURATION_NAME)

        val dependencyManager = project.objects.newInstance<IdeaDependencyManager>(
            ideaDependencyCachePath,
            archiveUtils,
            dependenciesDownloader,
            context,
        )

        val ideaDependency = when {
            localPath != null && version != null -> {
                throw GradleException("Both 'intellij.localPath' and 'intellij.version' are specified, but one of these is allowed to be present.")
            }

            version != null && type != null -> {
                info(context, "Using IDE from remote repository")
                dependencyManager.resolveRemote(project, version, type, extraDependencies)
            }

            localPath != null -> {
                info(context, "Using path to locally installed IDE: $localPath")
                dependencyManager.resolveLocal(project, localPath)
            }

            else -> {
                throw GradleException("Either 'intellij.localPath' or 'intellij.version' must be specified")
            }
        }

        if (configureDefaultDependencies && ideaConfiguration.dependencies.isEmpty()) {
            info(context, "${ideaDependency.buildNumber} is used for building")

            dependencyManager.register(project, ideaDependency, ideaConfiguration.dependencies)
            ideaConfiguration.resolve()

            if (!ideaDependency.extraDependencies.isEmpty()) {
                info(context, "Note: ${ideaDependency.buildNumber} extra dependencies (${ideaDependency.extraDependencies}) should be applied manually")
            }
        } else {
            info(context, "IDE ${ideaDependency.buildNumber} dependencies are applied manually")
        }

        ideaDependency
    }

    private fun Project.applyConfigurations() {
        val ideaPlugins = project.configurations.create(IDEA_PLUGINS_CONFIGURATION_NAME)
            .setVisible(false)
            .withDependencies {
//                configurePluginDependencies(project, ideaDependencyProvider, extension, this)
            }
            .apply {
                isCanBeConsumed = false
                isCanBeResolved = true
            }

        val performanceTest = project.configurations.create(PERFORMANCE_TEST_CONFIGURATION_NAME)
            .setVisible(false)
            .withDependencies {
//                val resolver = project.objects.newInstance<PluginDependencyManager>(
//                    project.gradle.gradleUserHomeDir.canonicalPath,
//                    ideaDependencyProvider,
//                    extension.getPluginsRepositories(),
//                    archiveUtils,
//                    context,
//                )

                // Check that the `runIdePerformanceTest` task was launched
                // Check that `performanceTesting.jar` is absent (that means it's a community version)
                // Check that user didn't pass a custom version of the performance plugin
//                if (
//                    RUN_IDE_PERFORMANCE_TEST_TASK_NAME in project.gradle.startParameter.taskNames
//                    && extension.plugins.get().none { it is String && it.startsWith(PERFORMANCE_PLUGIN_ID) }
//                ) {
//                    val bundledPlugins = BuiltinPluginsRegistry.resolveBundledPlugins(ideaDependencyProvider.get().classes.toPath(), context)
//                    if (!bundledPlugins.contains(PERFORMANCE_PLUGIN_ID)) {
//                        val buildNumber = ideaDependencyProvider.get().buildNumber
//                        val resolvedPlugin = resolveLatestPluginUpdate(PERFORMANCE_PLUGIN_ID, buildNumber)
//                            ?: throw BuildException("No suitable plugin update found for $PERFORMANCE_PLUGIN_ID:$buildNumber")
//
//                        val plugin = resolver.resolve(project, resolvedPlugin)
//                            ?: throw BuildException(with(resolvedPlugin) { "Failed to resolve plugin $id:$version@$channel" })
//
//                        configurePluginDependency(project, plugin, extension, this, resolver)
//                    }
//                }
            }
            .apply {
                isCanBeConsumed = false
                isCanBeResolved = true
            }

//        fun Configuration.extend() = extendsFrom(defaultDependencies, idea, ideaPlugins, performanceTest)
    }

    private fun configureProjectAfterEvaluate(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        project.subprojects.forEach { subproject ->
            if (subproject.plugins.findPlugin(IntelliJPlatformPlugin::class) == null) {
                subproject.extensions.findByType<IntelliJPluginExtension>()?.let {
                    configureProjectAfterEvaluate(subproject, it, ideaDependencyProvider)
                }
            }
        }

        configureTestTasks(project, extension, ideaDependencyProvider)
    }

    private fun verifyJavaPluginDependency(project: Project, ideaDependency: IdeaDependency, plugins: List<Any>) {
        val hasJavaPluginDependency = plugins.contains("java") || plugins.contains("com.intellij.java")
        if (!hasJavaPluginDependency && File(ideaDependency.classes, "plugins/java").exists()) {
            sourcePluginXmlFiles(project).forEach { path ->
                parsePluginXml(path)?.dependencies?.forEach {
                    if (it.dependencyId == "com.intellij.modules.java") {
                        throw BuildException("The project depends on 'com.intellij.modules.java' module but doesn't declare a compile dependency on it.\nPlease delete 'depends' tag from '${path}' or add Java plugin to Gradle dependencies (https://plugins.jetbrains.com/docs/intellij/plugin-compatibility.html#java)")
                    }
                }
            }
        }
    }

    private fun configureBuiltinPluginsDependencies(
        project: Project,
        dependencies: DependencySet,
        resolver: PluginDependencyManager,
        extension: IntelliJPluginExtension,
        ideaDependency: IdeaDependency,
    ) {
        val configuredPlugins = extension.getUnresolvedPluginDependencies()
            .filter(PluginDependency::builtin)
            .map(PluginDependency::id)
        ideaDependency.pluginsRegistry.collectBuiltinDependencies(configuredPlugins).forEach {
            val plugin = resolver.resolve(project, PluginDependencyNotation(it, null, null)) ?: return
            configurePluginDependency(project, plugin, extension, dependencies, resolver)
        }
    }

    private fun configurePluginDependency(
        project: Project,
        plugin: PluginDependency,
        extension: IntelliJPluginExtension,
        dependencies: DependencySet,
        resolver: PluginDependencyManager,
    ) {
        if (extension.configureDefaultDependencies.get()) {
            resolver.register(project, plugin, dependencies)
        }
        extension.addPluginDependency(plugin)
        project.tasks.withType<PrepareSandboxTask> {
            configureExternalPlugin(plugin)
        }
    }

    private fun configureProjectPluginTasksDependency(dependency: Project, task: PrepareSandboxTask) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPlatformPlugin::class) == null) {
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin not found: ${dependency.plugins}")
        }
        dependency.tasks.named(Tasks.PREPARE_SANDBOX) {
            task.dependsOn(this)
        }
    }

    private fun configureProjectPluginDependency(project: Project, dependency: Project, dependencies: DependencySet, extension: IntelliJPluginExtension) {
        // invoke on demand when plugin artifacts are needed
        if (dependency.plugins.findPlugin(IntelliJPlatformPlugin::class) == null) {
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin not found: ${dependency.plugins}")
        }
        dependencies.add(project.dependencies.create(dependency))

        val prepareSandboxTaskProvider = dependency.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)
        val dependencyDirectory = prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
            prepareSandboxTask.pluginName.map { pluginName ->
                prepareSandboxTask.destinationDir.resolve(pluginName)
            }
        }

        val pluginDependency = PluginProjectDependency(dependencyDirectory.get(), context)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType<PrepareSandboxTask> {
            configureCompositePlugin(pluginDependency)
        }
    }

//    private fun configurePrepareSandboxTasks(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
//        val jarTaskProvider = project.tasks.named<Jar>(JAR_TASK_NAME)
//        val instrumentedJarTaskProvider = project.tasks.named<Jar>(INSTRUMENTED_JAR_TASK_NAME)
//        val downloadPluginTaskProvider = project.tasks.named<DownloadRobotServerPluginTask>(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
//        val runtimeConfiguration = project.configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)
//
//        val ideaDependencyJarFiles = ideaDependencyProvider.map {
//            project.files(it.jarFiles)
//        }
//        val pluginJarProvider = extension.instrumentCode.flatMap { instrumentCode ->
//            when (instrumentCode) {
//                true -> instrumentedJarTaskProvider
//                false -> jarTaskProvider
//            }
//        }.flatMap { jarTask -> jarTask.archiveFile }
//
//        val gradleVersion = project.provider {
//            project.gradle.gradleVersion
//        }
//        val projectVersion = project.provider {
//            project.version
//        }
//        val buildSdk = project.provider {
//            extension.localPath.flatMap {
//                ideaDependencyProvider.map { ideaDependency ->
//                    ideaDependency.classes.toPath().let {
//                        // Fall back on build number if product-info.json is not present, this is the case for recent versions of Android Studio.
//                        it.productInfo().run { "$productCode-$projectVersion" }
//                    }
//                }
//            }.orElse(extension.getVersionType().zip(extension.getVersionNumber()) { type, version ->
//                "$type-$version"
//            })
//        }
//
//        listOf(jarTaskProvider, instrumentedJarTaskProvider).forEach {
//            it.configure {
//                exclude("**/classpath.index")
//
//                manifest.attributes(
//                    "Created-By" to gradleVersion.map { version -> "Gradle $version" },
//                    "Build-JVM" to Jvm.current(),
//                    "Version" to projectVersion,
//                    "Build-Plugin" to PLUGIN_NAME,
//                    "Build-Plugin-Version" to getCurrentPluginVersion().or("0.0.0"),
//                    "Build-OS" to OperatingSystem.current(),
//                    "Build-SDK" to buildSdk.get(),
//                )
//            }
//        }
//
//        project.tasks.register<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX) {
//            testSuffix.convention("")
//        }
//        project.tasks.register<PrepareSandboxTask>(Tasks.PREPARE_TESTING_SANDBOX) {
//            testSuffix.convention("-test")
//        }
//        project.tasks.register<PrepareSandboxTask>(Tasks.PREPARE_UI_TESTING_SANDBOX) {
//            testSuffix.convention("-uiTest")
//
//            from(downloadPluginTaskProvider.flatMap { downloadPluginTask ->
//                downloadPluginTask.outputDir
//            })
//
//            dependsOn(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
//        }
//
//        project.tasks.withType<PrepareSandboxTask> {
//            pluginName.convention(extension.pluginName)
//            pluginJar.convention(pluginJarProvider)
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
//            librariesToIgnore.convention(ideaDependencyJarFiles)
//            pluginDependencies.convention(project.provider {
//                extension.getPluginDependenciesList(project)
//            })
//            runtimeClasspathFiles.convention(runtimeConfiguration)
//
//            intoChild(pluginName.map { "$it/lib" })
//                .from(runtimeClasspathFiles.map { files ->
//                    val librariesToIgnore = librariesToIgnore.get().toSet() + Jvm.current().toolsJar
//                    val pluginDirectories = pluginDependencies.get().map { it.artifact }
//
//                    listOf(pluginJar.asFile) + files.filter { file ->
//                        !(librariesToIgnore.contains(file) || pluginDirectories.any { p ->
//                            file.toPath() == p || file.canonicalPath.startsWith("$p${File.separator}")
//                        })
//                    }
//                })
//                .eachFile {
//                    name = ensureName(file.toPath())
//                }
//
//            dependsOn(runtimeConfiguration)
//            dependsOn(jarTaskProvider)
//            dependsOn(instrumentedJarTaskProvider)
//
//            project.afterEvaluate {
//                extension.plugins.get().filterIsInstance<Project>().forEach { dependency ->
//                    if (dependency.state.executed) {
//                        configureProjectPluginTasksDependency(dependency, this@withType)
//                    } else {
//                        dependency.afterEvaluate {
//                            configureProjectPluginTasksDependency(dependency, this@withType)
//                        }
//                    }
//                }
//            }
//        }
//    }

    private fun configureDownloadRobotServerPluginTask(project: Project) {
        info(context, "Configuring robot-server download Task")

        project.tasks.register<DownloadRobotServerPluginTask>(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
        project.tasks.withType<DownloadRobotServerPluginTask> {
            val taskContext = logCategory()

            version.convention(VERSION_LATEST)
            outputDir.convention(project.layout.buildDirectory.dir("robotServerPlugin"))
            pluginArchive.convention(project.provider {
                val resolvedVersion = resolveRobotServerPluginVersion(version.orNull)
                val (group, name) = getDependency(resolvedVersion).split(':')
                dependenciesDownloader.downloadFromRepository(taskContext, {
                    create(
                        group = group,
                        name = name,
                        version = resolvedVersion,
                    )
                }, {
                    mavenRepository(INTELLIJ_DEPENDENCIES) {
                        content { includeGroup(group) }
                    }
                }).first()
            })
        }
    }

    private fun configureRunIdeTask(project: Project) {
        info(context, "Configuring run IDE task")

        project.tasks.register<RunIdeTask>(Tasks.RUN_IDE)
        project.tasks.withType<RunIdeTask> {
            dependsOn(Tasks.PREPARE_SANDBOX)
            finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        }
    }

    private fun configureRunIdePerformanceTestTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring run IDE performance test task")

        project.tasks.register<RunIdePerformanceTestTask>(RUN_IDE_PERFORMANCE_TEST_TASK_NAME)
        project.tasks.withType<RunIdePerformanceTestTask> {
            artifactsDir.convention(extension.type.flatMap { type ->
                extension.version.flatMap { version ->
                    project.layout.buildDirectory.dir(
                        "reports/performance-test/$type$version-${project.version}-${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                        }"
                    ).map { it.toString() }
                }
            })
            profilerName.convention(ProfilerName.ASYNC)

            finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        }
    }

    private fun resolveLatestPluginUpdate(pluginId: String, buildNumber: String, channel: String = "") =
        PluginRepositoryFactory.create(MARKETPLACE_HOST)
            .pluginManager
            .searchCompatibleUpdates(listOf(pluginId), buildNumber, channel)
            .firstOrNull()
            ?.let { PluginDependencyNotation(it.pluginXmlId, it.version, it.channel) }

    private fun configureRunIdeForUiTestsTask(project: Project) {
        info(context, "Configuring run IDE for UI tests task")

        project.tasks.register<RunIdeForUiTestTask>(RUN_IDE_FOR_UI_TESTS_TASK_NAME)
        project.tasks.withType<RunIdeForUiTestTask> {
            dependsOn(Tasks.PREPARE_UI_TESTING_SANDBOX)
            finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        }
    }

    private fun configureBuildSearchableOptionsTask(project: Project) {
        info(context, "Configuring build searchable options task")

        project.tasks.register<BuildSearchableOptionsTask>(BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
        project.tasks.withType<BuildSearchableOptionsTask> {
            outputDir.convention(project.layout.buildDirectory.dir(SEARCHABLE_OPTIONS_DIR_NAME))
            showPaidPluginWarning.convention(project.isBuildFeatureEnabled(PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING).map {
                it && sourcePluginXmlFiles(project).any { file ->
                    parsePluginXml(file)?.productDescriptor != null
                }
            })

            dependsOn(Tasks.PREPARE_SANDBOX)
        }
    }

    private fun RunIdeBase.prepareConventionMappingsForRunIdeTask(
        project: Project,
        extension: IntelliJPluginExtension,
        ideaDependencyProvider: Provider<IdeaDependency>,
        prepareSandBoxTaskName: String,
    ) {
        val taskContext = logCategory()
        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(prepareSandBoxTaskName)
        val initializeIntelliJPlatformPluginTaskProvider = project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it)?.id }

//        ideDir.convention(ideaDependencyProvider.map {
//            project.file(it.classes.path)
//        })
        requiredPluginIds.convention(project.provider {
            pluginIds
        })
//        configDir.convention(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
//            prepareSandboxTask.configDir.map { project.file(it) }
//        })
//        pluginsDir.convention(prepareSandboxTaskProvider.map { prepareSandboxTask ->
//            project.layout.projectDirectory.dir(prepareSandboxTask.destinationDir.path)
//        })
//        systemDir.convention(extension.sandboxDir.map {
//            project.file("$it/system")
//        })
//        autoReloadPlugins.convention(true)
//        projectWorkingDir.convention(ideDir.map {
//            it.resolve("bin")
//        })
//        projectExecutable.convention(project.provider {
//            jbrResolver.resolveRuntime(
//                jbrVersion = jbrVersion.orNull,
//                jbrVariant = jbrVariant.orNull,
//                jbrArch = jbrArch.orNull,
//                ideDir = ideDir.orNull,
//            ).toString()
//        })
//        coroutinesJavaAgentPath.convention(initializeIntelliJPlatformPluginTaskProvider.flatMap {
//            it.coroutinesJavaAgentPath
//        })
    }

    private fun configureJarSearchableOptionsTask(project: Project) {
        info(context, "Configuring jar searchable options task")

        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_SANDBOX)

        project.tasks.register<JarSearchableOptionsTask>(JAR_SEARCHABLE_OPTIONS_TASK_NAME)
        project.tasks.withType<JarSearchableOptionsTask> {
            inputDir.convention(project.layout.buildDirectory.dir(SEARCHABLE_OPTIONS_DIR_NAME))
            pluginName.convention(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName
            })
            sandboxDir.convention(prepareSandboxTaskProvider.map { prepareSandboxTask ->
                prepareSandboxTask.destinationDir.canonicalPath
            })
            archiveBaseName.convention("lib/$SEARCHABLE_OPTIONS_DIR_NAME")
            destinationDirectory.convention(project.layout.buildDirectory.dir("libsSearchableOptions"))
            noSearchableOptionsWarning.convention(project.isBuildFeatureEnabled(NO_SEARCHABLE_OPTIONS_WARNING))

            dependsOn(BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
            dependsOn(Tasks.PREPARE_SANDBOX)
            onlyIf { inputDir.asPath.isDirectory }
        }
    }

    private fun configureInstrumentation(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring compile tasks")

        val instrumentedJar = project.configurations.create(INSTRUMENTED_JAR_CONFIGURATION_NAME)
            .apply {
                isCanBeConsumed = true
                isCanBeResolved = false

                extendsFrom(project.configurations["implementation"], project.configurations["runtimeOnly"])
            }

        val jarTaskProvider = project.tasks.named<Jar>(JAR_TASK_NAME)
        val instrumentCodeProvider = project.provider { extension.instrumentCode.get() }

        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
        sourceSets.forEach { sourceSet ->
            val name = sourceSet.getTaskName("instrument", "code")
            val instrumentTaskProvider = project.tasks.register<InstrumentCodeTask>(name) {
                val taskContext = logCategory()

                sourceDirs.from(project.provider {
                    sourceSet.allJava.srcDirs
                })
                formsDirs.from(project.provider {
                    sourceDirs.asFileTree.filter {
                        it.toPath().hasExtension("form")
                    }
                })
                classesDirs.from(project.provider {
                    (sourceSet.output.classesDirs as ConfigurableFileCollection).from.run {
                        project.files(this).filter { it.exists() }
                    }
                })
                sourceSetCompileClasspath.from(project.provider {
                    sourceSet.compileClasspath
                })
                compilerVersion.convention(ideaDependencyProvider.map {
                    val productInfo = it.classes.toPath().productInfo()

                    val version = extension.getVersionNumber().orNull.orEmpty()
                    val type = extension.getVersionType().orNull.orEmpty().let {
                        IntelliJPlatformType.fromCode(it)
                    }
                    val localPath = extension.localPath.orNull.orEmpty()
                    val types = listOf(CLion, Rider, PyCharmProfessional, PhpStorm)

                    when {
                        localPath.isNotBlank() || !version.endsWith(RELEASE_SUFFIX_SNAPSHOT) -> {
                            val eapSuffix = RELEASE_SUFFIX_EAP.takeIf { productInfo.versionSuffix == "EAP" }.orEmpty()
                            IdeVersion.createIdeVersion(it.buildNumber).stripExcessComponents().asStringWithoutProductCode() + eapSuffix
                        }

                        version == DEFAULT_IDEA_VERSION && types.contains(type) -> {
                            val buildNumber = Version.parse(productInfo.buildNumber)
                            "${buildNumber.major}.${buildNumber.minor}$RELEASE_SUFFIX_EAP_CANDIDATE"
                        }

                        else -> {
                            val prefix = when (type) {
                                CLion -> "CLION-"
                                Rider -> "RIDER-"
                                PyCharmProfessional -> "PYCHARM-"
                                PhpStorm -> "PHPSTORM-"
                                else -> ""
                            }
                            prefix + version
                        }
                    }
                })
                ideaDependency.convention(ideaDependencyProvider)
                javac2.convention(ideaDependencyProvider.map {
                    it.classes.resolve("lib/javac2.jar")
                })
                compilerClassPathFromMaven.convention(compilerVersion.map { compilerVersion ->
                    if (compilerVersion == DEFAULT_IDEA_VERSION || Version.parse(compilerVersion) >= Version(183, 3795, 13)) {
                        val downloadCompiler = { version: String ->
                            dependenciesDownloader.downloadFromMultipleRepositories(taskContext, {
                                create(
                                    group = "com.jetbrains.intellij.java",
                                    name = "java-compiler-ant-tasks",
                                    version = version,
                                )
                            }, {
                                setOf(
                                    INTELLIJ_DEPENDENCIES,
                                ).map(::mavenRepository)
                            }, true).takeIf { it.isNotEmpty() }
                        }

                        listOf(
                            {
                                runCatching { downloadCompiler(compilerVersion) }.fold(
                                    onSuccess = { it },
                                    onFailure = {
                                        warn(taskContext, "Cannot resolve java-compiler-ant-tasks in version: $compilerVersion")
                                        null
                                    },
                                )
                            },
                            {
                                /**
                                 * Try falling back on the version without the -EAP-SNAPSHOT suffix if the download
                                 * for it fails - not all versions have a corresponding -EAP-SNAPSHOT version present
                                 * in the snapshot repository.
                                 */
                                if (compilerVersion.endsWith(RELEASE_SUFFIX_EAP)) {
                                    val nonEapVersion = compilerVersion.replace(RELEASE_SUFFIX_EAP, "")
                                    runCatching { downloadCompiler(nonEapVersion) }.fold(
                                        onSuccess = {
                                            warn(taskContext, "Resolved non-EAP java-compiler-ant-tasks version: $nonEapVersion")
                                            it
                                        },
                                        onFailure = {
                                            warn(taskContext, "Cannot resolve java-compiler-ant-tasks in version: $nonEapVersion")
                                            null
                                        },
                                    )
                                } else {
                                    null
                                }
                            },
                            {
                                /**
                                 * Get the list of available packages and pick the closest lower one.
                                 */
                                val closestCompilerVersion = URL(JAVA_COMPILER_ANT_TASKS_MAVEN_METADATA).openStream().use { inputStream ->
                                    val version = Version.parse(compilerVersion)
                                    XmlExtractor<MavenMetadata>().unmarshal(inputStream).versioning?.versions
                                        ?.map(Version.Companion::parse)?.filter { it <= version }
                                        ?.maxOf { it }
                                        ?.version
                                }

                                if (closestCompilerVersion == null) {
                                    warn(taskContext, "Cannot resolve java-compiler-ant-tasks Maven metadata")
                                    null
                                } else {
                                    runCatching { downloadCompiler(closestCompilerVersion) }.fold(
                                        onSuccess = {
                                            warn(taskContext, "Resolved closest lower java-compiler-ant-tasks version: $closestCompilerVersion")
                                            it
                                        },
                                        onFailure = {
                                            warn(taskContext, "Cannot resolve java-compiler-ant-tasks in version: $closestCompilerVersion")
                                            null
                                        },
                                    )
                                }
                            },
                        ).asSequence().mapNotNull { it() }.firstOrNull().orEmpty()
                    } else {
                        warn(
                            taskContext,
                            "Compiler in '$compilerVersion' version can't be resolved from Maven. Minimal version supported: 2018.3+. Use higher 'intellij.version' or specify the 'compilerVersion' property manually.",
                        )
                        emptyList()
                    }
                })

                outputDir.convention(project.layout.buildDirectory.map { it.dir("instrumented").dir(name) })
                instrumentationLogs.convention(project.gradle.startParameter.logLevel == LogLevel.INFO)

                dependsOn(sourceSet.classesTaskName)
                finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)
                onlyIf { instrumentCodeProvider.get() }
            }

            // Ensure that our task is invoked when the source set is built
            sourceSet.compiledBy(instrumentTaskProvider)
        }

        val instrumentTaskProvider = project.tasks.named<InstrumentCodeTask>(INSTRUMENT_CODE_TASK_NAME)
        val instrumentedJarTaskProvider = project.tasks.register<InstrumentedJarTask>(Tasks.INSTRUMENTED_JAR) {
            duplicatesStrategy = DuplicatesStrategy.EXCLUDE

            archiveBaseName.convention(jarTaskProvider.flatMap { jarTask ->
                jarTask.archiveBaseName.map {
                    "$INSTRUMENTED_JAR_PREFIX-$it"
                }
            })

            from(instrumentTaskProvider)
            with(jarTaskProvider.get())

            dependsOn(instrumentTaskProvider)

            onlyIf { instrumentCodeProvider.get() }
        }

        project.artifacts.add(instrumentedJar.name, instrumentedJarTaskProvider)
    }

    private fun configureTestTasks(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring tests tasks")
//        val runIdeTaskProvider = project.tasks.named<RunIdeTask>(Tasks.RUN_IDE)
        val prepareTestingSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(Tasks.PREPARE_TESTING_SANDBOX)
        val instrumentedCodeTaskProvider = project.tasks.named<InstrumentCodeTask>(INSTRUMENT_CODE_TASK_NAME)
        val instrumentedTestCodeTaskProvider = project.tasks.named<InstrumentCodeTask>(INSTRUMENT_TEST_CODE_TASK_NAME)
        val instrumentedCodeOutputsProvider = project.provider {
            project.files(instrumentedCodeTaskProvider.map { it.outputDir.asFile })
        }
        val instrumentedTestCodeOutputsProvider = project.provider {
            project.files(instrumentedTestCodeTaskProvider.map { it.outputDir.asFile })
        }
        val initializeIntellijPlatformPluginTaskProvider = project.tasks.named<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN)
        val coroutinesJavaAgentPathProvider = initializeIntellijPlatformPluginTaskProvider.flatMap {
            it.coroutinesJavaAgent
        }

        val testTasks = project.tasks.withType<Test>()
        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it)?.id }
        val buildNumberProvider = ideaDependencyProvider.map {
            it.buildNumber
        }
//        val ideDirProvider = runIdeTaskProvider.flatMap { runIdeTask ->
//            runIdeTask.ideDir.map { it.toPath() }
//        }
//        val jbrArchProvider = runIdeTaskProvider.flatMap { runIdeTask ->
//            runIdeTask.jbrArch
//        }
//        val jbrVersionProvider = runIdeTaskProvider.flatMap { runIdeTask ->
//            runIdeTask.jbrVersion
//        }
//        val jbrVariantProvider = runIdeTaskProvider.flatMap { runIdeTask ->
//            runIdeTask.jbrVariant
//        }

        val ideaDependencyLibrariesProvider = ideaDependencyProvider
            .map { it.classes }
            .map { project.files("$it/lib/resources.jar", "$it/lib/idea.jar", "$it/lib/app.jar") }

        val sandboxDirProvider = extension.sandboxDir.map {
            project.file(it)
        }
        val configDirectoryProvider = sandboxDirProvider.map {
            it.resolve("config-test").apply { mkdirs() }
        }
        val systemDirectoryProvider = sandboxDirProvider.map {
            it.resolve("system-test").apply { mkdirs() }
        }
        val pluginsDirectoryProvider = prepareTestingSandboxTaskProvider.map { prepareSandboxTask ->
            prepareSandboxTask.destinationDir.apply { mkdirs() }
        }

        val ideaConfigurationFiles = project.provider {
            project.files(project.configurations.getByName(IDEA_CONFIGURATION_NAME).resolve())
        }
        val ideaPluginsConfigurationFiles = project.provider {
            project.files(project.configurations.getByName(IDEA_PLUGINS_CONFIGURATION_NAME).resolve())
        }
//        val ideaClasspathFiles = ideDirProvider.map {
//            project.files(getIdeaClasspath(it))
//        }

        testTasks.configureEach {
            enableAssertions = true

            // appClassLoader should be used for user's plugins. Otherwise, classes it won't be possible to use
            // its classes of application components or services in tests: class loaders will be different for
            // classes references by test code and for classes loaded by the platform (pico container).
            //
            // The proper way to handle that is to substitute Gradle's test class-loader and teach it
            // to understand PluginClassLoaders. Unfortunately, I couldn't find a way to do that.
            systemProperty("idea.use.core.classloader.for.plugin.path", "true")
            systemProperty("idea.force.use.core.classloader", "true")
            // the same as previous setting appClassLoader but outdated. Works for part of 203 builds.
            systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

            outputs.dir(systemDirectoryProvider)
                .withPropertyName("System directory")
            inputs.dir(configDirectoryProvider)
                .withPropertyName("Config Directory")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            inputs.files(pluginsDirectoryProvider)
                .withPropertyName("Plugins directory")
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .withNormalizer(ClasspathNormalizer::class)

            dependsOn(Tasks.PREPARE_TESTING_SANDBOX)
            finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)

//            jbrResolver.resolveRuntime(
//                jbrVersion = jbrVersionProvider.orNull,
//                jbrVariant = jbrVariantProvider.orNull,
//                jbrArch = jbrArchProvider.orNull,
//                ideDir = ideDirProvider.map { it.toFile() }.orNull,
//            )?.let {
//                executable = it.toString()
//            }

            classpath = instrumentedCodeOutputsProvider.get() + instrumentedTestCodeOutputsProvider.get() + classpath
            testClassesDirs = instrumentedTestCodeOutputsProvider.get() + testClassesDirs
//            jvmArgumentProviders.add(IntelliJPlatformArgumentProvider(ideDirProvider.get(), coroutinesJavaAgentPathProvider.get(), this))

            doFirst {
//                classpath += ideaDependencyLibrariesProvider.get() +
//                        ideaConfigurationFiles.get() +
//                        ideaPluginsConfigurationFiles.get() +
//                        ideaClasspathFiles.get()


//                jvmArgumentProviders.add(
//                    LaunchSystemArgumentProvider(
//                        ideDirProvider.get(),
//                        configDirectoryProvider.get(),
//                        systemDirectoryProvider.get(),
//                        pluginsDirectoryProvider.get(),
//                        pluginIds,
//                    )
//                )

//                jvmArgumentProviders.add(PluginPathArgumentProvider(pluginsDirectoryProvider.get()))
                systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
            }
        }
    }

    private fun configureDownloadZipSignerTask(project: Project) {
        project.tasks.register<DownloadZipSignerTask>(DOWNLOAD_ZIP_SIGNER_TASK_NAME)
        project.tasks.withType<DownloadZipSignerTask> {
            val taskContext = logCategory()

            version.convention(VERSION_LATEST)
            cliPath.convention(version.map {
                val resolvedCliVersion = resolveCliVersion(version.orNull)
                val url = resolveCliUrl(resolvedCliVersion)
                debug(taskContext, "Using Marketplace ZIP Signer CLI in '$resolvedCliVersion' version")

                dependenciesDownloader.downloadFromRepository(taskContext, {
                    create(
                        group = "org.jetbrains",
                        name = "marketplace-zip-signer-cli",
                        version = resolvedCliVersion,
                        ext = "jar",
                    )
                }, {
                    ivyRepository(url)
                }).first().absolutePath
            })
            cli.convention(project.layout.buildDirectory.file("tools/marketplace-zip-signer-cli.jar"))
        }
    }

    private fun configureSignPluginTask(project: Project) {
        info(context, "Configuring sign plugin task")

        val signPluginTaskProvider = project.tasks.register<SignPluginTask>(SIGN_PLUGIN_TASK_NAME)
        val buildPluginTaskProvider = project.tasks.named<Zip>(Tasks.BUILD_PLUGIN)
        val downloadZipSignerTaskProvider = project.tasks.named<DownloadZipSignerTask>(DOWNLOAD_ZIP_SIGNER_TASK_NAME)
        val cliPathProvider = downloadZipSignerTaskProvider.flatMap { downloadZipSignerTask ->
            downloadZipSignerTask.cli.map {
                it.asPath.toString()
            }
        }

        project.tasks.withType<SignPluginTask> {
            inputArchiveFile.convention(project.resolveBuildTaskOutput())
            cliPath.convention(cliPathProvider)
            outputArchiveFile.convention(
                project.layout.file(
                    buildPluginTaskProvider.flatMap { buildPluginTask ->
                        buildPluginTask.archiveFile
                            .map { it.asPath }
                            .map { it.resolveSibling(it.nameWithoutExtension + "-signed." + it.extension).toFile() }
                    })
            )

            onlyIf { (privateKey.isSpecified() || privateKeyFile.isSpecified()) && (certificateChain.isSpecified() || certificateChainFile.isSpecified()) }
            dependsOn(Tasks.BUILD_PLUGIN)
            dependsOn(DOWNLOAD_ZIP_SIGNER_TASK_NAME)
        }

        project.tasks.register<VerifyPluginSignatureTask>(VERIFY_PLUGIN_SIGNATURE_TASK_NAME)
        project.tasks.withType<VerifyPluginSignatureTask> {
            inputArchiveFile.convention(signPluginTaskProvider.flatMap { signPluginTask ->
                signPluginTask.outputArchiveFile
            })
            cliPath.convention(signPluginTaskProvider.flatMap { signPluginTask ->
                signPluginTask.cliPath
            })
            certificateChainFile.convention(
                signPluginTaskProvider.flatMap { signPluginTask ->
                    signPluginTask.certificateChainFile
                }.orElse(
                    // workaround due to https://github.com/JetBrains/marketplace-zip-signer/issues/142
                    signPluginTaskProvider.flatMap { signPluginTask ->
                        signPluginTask.certificateChain.map { content ->
                            temporaryDir.resolve("certificate-chain.pem").also {
                                it.writeText(content)
                            }
                        }
                    }.let {
                        project.layout.file(it)
                    }
                )
            )

            dependsOn(SIGN_PLUGIN_TASK_NAME)
        }
    }

    private fun configurePublishPluginTask(project: Project) {
        info(context, "Configuring publish plugin task")

        val signPluginTaskProvider = project.tasks.named<SignPluginTask>(SIGN_PLUGIN_TASK_NAME)

        project.tasks.register<PublishPluginTask>(PUBLISH_PLUGIN_TASK_NAME)
        project.tasks.withType<PublishPluginTask> {
            val isOffline = project.gradle.startParameter.isOffline

            host.convention(MARKETPLACE_HOST)
            toolboxEnterprise.convention(false)
            channels.convention(listOf("default"))

            distributionFile.convention(
                signPluginTaskProvider
                    .flatMap { signPluginTask ->
                        when (signPluginTask.didWork) {
                            true -> signPluginTask.outputArchiveFile
                            else -> project.resolveBuildTaskOutput()
                        }
                    }
            )

            dependsOn(Tasks.BUILD_PLUGIN)
            dependsOn(Tasks.VERIFY_PLUGIN)
            dependsOn(SIGN_PLUGIN_TASK_NAME)
            onlyIf { !isOffline }
        }
    }

    private fun configureProcessResources(project: Project) {
        info(context, "Configuring resources task")
        val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(Tasks.PATCH_PLUGIN_XML)

        project.tasks.named<ProcessResources>(PROCESS_RESOURCES_TASK_NAME) {
            from(patchPluginXmlTaskProvider) {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                into("META-INF")
            }
        }
    }

    private fun configurePluginDependencies(
        project: Project,
        ideaDependencyProvider: Provider<IdeaDependency>,
        extension: IntelliJPluginExtension,
        dependencies: DependencySet,
    ) {
        val ideaDependency = ideaDependencyProvider.get() // TODO fix

        info(context, "Configuring plugin dependencies")
        val ideVersion = IdeVersion.createIdeVersion(ideaDependency.buildNumber)
        val resolver = project.objects.newInstance<PluginDependencyManager>(
            project.gradle.gradleUserHomeDir.canonicalPath,
            ideaDependencyProvider,
            extension.getPluginsRepositories(),
            archiveUtils,
            context,
        )
        extension.plugins.get().forEach {
            info(context, "Configuring plugin: $it")
            if (it is Project) {
                configureProjectPluginDependency(project, it, dependencies, extension)
            } else {
                val pluginDependency = PluginDependencyNotation.parsePluginDependencyString(it.toString())
                if (pluginDependency.id.isEmpty()) {
                    throw BuildException("Failed to resolve plugin: $it")
                }
                val plugin = resolver.resolve(project, pluginDependency)
                    ?: throw BuildException("Failed to resolve plugin $it")
                if (!plugin.isCompatible(ideVersion)) {
                    throw BuildException("Plugin '$it' is not compatible to: ${ideVersion.asString()}")
                }
                configurePluginDependency(project, plugin, extension, dependencies, resolver)
            }
        }
        if (extension.configureDefaultDependencies.get()) {
            configureBuiltinPluginsDependencies(project, dependencies, resolver, extension, ideaDependency)
        }
        verifyJavaPluginDependency(project, ideaDependency, extension.plugins.get())
        extension.getPluginsRepositories().forEach {
            it.postResolve(project, context)
        }
    }

    private fun configureClassPathIndexCleanupTask(project: Project, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring classpath.index cleanup task")

        project.tasks.register<ClasspathIndexCleanupTask>(CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        project.tasks.withType<ClasspathIndexCleanupTask> {
            classpathIndexFiles.from(project.provider {
                (project.extensions.findByName("sourceSets") as SourceSetContainer)
                    .flatMap {
                        it.output.classesDirs + it.output.generatedSourcesDirs + project.files(
                            it.output.resourcesDir
                        )
                    }
                    .mapNotNull { dir ->
                        dir
                            .resolve("classpath.index")
                            .takeIf { it.exists() }
                    }
            })
        }
    }

    private fun Project.resolveBuildTaskOutput() = tasks.named<Zip>(Tasks.BUILD_PLUGIN).flatMap { it.archiveFile }

    /**
     * Strips an [IdeVersion] of components other than SNAPSHOT and * that exceeds a patch, i.e. "excess" in the following
     * version will be stripped: major.minor.patch.excess.SNAPSHOT.
     * This is needed due to recent versions of Android Studio having additional components in its build number; e.g.
     * 2020.3.1-patch-4 has build number AI-203.7717.56.2031.7935034, with these additional components instrumentCode
     * fails because it tries to resolve a non-existent compiler version (203.7717.56.2031.7935034). This function
     * strips it down so that only major, minor, and patch are used.
     */
    private fun IdeVersion.stripExcessComponents() = asStringWithoutProductCode()
        .split(".")
        .filterIndexed { index, component -> index < 3 || component == "SNAPSHOT" || component == "*" }
        .joinToString(prefix = "$productCode-", separator = ".")
        .let(IdeVersion::createIdeVersion)
}
