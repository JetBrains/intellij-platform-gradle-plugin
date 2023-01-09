// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import com.jetbrains.plugin.structure.base.utils.*
import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.ExtensionAware
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPlugin.*
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.*
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.*
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.jetbrains.gradle.ext.IdeaExtPlugin
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig
import org.jetbrains.intellij.BuildFeature.*
import org.jetbrains.intellij.IntelliJPluginConstants.ANNOTATIONS_DEPENDENCY_VERSION
import org.jetbrains.intellij.IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.COMPILE_KOTLIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.DEFAULT_IDEA_VERSION
import org.jetbrains.intellij.IntelliJPluginConstants.DEFAULT_INTELLIJ_REPOSITORY
import org.jetbrains.intellij.IntelliJPluginConstants.DEFAULT_SANDBOX
import org.jetbrains.intellij.IntelliJPluginConstants.DOWNLOAD_IDE_PRODUCT_RELEASES_XML_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.DOWNLOAD_ZIP_SIGNER_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.EXTENSION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.GITHUB_REPOSITORY
import org.jetbrains.intellij.IntelliJPluginConstants.IDEA_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.IDEA_PRODUCTS_RELEASES_URL
import org.jetbrains.intellij.IntelliJPluginConstants.INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.INTELLIJ_DEPENDENCIES
import org.jetbrains.intellij.IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.JAVA_COMPILER_ANT_TASKS_MAVEN_METADATA
import org.jetbrains.intellij.IntelliJPluginConstants.LIST_BUNDLED_PLUGINS_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.MARKETPLACE_HOST
import org.jetbrains.intellij.IntelliJPluginConstants.MINIMAL_SUPPORTED_GRADLE_VERSION
import org.jetbrains.intellij.IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PERFORMANCE_PLUGIN_ID
import org.jetbrains.intellij.IntelliJPluginConstants.PERFORMANCE_TEST_CONFIGURATION_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_ANDROID_STUDIO
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_CLION
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_INTELLIJ_COMMUNITY
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_PHPSTORM
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_PYCHARM
import org.jetbrains.intellij.IntelliJPluginConstants.PLATFORM_TYPE_RIDER
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_ID
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_PATH
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_VERIFIER_REPOSITORY
import org.jetbrains.intellij.IntelliJPluginConstants.PLUGIN_XML_DIR_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PRINT_BUNDLED_PLUGINS_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PRINT_PRODUCTS_RELEASES_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_EAP
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_EAP_CANDIDATE
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT
import org.jetbrains.intellij.IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.RUN_IDE_PERFORMANCE_TEST_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.RUN_IDE_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.SETUP_INSTRUMENT_CODE_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.VERIFY_PLUGIN_CONFIGURATION_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME
import org.jetbrains.intellij.IntelliJPluginConstants.VERSION_LATEST
import org.jetbrains.intellij.dependency.*
import org.jetbrains.intellij.jbr.JbrResolver
import org.jetbrains.intellij.model.MavenMetadata
import org.jetbrains.intellij.model.XmlExtractor
import org.jetbrains.intellij.performanceTest.ProfilerName
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.tasks.*
import org.jetbrains.intellij.utils.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.File
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.jar.Manifest

abstract class IntelliJPlugin : Plugin<Project> {

    private lateinit var archiveUtils: ArchiveUtils
    private lateinit var dependenciesDownloader: DependenciesDownloader
    private lateinit var jbrResolver: JbrResolver
    private lateinit var context: String

    override fun apply(project: Project) {
        context = project.logCategory()

        checkGradleVersion(project)
        checkPluginVersion(project)

        archiveUtils = project.objects.newInstance()
        dependenciesDownloader = project.objects.newInstance(project.gradle.startParameter.isOffline)

        project.plugins.apply(JavaPlugin::class)
        project.plugins.apply(IdeaExtPlugin::class)

        project.pluginManager.withPlugin("org.jetbrains.gradle.plugin.idea-ext") {
            project.idea {
                // IdeaModel.project is available only for root project
                this.project?.settings {
                    taskTriggers {
                        afterSync(SETUP_DEPENDENCIES_TASK_NAME)
                    }
                }
            }
        }

        val extension = project.extensions.create<IntelliJPluginExtension>(EXTENSION_NAME, dependenciesDownloader).apply {
            version.convention(project.provider {
                if (!localPath.isPresent) {
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
                    .dir(DEFAULT_SANDBOX)
                    .map { it.asFile.canonicalPath }
            )
            intellijRepository.convention(DEFAULT_INTELLIJ_REPOSITORY)
            downloadSources.convention(!System.getenv().containsKey("CI"))
            configureDefaultDependencies.convention(true)
            type.convention(PLATFORM_TYPE_INTELLIJ_COMMUNITY)
        }

        jbrResolver = project.objects.newInstance(
            extension.jreRepository,
            archiveUtils,
            dependenciesDownloader,
            context,
        )

        val ideaDependencyProvider = prepareIdeaDependencyProvider(project, extension).memoize()
        configureDependencies(project, extension, ideaDependencyProvider)
        configureTasks(project, extension, ideaDependencyProvider)
    }

    private fun checkGradleVersion(project: Project) {
        if (Version.parse(project.gradle.gradleVersion) < Version.parse(MINIMAL_SUPPORTED_GRADLE_VERSION)) {
            throw PluginInstantiationException("$PLUGIN_NAME requires Gradle $MINIMAL_SUPPORTED_GRADLE_VERSION and higher")
        }
    }

    private fun checkPluginVersion(project: Project) {
        if (!project.isBuildFeatureEnabled(SELF_UPDATE_CHECK)) {
            return
        }
        if (project.gradle.startParameter.isOffline) {
            return
        }
        try {
            val version = getCurrentVersion()
                ?.let(Version::parse)
                .or { Version() }
            val latestVersion = LatestVersionResolver.fromGitHub(PLUGIN_NAME, GITHUB_REPOSITORY)
            if (version < Version.parse(latestVersion)) {
                warn(context, "$PLUGIN_NAME is outdated: $version. Update `$PLUGIN_ID` to: $latestVersion")
            }
        } catch (e: Exception) {
            error(context, e.message.orEmpty(), e)
        }
    }

    private fun configureTasks(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring plugin")
        project.tasks.withType<RunIdeBase>().configureEach {
            prepareConventionMappingsForRunIdeTask(project, extension, ideaDependencyProvider, PREPARE_SANDBOX_TASK_NAME)
        }
        project.tasks.withType<RunIdeForUiTestTask>().configureEach {
            prepareConventionMappingsForRunIdeTask(project, extension, ideaDependencyProvider, PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
        }

        configureSetupDependenciesTask(project, ideaDependencyProvider)
        configureClassPathIndexCleanupTask(project, ideaDependencyProvider)
        configurePatchPluginXmlTask(project, extension, ideaDependencyProvider)
        configureDownloadRobotServerPluginTask(project)
        configurePrepareSandboxTasks(project, extension, ideaDependencyProvider)
        configureListProductsReleasesTask(project, extension)
        configureListBundledPluginsTask(project, ideaDependencyProvider)
        configurePluginVerificationTask(project)
        configureRunIdeTask(project)
        configureRunIdePerformanceTestTask(project, extension)
        configureRunIdeForUiTestsTask(project)
        configureBuildSearchableOptionsTask(project)
        configureJarSearchableOptionsTask(project)
        configureBuildPluginTask(project)
        configureRunPluginVerifierTask(project, extension)
        configureDownloadZipSignerTask(project)
        configureSignPluginTask(project)
        configurePublishPluginTask(project)
        configureProcessResources(project)
        configureInstrumentation(project, extension, ideaDependencyProvider)
        configureVerifyPluginConfigurationTask(project, ideaDependencyProvider)
        assert(!project.state.executed) { "afterEvaluate is a no-op for an executed project" }

        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            project.tasks.withType<KotlinCompile>().configureEach {
                dependsOn(VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)
            }
        }

        project.tasks.withType<JavaCompile>().configureEach {
            dependsOn(VERIFY_PLUGIN_CONFIGURATION_TASK_NAME)
        }

        project.afterEvaluate {
            configureProjectAfterEvaluate(this, extension, ideaDependencyProvider)
        }
    }

    private fun prepareIdeaDependencyProvider(project: Project, extension: IntelliJPluginExtension) = project.provider {
        val configureDefaultDependencies = extension.configureDefaultDependencies.get()
        val downloadSources = extension.downloadSources.get()
        val extraDependencies = extension.extraDependencies.get()
        val ideaDependencyCachePath = extension.ideaDependencyCachePath.orNull.orEmpty()
        val intellijRepository = extension.intellijRepository.get()
        val localPath = extension.localPath.orNull
        val localSourcesPath = extension.localSourcesPath.orNull
        val type = extension.getVersionType().orNull
        val version = extension.getVersionNumber().orNull

        val ideaConfiguration = project.configurations.getByName(IDEA_CONFIGURATION_NAME)

        val dependencyManager = project.objects.newInstance<IdeaDependencyManager>(
            intellijRepository,
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
                dependencyManager.resolveRemote(project, version, type, downloadSources, extraDependencies)
            }

            localPath != null -> {
                info(context, "Using path to locally installed IDE: $localPath")
                dependencyManager.resolveLocal(project, localPath, localSourcesPath)
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

    private fun configureDependencies(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        val idea = project.configurations.create(IDEA_CONFIGURATION_NAME)
            .setVisible(false)

        val ideaPlugins = project.configurations.create(IDEA_PLUGINS_CONFIGURATION_NAME)
            .setVisible(false)
            .withDependencies {
                configurePluginDependencies(project, ideaDependencyProvider, extension, this)
            }

        val defaultDependencies = project.configurations.create(INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME)
            .setVisible(false)
            .withDependencies {
                add(
                    project.dependencies.create(
                        group = "org.jetbrains",
                        name = "annotations",
                        version = ANNOTATIONS_DEPENDENCY_VERSION,
                    )
                )
            }

        val performanceTest = project.configurations.create(PERFORMANCE_TEST_CONFIGURATION_NAME)
            .setVisible(false)
            .withDependencies {
                val resolver = project.objects.newInstance<PluginDependencyManager>(
                    project.gradle.gradleUserHomeDir.canonicalPath,
                    ideaDependencyProvider,
                    extension.getPluginsRepositories(),
                    archiveUtils,
                    context,
                )

                // Check that `runIdePerformanceTest` task was launched
                // Check that `performanceTesting.jar` is absent (that means it's community version)
                // Check that user didn't pass custom version of the performance plugin
                if (
                    RUN_IDE_PERFORMANCE_TEST_TASK_NAME in project.gradle.startParameter.taskNames
                    && extension.plugins.get().none { it is String && it.startsWith(PERFORMANCE_PLUGIN_ID) }
                ) {
                    val bundledPlugins = BuiltinPluginsRegistry.resolveBundledPlugins(ideaDependencyProvider.get().classes.toPath(), context)
                    if (!bundledPlugins.contains(PERFORMANCE_PLUGIN_ID)) {
                        val buildNumber = ideaDependencyProvider.get().buildNumber
                        val resolvedPlugin = resolveLatestPluginUpdate(PERFORMANCE_PLUGIN_ID, buildNumber)
                            ?: throw BuildException("No suitable plugin update found for $PERFORMANCE_PLUGIN_ID:$buildNumber")

                        val plugin = resolver.resolve(project, resolvedPlugin)
                            ?: throw BuildException(with(resolvedPlugin) { "Failed to resolve plugin $id:$version@$channel" })

                        configurePluginDependency(project, plugin, extension, this, resolver)
                    }
                }
            }

        fun Configuration.extend() = extendsFrom(defaultDependencies, idea, ideaPlugins, performanceTest)

        with(project.configurations) {
            getByName(COMPILE_ONLY_CONFIGURATION_NAME).extend()
            getByName(TEST_IMPLEMENTATION_CONFIGURATION_NAME).extend()
            project.pluginManager.withPlugin("java-test-fixtures") {
                getByName("testFixturesCompileOnly").extend()
            }
        }
    }

    private fun configureProjectAfterEvaluate(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        project.subprojects.forEach { subproject ->
            if (subproject.plugins.findPlugin(IntelliJPlugin::class) == null) {
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
                parsePluginXml(path, context)?.dependencies?.forEach {
                    if (it.dependencyId == "com.intellij.modules.java") {
                        throw BuildException("The project depends on 'com.intellij.modules.java' module but doesn't declare a compile dependency on it.\nPlease delete 'depends' tag from '${path}' or add 'java' plugin to Gradle dependencies (e.g. intellij { plugins = ['java'] })")
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
        project.tasks.withType<PrepareSandboxTask>().configureEach {
            configureExternalPlugin(plugin)
        }
    }

    private fun configureProjectPluginTasksDependency(dependency: Project, taskProvider: TaskProvider<PrepareSandboxTask>) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPlugin::class) == null) {
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin not found: ${dependency.plugins}")
        }
        dependency.tasks.named(PREPARE_SANDBOX_TASK_NAME) {
            taskProvider.get().dependsOn(this)
        }
    }

    private fun configureProjectPluginDependency(project: Project, dependency: Project, dependencies: DependencySet, extension: IntelliJPluginExtension) {
        // invoke on demand, when plugins artifacts are needed
        if (dependency.plugins.findPlugin(IntelliJPlugin::class) == null) {
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin not found: ${dependency.plugins}")
        }
        dependencies.add(project.dependencies.create(dependency))

        val prepareSandboxTaskProvider = dependency.tasks.named<PrepareSandboxTask>(PREPARE_SANDBOX_TASK_NAME)
        val dependencyDirectory = prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
            prepareSandboxTask.pluginName.map { pluginName ->
                prepareSandboxTask.destinationDir.resolve(pluginName)
            }
        }

        val pluginDependency = PluginProjectDependency(dependencyDirectory.get(), context)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType<PrepareSandboxTask>().configureEach {
            configureCompositePlugin(pluginDependency)
        }
    }

    private fun configurePatchPluginXmlTask(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring patch plugin.xml task")

        val buildNumberProvider = ideaDependencyProvider.map { it.buildNumber }

        project.tasks.register<PatchPluginXmlTask>(PATCH_PLUGIN_XML_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Patches `plugin.xml` files with values provided to the task."

            version.convention(project.provider {
                project.version.toString()
            })
            pluginXmlFiles.convention(project.provider {
                sourcePluginXmlFiles(project).map(Path::toFile)
            })
            destinationDir.convention(project.layout.buildDirectory.dir(PLUGIN_XML_DIR_NAME))
            outputFiles.convention(pluginXmlFiles.map {
                it.map { file ->
                    destinationDir.get().asFile.resolve(file.name)
                }
            })
            sinceBuild.convention(project.provider {
                if (extension.updateSinceUntilBuild.get()) {
                    val ideVersion = IdeVersion.createIdeVersion(buildNumberProvider.get())
                    "${ideVersion.baselineVersion}.${ideVersion.build}"
                } else {
                    null
                }
            })
            untilBuild.convention(project.provider {
                if (extension.updateSinceUntilBuild.get()) {
                    if (extension.sameSinceUntilBuild.get()) {
                        "${sinceBuild.get()}.*"
                    } else {
                        val ideVersion = IdeVersion.createIdeVersion(buildNumberProvider.get())
                        "${ideVersion.baselineVersion}.*"
                    }
                } else {
                    null
                }
            })
        }
    }

    private fun configurePrepareSandboxTasks(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        val downloadPluginTaskProvider = project.tasks.named<DownloadRobotServerPluginTask>(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)

        configurePrepareSandboxTask(project, extension, ideaDependencyProvider, PREPARE_SANDBOX_TASK_NAME, "")
        configurePrepareSandboxTask(project, extension, ideaDependencyProvider, PREPARE_TESTING_SANDBOX_TASK_NAME, "-test")
        configurePrepareSandboxTask(project, extension, ideaDependencyProvider, PREPARE_UI_TESTING_SANDBOX_TASK_NAME, "-uiTest") {
            it.from(downloadPluginTaskProvider.flatMap { downloadPluginTask ->
                downloadPluginTask.outputDir
            })
            it.dependsOn(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)
        }
    }

    private fun configureDownloadRobotServerPluginTask(project: Project) {
        info(context, "Configuring robot-server download Task")

        project.tasks.register<DownloadRobotServerPluginTask>(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Download `robot-server` plugin."

            version.convention(VERSION_LATEST)
            outputDir.convention(project.layout.buildDirectory.dir("robotServerPlugin"))
            pluginArchive.convention(project.provider {
                val resolvedVersion = resolveRobotServerPluginVersion(version.orNull)
                val (group, name) = getDependency(resolvedVersion).split(':')
                dependenciesDownloader.downloadFromRepository(logCategory(), {
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

    private fun configurePrepareSandboxTask(
        project: Project,
        extension: IntelliJPluginExtension,
        ideaDependencyProvider: Provider<IdeaDependency>,
        taskName: String,
        testSuffix: String,
        configure: ((it: PrepareSandboxTask) -> Unit)? = null,
    ) {
        info(context, "Configuring $taskName task")

        val jarTaskProvider = project.tasks.named<Jar>(JAR_TASK_NAME)
        val runtimeConfiguration = project.configurations.getByName(RUNTIME_CLASSPATH_CONFIGURATION_NAME)

        val ideaDependencyJarFiles = ideaDependencyProvider.map {
            project.files(it.jarFiles)
        }
        val jarArchiveFile = jarTaskProvider.flatMap { jarTask ->
            jarTask.archiveFile
        }
        val runtimeConfigurationFiles = project.provider {
            runtimeConfiguration.allDependencies.flatMap {
                runtimeConfiguration.fileCollection(it)
            }
        }
        val gradleVersion = project.provider {
            project.gradle.gradleVersion
        }
        val projectVersion = project.provider {
            project.version
        }
        val buildSdk = project.provider {
            extension.localPath.flatMap {
                ideaDependencyProvider.map { ideaDependency ->
                    ideaDependency.classes.toPath().let {
                        // Fall back on build number if product-info.json is not present, this is the case for recent versions of Android Studio.
                        ideProductInfo(it)
                            ?.run { "$productCode-$projectVersion" }
                            ?: ideBuildNumber(it)
                    }
                }
            }.orElse(project.provider {
                val version = extension.getVersionNumber()
                val type = extension.getVersionType()
                "$version-$type"
            })
        }

        jarTaskProvider.configure {
            exclude("**/classpath.index")

            manifest.attributes(
                "Created-By" to gradleVersion.map { "Gradle $it" },
                "Build-JVM" to Jvm.current(),
                "Version" to projectVersion,
                "Build-Plugin" to PLUGIN_NAME,
                "Build-Plugin-Version" to getCurrentVersion().or("0.0.0"),
                "Build-OS" to OperatingSystem.current(),
                "Build-SDK" to buildSdk.get(),
            )
        }

        project.tasks.register<PrepareSandboxTask>(taskName) {
            group = PLUGIN_GROUP_NAME
            description = "Prepares sandbox directory with installed plugin and its dependencies."
            duplicatesStrategy = DuplicatesStrategy.FAIL

            pluginName.convention(extension.pluginName)
            pluginJar.convention(jarArchiveFile)
            defaultDestinationDir.convention(extension.sandboxDir.map {
                project.file("$it/plugins$testSuffix")
            })
            configDir.convention(extension.sandboxDir.map {
                "$it/config$testSuffix"
            })
            librariesToIgnore.convention(ideaDependencyJarFiles)
            pluginDependencies.convention(project.provider {
                extension.getPluginDependenciesList(project)
            })

            intoChild(pluginName.map { "$it/lib" })
                .from(runtimeConfigurationFiles.map { files ->
                    val librariesToIgnore = librariesToIgnore.get().toSet() + Jvm.current().toolsJar
                    val pluginDirectories = pluginDependencies.get().map { it.artifact.canonicalPath }

                    listOf(pluginJar.get().asFile) + files.filter { file ->
                        !(librariesToIgnore.contains(file) || pluginDirectories.any { p ->
                            file.canonicalPath == p || file.canonicalPath.startsWith("$p${File.separator}")
                        })
                    }
                })
                .eachFile {
                    name = ensureName(file.toPath())
                }

            dependsOn(runtimeConfiguration)
            dependsOn(JAR_TASK_NAME)

            configure?.invoke(this)
        }.let { taskProvider ->
            project.afterEvaluate {
                extension.plugins.get().filterIsInstance<Project>().forEach { dependency ->
                    if (dependency.state.executed) {
                        configureProjectPluginTasksDependency(dependency, taskProvider)
                    } else {
                        dependency.afterEvaluate {
                            configureProjectPluginTasksDependency(dependency, taskProvider)
                        }
                    }
                }
            }
        }
    }

    private fun configureRunPluginVerifierTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring run plugin verifier task")

        val listProductsReleasesTaskProvider = project.tasks.named<ListProductsReleasesTask>(LIST_PRODUCTS_RELEASES_TASK_NAME)
        val runIdeTaskProvider = project.tasks.named<RunIdeTask>(RUN_IDE_TASK_NAME)
        val userHomeProvider = project.providers.systemProperty("user.home")

        project.tasks.register<RunPluginVerifierTask>(RUN_PLUGIN_VERIFIER_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IDE builds."

            failureLevel.convention(EnumSet.of(RunPluginVerifierTask.FailureLevel.COMPATIBILITY_PROBLEMS))
            verifierVersion.convention(VERSION_LATEST)
            distributionFile.convention(resolveBuildTaskOutput(project))
            verificationReportsDir.convention(
                project.layout.buildDirectory.dir("reports/pluginVerifier").map { it.asFile.canonicalPath }
            )
            downloadDir.convention(ideDownloadDir().map {
                it.toFile().invariantSeparatorsPath
            })
            teamCityOutputFormat.convention(false)
            subsystemsToCheck.convention("all")
            ideDir.convention(runIdeTaskProvider.flatMap { runIdeTask ->
                runIdeTask.ideDir
            })
            productsReleasesFile.convention(listProductsReleasesTaskProvider.flatMap { listProductsReleasesTask ->
                listProductsReleasesTask.outputFile.asFile
            })
            ides.convention(ideVersions.map { ideVersions ->
                val userHomePath = Path.of(userHomeProvider.get())
                val downloadPath = with(downloadDir.get()) {
                    when {
                        startsWith("~/") -> userHomePath.resolve(removePrefix("~/"))
                        equals("~") -> userHomePath
                        else -> Path.of(this)
                    }
                }

                ideVersions
                    .ifEmpty {
                        when {
                            localPaths.get().isEmpty() -> productsReleasesFile.get().takeIf(File::exists)?.readLines()
                            else -> null
                        }
                    }
                    .orEmpty()
                    .map { ideVersion ->
                        val context = logCategory()

                        resolveIdePath(ideVersion, downloadPath, context) { type, version, buildType ->
                            val name = "$type-$version"
                            val ideDir = downloadPath.resolve(name)
                            info(context, "Downloading IDE '$name' to: $ideDir")

                            val url = resolveIdeUrl(type, version, buildType, context)
                            val dependencyVersion = listOf(type, version, buildType).filterNot(String::isNullOrEmpty).joinToString("-")
                            val group = when (type) {
                                PLATFORM_TYPE_ANDROID_STUDIO -> "com.android"
                                else -> "com.jetbrains"
                            }
                            debug(context, "Downloading IDE from $url")

                            try {
                                val ideArchive = dependenciesDownloader.downloadFromRepository(context, {
                                    create(
                                        group = group,
                                        name = "ides",
                                        version = dependencyVersion,
                                        ext = "tar.gz",
                                    )
                                }, {
                                    ivyRepository(url)
                                }).first()

                                debug(context, "IDE downloaded, extracting...")
                                archiveUtils.extract(ideArchive.toPath(), ideDir, context) // FIXME ideArchive.toPath()
                                ideDir.listFiles().let { files ->
                                    files.filter { it.isDirectory }.forEach { container ->
                                        container.listFiles().forEach { file ->
                                            Files.move(file, ideDir.resolve(file.simpleName))
                                        }
                                        container.forceRemoveDirectory()
                                    }
                                }
                            } catch (e: Exception) {
                                warn(context, "Cannot download '$type-$version' from '$buildType' channel: $url", e)
                            }

                            debug(context, "IDE extracted to: $ideDir")
                            ideDir
                        }
                    }.let { files -> project.files(files) }
            })
            verifierPath.convention(project.provider {
                val resolvedVerifierVersion = resolveVerifierVersion(verifierVersion.orNull)
                debug(context, "Using Verifier in '$resolvedVerifierVersion' version")

                dependenciesDownloader.downloadFromRepository(logCategory(), {
                    create(
                        group = "org.jetbrains.intellij.plugins",
                        name = "verifier-cli",
                        version = resolvedVerifierVersion,
                        classifier = "all",
                        ext = "jar",
                    )
                }, {
                    mavenRepository(PLUGIN_VERIFIER_REPOSITORY)
                }).first().canonicalPath
            })
            jreRepository.convention(extension.jreRepository)
            offline.set(project.gradle.startParameter.isOffline)
            resolvedRuntimeDir.convention(project.provider {
                resolveRuntimeDir(jbrResolver)
            })

            dependsOn(BUILD_PLUGIN_TASK_NAME)
            dependsOn(VERIFY_PLUGIN_TASK_NAME)
            dependsOn(LIST_PRODUCTS_RELEASES_TASK_NAME)

            val isIdeVersionsEmpty = localPaths.flatMap { localPaths ->
                ideVersions.map { ideVersions ->
                    localPaths.isEmpty() && ideVersions.isEmpty()
                }
            }
            listProductsReleasesTaskProvider.get().onlyIf { isIdeVersionsEmpty.get() }
        }
    }

    private fun configurePluginVerificationTask(project: Project) {
        info(context, "Configuring plugin verification task")

        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(PREPARE_SANDBOX_TASK_NAME)

        project.tasks.register<VerifyPluginTask>(VERIFY_PLUGIN_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Validates completeness and contents of `plugin.xml` descriptors as well as plugin archive structure."

            ignoreFailures.convention(false)
            ignoreUnacceptableWarnings.convention(false)
            ignoreWarnings.convention(true)

            pluginDir.convention(
                project.layout.dir(
                    prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                        prepareSandboxTask.pluginName.map { pluginName ->
                            prepareSandboxTask.destinationDir.resolve(pluginName)
                        }
                    }
                )
            )

            dependsOn(PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private fun configureVerifyPluginConfigurationTask(project: Project, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring plugin configuration verification task")

        val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(PATCH_PLUGIN_XML_TASK_NAME)
        val runPluginVerifierTaskProvider = project.tasks.named<RunPluginVerifierTask>(RUN_PLUGIN_VERIFIER_TASK_NAME)
        val compileJavaTaskProvider = project.tasks.named<JavaCompile>(COMPILE_JAVA_TASK_NAME)
        val stdlibDefaultDependencyProvider = project.providers.gradleProperty("kotlin.stdlib.default.dependency").map {
            it.toBoolean()
        }

        val downloadDirProvider = runPluginVerifierTaskProvider.flatMap { runPluginVerifierTask ->
            runPluginVerifierTask.downloadDir
        }

        project.tasks.register<VerifyPluginConfigurationTask>(VERIFY_PLUGIN_CONFIGURATION_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Checks if Java and Kotlin compilers configuration meet IntelliJ SDK requirements"

            platformBuild.convention(ideaDependencyProvider.map {
                it.buildNumber
            })
            platformVersion.convention(ideaDependencyProvider.map {
                it.version
            })
            pluginXmlFiles.convention(patchPluginXmlTaskProvider.flatMap { patchPluginXmlTask ->
                patchPluginXmlTask.outputFiles
            })
            sourceCompatibility.convention(compileJavaTaskProvider.map {
                it.sourceCompatibility
            })
            targetCompatibility.convention(compileJavaTaskProvider.map {
                it.targetCompatibility
            })
            pluginVerifierDownloadDir.convention(downloadDirProvider)

            kotlinPluginAvailable.convention(project.provider {
                project.pluginManager.hasPlugin("org.jetbrains.kotlin.jvm")
            })
            project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
                val compileKotlinTaskProvider = project.tasks.named<KotlinCompile>(COMPILE_KOTLIN_TASK_NAME)

                kotlinJvmTarget.convention(project.provider {
                    compileKotlinTaskProvider.get().kotlinOptions.jvmTarget
                })
                kotlinApiVersion.convention(project.provider {
                    compileKotlinTaskProvider.get().kotlinOptions.apiVersion
                })
                kotlinLanguageVersion.convention(project.provider {
                    compileKotlinTaskProvider.get().kotlinOptions.languageVersion
                })
                kotlinStdlibDefaultDependency.convention(stdlibDefaultDependencyProvider)
            }

            dependsOn(PATCH_PLUGIN_XML_TASK_NAME)
        }
    }

    private fun configureRunIdeTask(project: Project) {
        info(context, "Configuring run IDE task")

        project.tasks.register<RunIdeTask>(RUN_IDE_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Runs the IDE instance with the developed plugin installed."

            dependsOn(PREPARE_SANDBOX_TASK_NAME)
            finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        }
    }

    private fun configureRunIdePerformanceTestTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring run IDE performance test task")

        project.tasks.register<RunIdePerformanceTestTask>(RUN_IDE_PERFORMANCE_TEST_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Runs performance tests on the IDE with the developed plugin installed."

            artifactsDir.convention(extension.type.flatMap { type ->
                extension.version.map { version ->
                    project.buildDir.resolve(
                        "reports/performance-test/$type$version-${project.version}-${
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                        }"
                    ).canonicalPath
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

        project.tasks.register<RunIdeForUiTestTask>(RUN_IDE_FOR_UI_TESTS_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Runs the IDE instance with the developed plugin and robot-server installed and ready for UI testing."

            dependsOn(PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
            finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        }
    }

    private fun configureBuildSearchableOptionsTask(project: Project) {
        info(context, "Configuring build searchable options task")

        project.tasks.register<BuildSearchableOptionsTask>(BUILD_SEARCHABLE_OPTIONS_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Builds an index of UI components (searchable options) for the plugin."

            outputDir.convention(project.layout.buildDirectory.dir(SEARCHABLE_OPTIONS_DIR_NAME))
            showPaidPluginWarning.convention(project.provider {
                project.isBuildFeatureEnabled(PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING) && run {
                    sourcePluginXmlFiles(project).any {
                        parsePluginXml(it, context)?.productDescriptor != null
                    }
                }
            })

            dependsOn(PREPARE_SANDBOX_TASK_NAME)

            onlyIf {
                val number = ideBuildNumber(ideDir.get().toPath())
                Version.parse(number.split('-').last()) >= Version.parse("191.2752")
            }
        }
    }

    private fun RunIdeBase.prepareConventionMappingsForRunIdeTask(
        project: Project,
        extension: IntelliJPluginExtension,
        ideaDependencyProvider: Provider<IdeaDependency>,
        prepareSandBoxTaskName: String,
    ) {
        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(prepareSandBoxTaskName)

        val taskContext = logCategory()
        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it, taskContext)?.id }

        ideDir.convention(ideaDependencyProvider.map {
            project.file(it.classes.path)
        })
        requiredPluginIds.convention(project.provider {
            pluginIds
        })
        configDir.convention(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
            prepareSandboxTask.configDir.map { project.file(it) }
        })
        pluginsDir.convention(prepareSandboxTaskProvider.map { prepareSandboxTask ->
            project.layout.projectDirectory.dir(prepareSandboxTask.destinationDir.path)
        })
        systemDir.convention(extension.sandboxDir.map {
            project.file("$it/system")
        })
        autoReloadPlugins.convention(ideDir.map {
            val number = ideBuildNumber(it.toPath())
            Version.parse(number.split('-').last()) >= Version.parse("202.0")
        })
        projectWorkingDir.convention(ideDir.map {
            it.resolve("bin")
        })
        projectExecutable.convention(project.provider {
            jbrResolver.resolveRuntime(
                jbrVersion = jbrVersion.orNull,
                jbrVariant = jbrVariant.orNull,
                jbrArch = jbrArch.orNull,
                ideDir = ideDir.orNull,
            ).toString()
        })
    }

    private fun configureJarSearchableOptionsTask(project: Project) {
        info(context, "Configuring jar searchable options task")

        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(PREPARE_SANDBOX_TASK_NAME)

        project.tasks.register<JarSearchableOptionsTask>(JAR_SEARCHABLE_OPTIONS_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Creates a JAR file with searchable options to be distributed with the plugin."

            outputDir.convention(project.layout.buildDirectory.dir(SEARCHABLE_OPTIONS_DIR_NAME))
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
            dependsOn(PREPARE_SANDBOX_TASK_NAME)
            onlyIf { outputDir.get().asFile.isDirectory }
        }
    }

    private fun configureInstrumentation(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring compile tasks")

        val jarTaskProvider = project.tasks.named<Jar>(JAR_TASK_NAME)

        if (extension.instrumentCode.get()) {
            jarTaskProvider.configure { duplicatesStrategy = DuplicatesStrategy.EXCLUDE }
        }

        val setupInstrumentCodeTaskProvider = project.tasks.register<SetupInstrumentCodeTask>(SETUP_INSTRUMENT_CODE_TASK_NAME) {
            description = "Prepares code instrumentation tasks."
            group = PLUGIN_GROUP_NAME

            instrumentationEnabled.convention(extension.instrumentCode)
            instrumentedDir.convention(project.layout.buildDirectory.dir("instrumented"))
        }

        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
        sourceSets.forEach { sourceSet ->
            val name = sourceSet.getTaskName("instrument", "code")
            val instrumentTaskProvider = project.tasks.register<IntelliJInstrumentCodeTask>(name) {
                description = "Code instrumentation task."
                group = PLUGIN_GROUP_NAME

                val instrumentCodeProvider = project.provider { extension.instrumentCode.get() }

                sourceDirs.from(project.provider {
                    sourceSet.allJava.srcDirs
                })
                formsDirs.from(project.provider {
                    sourceDirs.asFileTree.filter {
                        // TODO: migrate to Path
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
                    val productInfo = ideProductInfo(it.classes.toPath())

                    val version = extension.getVersionNumber().orNull.orEmpty()
                    val type = extension.getVersionType().orNull.orEmpty()
                    val localPath = extension.localPath.orNull.orEmpty()
                    val types = listOf(
                        PLATFORM_TYPE_CLION,
                        PLATFORM_TYPE_RIDER,
                        PLATFORM_TYPE_PYCHARM,
                        PLATFORM_TYPE_PHPSTORM,
                    )

                    when {
                        localPath.isNotBlank() || !version.endsWith(RELEASE_SUFFIX_SNAPSHOT) -> {
                            val eapSuffix = RELEASE_SUFFIX_EAP.takeIf { productInfo?.versionSuffix == "EAP" }.orEmpty()
                            IdeVersion.createIdeVersion(it.buildNumber).stripExcessComponents().asStringWithoutProductCode() + eapSuffix
                        }

                        version == DEFAULT_IDEA_VERSION && types.contains(type) -> {
                            productInfo?.buildNumber?.let { buildNumber ->
                                Version.parse(buildNumber).let { v -> "${v.major}.${v.minor}$RELEASE_SUFFIX_EAP_CANDIDATE" }
                            } ?: version
                        }

                        else -> {
                            val prefix = when (type) {
                                PLATFORM_TYPE_CLION -> "CLION-"
                                PLATFORM_TYPE_RIDER -> "RIDER-"
                                PLATFORM_TYPE_PYCHARM -> "PYCHARM-"
                                PLATFORM_TYPE_PHPSTORM -> "PHPSTORM-"
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
                            dependenciesDownloader.downloadFromMultipleRepositories(logCategory(), {
                                create(
                                    group = "com.jetbrains.intellij.java",
                                    name = "java-compiler-ant-tasks",
                                    version = version,
                                )
                            }, {
                                listOf(
                                    "${extension.intellijRepository.get()}/${releaseType(version)}",
                                    INTELLIJ_DEPENDENCIES,
                                ).map(::mavenRepository)
                            }, true)
                        }

                        listOf(
                            {
                                runCatching { downloadCompiler(compilerVersion) }.fold(
                                    onSuccess = { it },
                                    onFailure = {
                                        warn(logCategory(), "Cannot resolve java-compiler-ant-tasks in version: $compilerVersion")
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
                                            warn(logCategory(), "Resolved non-EAP java-compiler-ant-tasks version: $nonEapVersion")
                                            it
                                        },
                                        onFailure = {
                                            warn(logCategory(), "Cannot resolve java-compiler-ant-tasks in version: $nonEapVersion")
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
                                        ?.map(Version::parse)?.filter { it <= version }
                                        ?.maxOf { it }
                                        ?.version
                                }

                                if (closestCompilerVersion == null) {
                                    warn(logCategory(), "Cannot resolve java-compiler-ant-tasks Maven metadata")
                                    null
                                } else {
                                    runCatching { downloadCompiler(closestCompilerVersion) }.fold(
                                        onSuccess = {
                                            warn(logCategory(), "Resolved closest lower java-compiler-ant-tasks version: $closestCompilerVersion")
                                            it
                                        },
                                        onFailure = {
                                            warn(logCategory(), "Cannot resolve java-compiler-ant-tasks in version: $closestCompilerVersion")
                                            null
                                        },
                                    )
                                }
                            },
                        ).asSequence().mapNotNull { it() }.firstOrNull().orEmpty()
                    } else {
                        warn(
                            logCategory(),
                            "Compiler in '$compilerVersion' version can't be resolved from Maven. Minimal version supported: 2018.3+. Use higher 'intellij.version' or specify the 'compilerVersion' property manually.",
                        )
                        emptyList()
                    }
                })

                outputDir.convention(setupInstrumentCodeTaskProvider.get().instrumentedDir.map { it.dir(name) })

                dependsOn(sourceSet.classesTaskName)
                dependsOn(SETUP_INSTRUMENT_CODE_TASK_NAME)
                onlyIf { instrumentCodeProvider.get() }
            }

            // A dedicated task ensures that sources substitution is always run,
            // even when the instrumentCode task is up-to-date.
            val postInstrumentName = "post${name.capitalize()}"
            val updateTask = project.tasks.register(postInstrumentName) {
                description = "Code post-instrumentation task."
                group = PLUGIN_GROUP_NAME

                val instrumentTask = instrumentTaskProvider.get()
                val instrumentCodeProvider = project.provider { extension.instrumentCode.get() && instrumentTask.isEnabled }
                val classesDirs = sourceSet.output.classesDirs as ConfigurableFileCollection
                val outputDir = project.provider { instrumentTask.outputDir.get() }

                onlyIf { instrumentCodeProvider.get() }
                doLast { classesDirs.setFrom(outputDir.get()) }

                dependsOn(instrumentTask)
                finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)
            }

            // Ensure that our task is invoked when the source set is built
            sourceSet.compiledBy(updateTask)
        }
    }

    private fun configureTestTasks(project: Project, extension: IntelliJPluginExtension, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring tests tasks")
        val runIdeTaskProvider = project.tasks.named<RunIdeTask>(RUN_IDE_TASK_NAME)
        val prepareTestingSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(PREPARE_TESTING_SANDBOX_TASK_NAME)

        val testTasks = project.tasks.withType<Test>()
        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it, context)?.id }
        val buildNumberProvider = ideaDependencyProvider.map {
            it.buildNumber
        }
        val ideDirProvider = runIdeTaskProvider.flatMap { runIdeTask ->
            runIdeTask.ideDir.map { it.toPath() }
        }

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

        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
        val sourceSetsOutputs = project.provider {
            project.files(sourceSets.map {
                it.output.run { classesDirs + generatedSourcesDirs + resourcesDir }
            })
        }

        val ideaConfigurationFiles = project.provider {
            project.files(project.configurations.getByName(IDEA_CONFIGURATION_NAME).resolve())
        }
        val ideaPluginsConfigurationFiles = project.provider {
            project.files(project.configurations.getByName(IDEA_PLUGINS_CONFIGURATION_NAME).resolve())
        }
        val ideaClasspathFiles = ideDirProvider.map {
            project.files(getIdeaClasspath(it))
        }

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
            // the same as previous – setting appClassLoader but outdated. Works for part of 203 builds.
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

            dependsOn(PREPARE_TESTING_SANDBOX_TASK_NAME)
            finalizedBy(CLASSPATH_INDEX_CLEANUP_TASK_NAME)

            doFirst {
                jvmArgs = getIdeaJvmArgs((this as Test), jvmArgs, ideDirProvider.get())
                classpath += ideaDependencyLibrariesProvider.get()
                classpath -= classpath.filter { !it.toPath().isDirectory && !it.toPath().isJar() }

                // Rearrange classpath to put idea and plugins in the right order.
                classpath -= ideaConfigurationFiles.get()
                classpath -= ideaPluginsConfigurationFiles.get()
                classpath += ideaConfigurationFiles.get() + ideaPluginsConfigurationFiles.get()

                // Add source roots to the classpath.
                classpath += sourceSetsOutputs.get()
                classpath += ideaClasspathFiles.get()


                systemProperties(
                    getIdeaSystemProperties(
                        ideDirProvider.get(),
                        configDirectoryProvider.get(),
                        systemDirectoryProvider.get(),
                        pluginsDirectoryProvider.get(),
                        pluginIds,
                    )
                )

                // since 193 plugins from classpath are loaded before plugins from plugins directory
                // to handle this, use plugin.path property as task's the very first source of plugins
                // we cannot do this for IDEA < 193, as plugins from plugin.path can be loaded twice
                val ideVersion = IdeVersion.createIdeVersion(buildNumberProvider.get())
                if (ideVersion.baselineVersion >= 193) {
                    systemProperty(
                        PLUGIN_PATH,
                        pluginsDirectoryProvider.get()
                            .listFiles()
                            ?.joinToString("${File.pathSeparator},") { it.path }
                            .orEmpty(),
                    )
                }

                if (ideVersion.baselineVersion >= 221) {
                    systemProperty("java.system.class.loader", "com.intellij.util.lang.PathClassLoader")
                }
            }
        }
    }

    private fun configureBuildPluginTask(project: Project) {
        info(context, "Configuring building plugin task")

        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(PREPARE_SANDBOX_TASK_NAME)
        val jarSearchableOptionsTaskProvider = project.tasks.named<JarSearchableOptionsTask>(JAR_SEARCHABLE_OPTIONS_TASK_NAME)

        project.tasks.register<Zip>(BUILD_PLUGIN_TASK_NAME) {
            description = "Assembles plugin and prepares ZIP archive for deployment."
            group = PLUGIN_GROUP_NAME

            archiveBaseName.convention(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName
            })

            from(prepareSandboxTaskProvider.flatMap { prepareSandboxTask ->
                prepareSandboxTask.pluginName.map {
                    "${prepareSandboxTask.destinationDir}/$it"
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

            dependsOn(JAR_SEARCHABLE_OPTIONS_TASK_NAME)
            dependsOn(PREPARE_SANDBOX_TASK_NAME)

            val archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
            ArchivePublishArtifact(this).let { zipArtifact ->
                archivesConfiguration.artifacts.add(zipArtifact)
                project.extensions.getByType<DefaultArtifactPublicationSet>().addCandidate(zipArtifact)
                project.components.add(IntelliJPluginLibrary())
            }
        }
    }

    private fun configureDownloadZipSignerTask(project: Project) {
        project.tasks.register<DownloadZipSignerTask>(DOWNLOAD_ZIP_SIGNER_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Downloads marketplace-zip-signer library."

            val context = logCategory()

            version.convention(VERSION_LATEST)
            cliPath.convention(version.map {
                val resolvedCliVersion = resolveCliVersion(version.orNull)
                val url = resolveCliUrl(resolvedCliVersion)
                debug(context, "Using Marketplace ZIP Signer CLI in '$resolvedCliVersion' version")

                dependenciesDownloader.downloadFromRepository(context, {
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

        val buildPluginTaskProvider = project.tasks.named<Zip>(BUILD_PLUGIN_TASK_NAME)
        val downloadZipSignerTaskProvider = project.tasks.named<DownloadZipSignerTask>(DOWNLOAD_ZIP_SIGNER_TASK_NAME)

        project.tasks.register<SignPluginTask>(SIGN_PLUGIN_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Signs the ZIP archive with the provided key using marketplace-zip-signer library."

            inputArchiveFile.convention(resolveBuildTaskOutput(project))
            outputArchiveFile.convention(
                project.layout.file(
                    buildPluginTaskProvider.flatMap { buildPluginTask ->
                        buildPluginTask.archiveFile
                            .map { it.asPath }
                            .map { it.resolveSibling(it.nameWithoutExtension + "-signed." + it.extension).toFile() }
                    })
            )
            cliPath.convention(downloadZipSignerTaskProvider.flatMap { downloadZipSignerTask ->
                downloadZipSignerTask.cli.map {
                    it.asPath.toString()
                }
            })

            onlyIf { (privateKey.isPresent || privateKeyFile.isPresent) && (certificateChain.isPresent || certificateChainFile.isPresent) }
            dependsOn(BUILD_PLUGIN_TASK_NAME)
            dependsOn(DOWNLOAD_ZIP_SIGNER_TASK_NAME)
        }
    }

    private fun configurePublishPluginTask(project: Project) {
        info(context, "Configuring publish plugin task")

        val signPluginTaskProvider = project.tasks.named<SignPluginTask>(SIGN_PLUGIN_TASK_NAME)

        project.tasks.register<PublishPluginTask>(PUBLISH_PLUGIN_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Publishes plugin to the remote Marketplace repository."

            val isOffline = project.gradle.startParameter.isOffline

            host.convention(MARKETPLACE_HOST)
            toolboxEnterprise.convention(false)
            channels.convention(listOf("default"))

            distributionFile.convention(
                signPluginTaskProvider
                    .flatMap { signPluginTask ->
                        when (signPluginTask.didWork) {
                            true -> signPluginTask.outputArchiveFile
                            else -> resolveBuildTaskOutput(project)
                        }
                    }
            )

            dependsOn(BUILD_PLUGIN_TASK_NAME)
            dependsOn(VERIFY_PLUGIN_TASK_NAME)
            dependsOn(SIGN_PLUGIN_TASK_NAME)
            onlyIf { !isOffline }
        }
    }

    private fun configureListProductsReleasesTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring list products task")

        val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(PATCH_PLUGIN_XML_TASK_NAME)

        val downloadIdeaProductReleasesXml = project.tasks.register<Sync>(DOWNLOAD_IDE_PRODUCT_RELEASES_XML_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            from(project.resources.text.fromUri(IDEA_PRODUCTS_RELEASES_URL)) {
                rename { "idea_product_releases.xml" }
            }
            into(temporaryDir)
        }

        val listProductsReleasesTaskProvider = project.tasks.register<ListProductsReleasesTask>(LIST_PRODUCTS_RELEASES_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "List all available IntelliJ-based IDE releases with their updates."

            productsReleasesUpdateFiles
                .from(updatePaths)
                .from(downloadIdeaProductReleasesXml.map {
                    it.outputs.files.asFileTree
                })
            androidStudioUpdatePath.convention(project.provider {
                dependenciesDownloader.getAndroidStudioReleases(logCategory()).toString()
            })
            outputFile.convention(
                project.layout.buildDirectory.file("$LIST_PRODUCTS_RELEASES_TASK_NAME.txt")
            )
            types.convention(extension.type.map { listOf(it) })
            sinceBuild.convention(patchPluginXmlTaskProvider.flatMap { it.sinceBuild })
            untilBuild.convention(patchPluginXmlTaskProvider.flatMap { it.untilBuild })
            releaseChannels.convention(EnumSet.allOf(ListProductsReleasesTask.Channel::class.java))

            dependsOn(PATCH_PLUGIN_XML_TASK_NAME)
        }

        project.tasks.register<PrintProductsReleasesTask>(PRINT_PRODUCTS_RELEASES_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Prints all available IntelliJ-based IDE releases with their updates."

            inputFile.convention(listProductsReleasesTaskProvider.flatMap { listProductsReleasesTaskProvider ->
                listProductsReleasesTaskProvider.outputFile
            })

            dependsOn(LIST_PRODUCTS_RELEASES_TASK_NAME)
        }
    }

    private fun configureListBundledPluginsTask(project: Project, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring list bundled plugins task")

        val listBundledPluginsTaskProvider = project.tasks.register<ListBundledPluginsTask>(LIST_BUNDLED_PLUGINS_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "List bundled plugins within the currently targeted IntelliJ-based IDE release."

            ideDir.convention(ideaDependencyProvider.map {
                project.file(it.classes.path)
            })
            outputFile.convention(
                project.layout.buildDirectory.file("$LIST_BUNDLED_PLUGINS_TASK_NAME.txt")
            )
        }

        project.tasks.register<PrintBundledPluginsTask>(PRINT_BUNDLED_PLUGINS_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Prints bundled plugins within the currently targeted IntelliJ-based IDE release."

            inputFile.convention(listBundledPluginsTaskProvider.flatMap { listBundledPluginsTask ->
                listBundledPluginsTask.outputFile
            })

            dependsOn(LIST_BUNDLED_PLUGINS_TASK_NAME)
        }
    }

    @Suppress("UnstableApiUsage")
    private fun configureProcessResources(project: Project) {
        info(context, "Configuring resources task")
        val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(PATCH_PLUGIN_XML_TASK_NAME)

        project.tasks.named<ProcessResources>(PROCESS_RESOURCES_TASK_NAME) {
            from(patchPluginXmlTaskProvider) {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                into("META-INF")
            }
        }
    }

    private fun configureSetupDependenciesTask(project: Project, ideaDependencyProvider: Provider<IdeaDependency>) {
        info(context, "Configuring setup dependencies task")

        project.tasks.register<SetupDependenciesTask>(SETUP_DEPENDENCIES_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Sets up required dependencies for building and running project."

            idea.convention(ideaDependencyProvider)

            Jvm.current().toolsJar?.let { toolsJar ->
                project.dependencies.add(RUNTIME_ONLY_CONFIGURATION_NAME, project.files(toolsJar))
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

        project.tasks.register<ClasspathIndexCleanupTask>(CLASSPATH_INDEX_CLEANUP_TASK_NAME) {
            group = PLUGIN_GROUP_NAME
            description = "Removes classpath index files created by PathClassLoader"

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

            val buildNumberProvider = ideaDependencyProvider.map {
                it.buildNumber
            }

            onlyIf {
                val ideVersion = IdeVersion.createIdeVersion(buildNumberProvider.get())
                ideVersion.baselineVersion >= 221
            }
        }
    }

    private fun resolveBuildTaskOutput(project: Project) = project.tasks.named<Zip>(BUILD_PLUGIN_TASK_NAME).flatMap { it.archiveFile }

    private fun getCurrentVersion() = IntelliJPlugin::class.java
        .run { getResource("$simpleName.class") }
        .runCatching {
            val manifestPath = with(this?.path) {
                when {
                    this == null -> return@runCatching null
                    startsWith("jar:") -> this
                    startsWith("file:") -> "jar:$this"
                    else -> return@runCatching null
                }
            }.run { substring(0, lastIndexOf("!") + 1) } + "/META-INF/MANIFEST.MF"
            info(context, "Resolving Gradle IntelliJ Plugin version with: $manifestPath")
            URL(manifestPath).openStream().use {
                Manifest(it).mainAttributes.getValue("Version")
            }
        }.getOrNull()

    private fun Project.idea(
        action: IdeaModel.() -> Unit,
    ) = extensions.configure("idea", action)

    private fun IdeaProject.settings(
        action: ProjectSettings.() -> Unit,
    ) = (this as ExtensionAware).extensions.configure("settings", action)

    private fun ProjectSettings.taskTriggers(
        action: TaskTriggersConfig.() -> Unit,
    ) = (this as ExtensionAware).extensions.configure("taskTriggers", action)

    /**
     * Strips an [IdeVersion] of components other than SNAPSHOT and * that exceeds patch, i.e. "excess" in the following
     * version will be stripped: major.minor.patch.excess.SNAPSHOT.
     * This is needed due to recent versions of Android Studio having additional components in its build number; e.g.
     * 2020.3.1-patch-4 has build number AI-203.7717.56.2031.7935034, with these additional components instrumentCode
     * fails because it tries to resolve a non-existent compiler version (203.7717.56.2031.7935034). This function
     * strips it down so that only major minor and patch are used.
     */
    private fun IdeVersion.stripExcessComponents() = asStringWithoutProductCode()
        .split(".")
        .filterIndexed { index, component -> index < 3 || component == "SNAPSHOT" || component == "*" }
        .joinToString(prefix = "$productCode-", separator = ".")
        .let(IdeVersion::createIdeVersion)
}
