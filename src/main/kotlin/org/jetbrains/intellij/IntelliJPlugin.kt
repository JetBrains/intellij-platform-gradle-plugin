// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.GradleException
import org.gradle.api.JavaVersion
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
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.plugins.ide.idea.model.IdeaProject
import org.gradle.tooling.BuildException
import org.jetbrains.gradle.ext.IdeaExtPlugin
import org.jetbrains.gradle.ext.ProjectSettings
import org.jetbrains.gradle.ext.TaskTriggersConfig
import org.jetbrains.intellij.BuildFeature.NO_SEARCHABLE_OPTIONS_WARNING
import org.jetbrains.intellij.BuildFeature.PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING
import org.jetbrains.intellij.BuildFeature.SELF_UPDATE_CHECK
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_EAP_CANDIDATE
import org.jetbrains.intellij.IntelliJPluginConstants.RELEASE_SUFFIX_SNAPSHOT
import org.jetbrains.intellij.dependency.IdeaDependency
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginDependencyManager
import org.jetbrains.intellij.dependency.PluginDependencyNotation
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.jbr.JbrResolver
import org.jetbrains.intellij.model.MavenMetadata
import org.jetbrains.intellij.model.XmlExtractor
import org.jetbrains.intellij.performanceTest.ProfilerName
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.tasks.*
import org.jetbrains.intellij.utils.ArchiveUtils
import org.jetbrains.intellij.utils.DependenciesDownloader
import org.jetbrains.intellij.utils.LatestVersionResolver
import org.jetbrains.intellij.utils.OpenedPackages
import org.jetbrains.intellij.utils.ivyRepository
import org.jetbrains.intellij.utils.mavenRepository
import java.io.File
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.EnumSet
import java.util.jar.Manifest

@Suppress("UnstableApiUsage")
open class IntelliJPlugin : Plugin<Project> {

    private lateinit var archiveUtils: ArchiveUtils
    private lateinit var dependenciesDownloader: DependenciesDownloader
    private lateinit var context: String

    override fun apply(project: Project) {
        archiveUtils = project.objects.newInstance(ArchiveUtils::class.java)
        dependenciesDownloader = project.objects.newInstance(DependenciesDownloader::class.java)
        context = project.logCategory()

        checkGradleVersion(project)
        checkPluginVersion(project)

        project.plugins.apply(JavaPlugin::class.java)
        project.plugins.apply(IdeaExtPlugin::class.java)

        project.pluginManager.withPlugin("org.jetbrains.gradle.plugin.idea-ext") {
            project.idea {
                // IdeaModel.project is available only for root project
                this.project?.settings {
                    taskTriggers {
                        afterSync("setupDependencies")
                    }
                }
            }
        }

        val intellijExtension = project.extensions.create(
            IntelliJPluginConstants.EXTENSION_NAME,
            IntelliJPluginExtension::class.java,
        )

        intellijExtension.apply {
            version.convention(project.provider {
                if (!localPath.isPresent) {
                    throw GradleException(
                        "The value for the 'intellij.version' property was not specified, " +
                            "see: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html#intellij-extension-version"
                    )
                }
                null
            })
            pluginName.convention(project.provider {
                project.name
            })
            updateSinceUntilBuild.convention(true)
            sameSinceUntilBuild.convention(false)
            instrumentCode.convention(true)
            sandboxDir.convention(project.provider {
                File(project.buildDir, IntelliJPluginConstants.DEFAULT_SANDBOX).absolutePath
            })
            intellijRepository.convention(IntelliJPluginConstants.DEFAULT_INTELLIJ_REPOSITORY)
            downloadSources.convention(!System.getenv().containsKey("CI"))
            configureDefaultDependencies.convention(true)
            type.convention("IC")
        }

        configureTasks(project, intellijExtension)
    }

    private fun checkGradleVersion(project: Project) {
        if (Version.parse(project.gradle.gradleVersion) < Version.parse("6.7.1")) {
            throw PluginInstantiationException("${IntelliJPluginConstants.NAME} requires Gradle 6.7.1 and higher")
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
            val version = getCurrentVersion()?.let(Version::parse) ?: Version()
            val latestVersion = LatestVersionResolver.fromGitHub(IntelliJPluginConstants.NAME, IntelliJPluginConstants.GITHUB_REPOSITORY)
            if (version < Version.parse(latestVersion)) {
                warn(
                    context,
                    "${IntelliJPluginConstants.NAME} is outdated: $version. Update `${IntelliJPluginConstants.ID}` to: $latestVersion"
                )
            }
        } catch (e: Exception) {
            error(context, e.message.orEmpty(), e)
        }
    }

    private fun checkJavaRuntimeVersion(project: Project, extension: IntelliJPluginExtension) {
        val setupDependenciesTaskProvider = project.tasks.named<SetupDependenciesTask>(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
        val setupDependenciesTask = setupDependenciesTaskProvider.get()

        val javaVersion = Jvm.current().javaVersion
        val platformVersion = setupDependenciesTask.idea.get().version.let(Version::parse)
        val platform203 = Version.parse("2020.3")
        val platform222 = Version.parse("2022.2")

        when {
            javaVersion == null -> throw GradleException("Could not determine Java version")
            platformVersion < platform203 && javaVersion < JavaVersion.VERSION_1_8 -> throw GradleException(
                "Java $javaVersion is not supported with IntelliJ Platform $platformVersion. Please use Java 8 it you target IntelliJ Platform lower than 2020.3"
            )
            platformVersion >= platform203 && platformVersion < platform222 && javaVersion < JavaVersion.VERSION_11 -> throw GradleException(
                "Java $javaVersion is not supported with IntelliJ Platform $platformVersion. Please use Java 11 it you target IntelliJ Platform 2020.3+ and lower than 2022.2"
            )
            platformVersion >= platform222 && javaVersion < JavaVersion.VERSION_17 -> throw GradleException(
                "Java $javaVersion is not supported with IntelliJ Platform $platformVersion. Please use Java 17 it you target IntelliJ Platform 2022.2+"
            )
        }
    }

    private fun configureTasks(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring plugin")
        project.tasks.whenTaskAdded {
            if (this is RunIdeBase) {
                prepareConventionMappingsForRunIdeTask(project, extension, this, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            }
            if (this is RunIdeForUiTestTask) {
                prepareConventionMappingsForRunIdeTask(
                    project,
                    extension,
                    this,
                    IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME
                )
            }
        }
        configureSetupDependenciesTask(project, extension)
        configureClassPathIndexCleanupTask(project)
        configurePatchPluginXmlTask(project, extension)
        configureRobotServerDownloadTask(project)
        configurePrepareSandboxTasks(project, extension)
        configureListProductsReleasesTask(project, extension)
        configureRunPluginVerifierTask(project, extension)
        configurePluginVerificationTask(project)
        configureRunIdeTask(project)
        configureRunIdePerformanceTestTask(project, extension)
        configureRunIdeForUiTestsTask(project)
        configureBuildSearchableOptionsTask(project)
        configureJarSearchableOptionsTask(project)
        configureBuildPluginTask(project)
        configureSignPluginTask(project)
        configurePublishPluginTask(project)
        configureProcessResources(project)
        configureInstrumentation(project, extension)
        assert(!project.state.executed) { "afterEvaluate is a no-op for an executed project" }

        project.afterEvaluate {
            configureProjectAfterEvaluate(this, extension)
            checkJavaRuntimeVersion(this, extension)
        }
    }

    private fun configureProjectAfterEvaluate(project: Project, extension: IntelliJPluginExtension) {
        project.subprojects.forEach { subproject ->
            if (subproject.plugins.findPlugin(IntelliJPlugin::class.java) == null) {
                subproject.extensions.findByType(IntelliJPluginExtension::class.java)?.let {
                    configureProjectAfterEvaluate(subproject, it)
                }
            }
        }

        configureTestTasks(project, extension)
    }

    private fun verifyJavaPluginDependency(project: Project, ideaDependency: IdeaDependency, plugins: List<Any>) {
        val hasJavaPluginDependency = plugins.contains("java") || plugins.contains("com.intellij.java")
        if (!hasJavaPluginDependency && File(ideaDependency.classes, "plugins/java").exists()) {
            sourcePluginXmlFiles(project).forEach { file ->
                parsePluginXml(file, context)?.dependencies?.forEach {
                    if (it.dependencyId == "com.intellij.modules.java") {
                        throw BuildException(
                            "The project depends on 'com.intellij.modules.java' module but doesn't declare a compile dependency on it.\n " +
                                "Please delete 'depends' tag from '${file.absolutePath}' or add 'java' plugin to Gradle dependencies " +
                                "(e.g. intellij { plugins = ['java'] })",
                            null,
                        )
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
        val configuredPlugins = extension.getUnresolvedPluginDependencies().filter(PluginDependency::builtin).map(PluginDependency::id)
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
        project.tasks.withType(PrepareSandboxTask::class.java).configureEach {
            configureExternalPlugin(plugin)
        }
    }

    private fun configureProjectPluginTasksDependency(dependency: Project, taskProvider: TaskProvider<PrepareSandboxTask>) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPlugin::class.java) == null) {
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin not found." + dependency.plugins, null)
        }
        dependency.tasks.named(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) {
            taskProvider.get().dependsOn(this)
        }
    }

    private fun configureProjectPluginDependency(
        project: Project,
        dependency: Project,
        dependencies: DependencySet,
        extension: IntelliJPluginExtension,
    ) {
        // invoke on demand, when plugins artifacts are needed
        if (dependency.plugins.findPlugin(IntelliJPlugin::class.java) == null) {
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin not found." + dependency.plugins, null)
        }
        dependencies.add(project.dependencies.create(dependency))

        val prepareSandboxTaskProvider = dependency.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        val prepareSandboxTask = prepareSandboxTaskProvider.get()
        val dependencyDirectory = File(prepareSandboxTask.destinationDir, prepareSandboxTask.pluginName.get())

        val pluginDependency = PluginProjectDependency(dependencyDirectory, context)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType(PrepareSandboxTask::class.java).forEach {
            it.configureCompositePlugin(pluginDependency)
        }
    }

    private fun configurePatchPluginXmlTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring patch plugin.xml task")

        project.tasks.register(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask::class.java) {
            val setupDependenciesTaskProvider =
                project.tasks.named<SetupDependenciesTask>(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
            val setupDependenciesTask = setupDependenciesTaskProvider.get()

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Patches `plugin.xml` files with values provided to the task."

            version.convention(project.provider {
                project.version.toString()
            })
            pluginXmlFiles.convention(project.provider {
                sourcePluginXmlFiles(project)
            })
            destinationDir.convention(project.layout.dir(project.provider {
                File(project.buildDir, IntelliJPluginConstants.PLUGIN_XML_DIR_NAME)
            }))
            sinceBuild.convention(project.provider {
                if (extension.updateSinceUntilBuild.get()) {
                    val ideVersion = IdeVersion.createIdeVersion(setupDependenciesTask.idea.get().buildNumber)
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
                        val ideVersion = IdeVersion.createIdeVersion(setupDependenciesTask.idea.get().buildNumber)
                        "${ideVersion.baselineVersion}.*"
                    }
                } else {
                    null
                }
            })

            dependsOn(setupDependenciesTaskProvider)
        }
    }

    private fun configurePrepareSandboxTasks(project: Project, extension: IntelliJPluginExtension) {
        val downloadPluginTaskProvider =
            project.tasks.named<DownloadRobotServerPluginTask>(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)

        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, "")
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME, "-test")
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME, "-uiTest") {
            val downloadPluginTask = downloadPluginTaskProvider.get()

            it.from(downloadPluginTask.outputDir.get())
            it.dependsOn(downloadPluginTask)
        }
    }

    private fun configureRobotServerDownloadTask(project: Project) {
        info(context, "Configuring robot-server download Task")

        project.tasks.register(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME, DownloadRobotServerPluginTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Download `robot-server` plugin."

            version.convention(IntelliJPluginConstants.VERSION_LATEST)
            outputDir.convention(project.provider {
                project.layout.projectDirectory.dir("${project.buildDir}/robotServerPlugin")
            })
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
                    mavenRepository(IntelliJPluginConstants.INTELLIJ_DEPENDENCIES) {
                        content { includeGroup(group) }
                    }
                }).first()
            })
        }
    }

    private fun configurePrepareSandboxTask(
        project: Project,
        extension: IntelliJPluginExtension,
        taskName: String,
        testSuffix: String,
        configure: ((it: PrepareSandboxTask) -> Unit)? = null,
    ) {
        info(context, "Configuring $taskName task")

        project.tasks.register(taskName, PrepareSandboxTask::class.java) {
            val setupDependenciesTaskProvider =
                project.tasks.named<SetupDependenciesTask>(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
            val setupDependenciesTask = setupDependenciesTaskProvider.get()

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Prepares sandbox directory with installed plugin and its dependencies."

            pluginName.convention(extension.pluginName)
            pluginJar.convention(project.layout.file(project.provider {
                val jarTaskProvider = project.tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME)
                val jarTask = jarTaskProvider.get()

                jarTask.run {
                    exclude("**/classpath.index")
                    manifest.attributes(
                        "Created-By" to "Gradle ${project.gradle.gradleVersion}",
                        "Build-JVM" to Jvm.current(),
                        "Version" to project.version,
                        "Build-Plugin" to IntelliJPluginConstants.NAME,
                        "Build-Plugin-Version" to (getCurrentVersion() ?: "0.0.0"),
                        "Build-OS" to OperatingSystem.current(),
                        "Build-SDK" to when (extension.localPath.orNull) {
                            null -> "${extension.getVersionType()}-${extension.getVersionNumber()}"
                            else -> setupDependenciesTask.idea.get().classes.let { ideaClasses ->
                                ideProductInfo(ideaClasses)
                                    ?.run { "$productCode-$version" }
                                // Fall back on build number if product-info.json is not present, this is the case
                                // for recent versions of Android Studio.
                                    ?: ideBuildNumber(ideaClasses)
                            }
                        },
                    )
                    archiveFile.orNull?.asFile
                }
            }))
            defaultDestinationDir.convention(project.provider {
                project.file("${extension.sandboxDir.get()}/plugins$testSuffix")
            })
            configDir.convention(project.provider {
                "${extension.sandboxDir.get()}/config$testSuffix"
            })
            librariesToIgnore.convention(project.provider {
                project.files(setupDependenciesTask.idea.get().jarFiles)
            })
            pluginDependencies.convention(project.provider {
                extension.getPluginDependenciesList(project)
            })

            dependsOn(JavaPlugin.JAR_TASK_NAME)
            dependsOn(project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME))
            dependsOn(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)

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

        project.tasks.register(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, RunPluginVerifierTask::class.java) {
            val listProductsReleasesTaskProvider =
                project.tasks.named<ListProductsReleasesTask>(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)
            val listProductsReleasesTask = listProductsReleasesTaskProvider.get()

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IDE builds."

            failureLevel.convention(EnumSet.of(RunPluginVerifierTask.FailureLevel.COMPATIBILITY_PROBLEMS))
            verifierVersion.convention(IntelliJPluginConstants.VERSION_LATEST)
            distributionFile.convention(project.layout.file(project.provider {
                resolveBuildTaskOutput(project)
            }))
            verificationReportsDir.convention(project.provider {
                "${project.buildDir}/reports/pluginVerifier"
            })
            downloadDir.convention(project.provider {
                ideDownloadDir().toString()
            })
            teamCityOutputFormat.convention(false)
            subsystemsToCheck.convention("all")
            ideDir.convention(project.provider {
                val runIdeTaskProvider = project.tasks.named<RunIdeTask>(IntelliJPluginConstants.RUN_IDE_TASK_NAME)
                val runIdeTask = runIdeTaskProvider.get()
                runIdeTask.ideDir.get()
            })
            productsReleasesFile.convention(project.provider {
                listProductsReleasesTask.outputFile.get().asFile
            })
            ides.convention(project.provider {
                val ideVersions = ideVersions.get().takeIf(List<String>::isNotEmpty) ?: run {
                    when {
                        localPaths.get().isEmpty() -> productsReleasesFile.get().takeIf(File::exists)?.readLines()
                        else -> null
                    }
                } ?: emptyList()

                ideVersions.map { ideVersion ->
                    val downloadDir = File(downloadDir.get())
                    val context = logCategory()

                    resolveIdePath(ideVersion, downloadDir, context) { type, version, buildType ->
                        val name = "$type-$version"
                        val ideDir = downloadDir.resolve(name)
                        info(context, "Downloading IDE '$name' to: $ideDir")

                        val url = resolveIdeUrl(type, version, buildType, context)
                        val dependencyVersion = listOf(type, version, buildType).filterNot(String::isNullOrEmpty).joinToString("-")
                        val group = when (type) {
                            IntelliJPluginConstants.ANDROID_STUDIO_TYPE -> "com.android"
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
                            archiveUtils.extract(ideArchive, ideDir, context)
                            ideDir.listFiles()?.let { files ->
                                files.filter(File::isDirectory).forEach { container ->
                                    container.listFiles()?.forEach { file ->
                                        file.renameTo(ideDir.resolve(file.name))
                                    }
                                    container.deleteRecursively()
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
                    mavenRepository(IntelliJPluginConstants.PLUGIN_VERIFIER_REPOSITORY)
                }).first().canonicalPath
            })
            jreRepository.convention(extension.jreRepository)
            offline.set(project.gradle.startParameter.isOffline)

            dependsOn(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
            dependsOn(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
            dependsOn(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)

            val isIdeVersionsEmpty = project.provider {
                ideVersions.get().isEmpty() && localPaths.get().isEmpty()
            }
            listProductsReleasesTask.onlyIf { isIdeVersionsEmpty.get() }

            outputs.upToDateWhen { false }
        }
    }

    private fun configurePluginVerificationTask(project: Project) {
        info(context, "Configuring plugin verification task")

        project.tasks.register(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, VerifyPluginTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Validates completeness and contents of `plugin.xml` descriptors as well as plugin archive structure."

            ignoreFailures.convention(false)
            ignoreWarnings.convention(true)
            pluginDir.convention(project.provider {
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
                val prepareSandboxTask = prepareSandboxTaskProvider.get()
                val path = File(prepareSandboxTask.destinationDir, prepareSandboxTask.pluginName.get()).path
                project.layout.projectDirectory.dir(path)
            })

            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private fun configureRunIdeTask(project: Project) {
        info(context, "Configuring run IDE task")

        project.tasks.register(IntelliJPluginConstants.RUN_IDE_TASK_NAME, RunIdeTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Runs the IDE instance with the developed plugin installed."

            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        }
    }

    private fun configureRunIdePerformanceTestTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring run IDE performance test task")

        project.tasks.register(
            IntelliJPluginConstants.RUN_IDE_PERFORMANCE_TEST_TASK_NAME,
            RunIdePerformanceTestTask::class.java
        ) {
            val setupDependenciesTaskProvider =
                project.tasks.named<SetupDependenciesTask>(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
            val setupDependenciesTask = setupDependenciesTaskProvider.get()

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Runs performance tests on the IDE with the developed plugin installed."

            artifactsDir.convention(project.provider {
                "${project.buildDir}/reports/performance-test/${extension.type.get()}${extension.version.get()}-${project.version}-${
                    LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                }"
            })
            profilerName.convention(ProfilerName.ASYNC)

            with(project.configurations) {
                val performanceTestConfiguration = create(IntelliJPluginConstants.PERFORMANCE_TEST_CONFIGURATION_NAME)
                    .setVisible(false)
                    .withDependencies {
                        val ideaDependency = setupDependenciesTask.idea.get()
                        val plugins = extension.plugins.get()

                        // Check that `runIdePerformanceTest` task was launched
                        // Check that `performanceTesting.jar` is absent (that means it's community version)
                        // Check that user didn't pass custom version of the performance plugin
                        if (IntelliJPluginConstants.RUN_IDE_PERFORMANCE_TEST_TASK_NAME in project.gradle.startParameter.taskNames &&
                            ideaDependency.pluginsRegistry.findPlugin(IntelliJPluginConstants.PERFORMANCE_PLUGIN_ID) == null &&
                            plugins.none { it is String && it.startsWith(IntelliJPluginConstants.PERFORMANCE_PLUGIN_ID) }
                        ) {
                            val resolver = project.objects.newInstance(
                                PluginDependencyManager::class.java,
                                project.gradle.gradleUserHomeDir.absolutePath,
                                ideaDependency,
                                extension.getPluginsRepositories(),
                                archiveUtils,
                                context,
                            )

                            val resolvedPlugin = resolveLatestPluginUpdate(
                                IntelliJPluginConstants.PERFORMANCE_PLUGIN_ID,
                                ideaDependency.buildNumber,
                            )

                            val plugin = resolver.resolve(project, resolvedPlugin)
                                ?: throw BuildException(with(resolvedPlugin) { "Failed to resolve plugin $id:$version@$channel" }, null)

                            configurePluginDependency(project, plugin, extension, this, resolver)
                        }
                    }

                getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(performanceTestConfiguration)
            }

            dependsOn(setupDependenciesTask)
            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        }
    }

    private fun resolveLatestPluginUpdate(pluginId: String, buildNumber: String, channel: String = "") =
        PluginRepositoryFactory
            .create(IntelliJPluginConstants.MARKETPLACE_HOST)
            .pluginManager
            .searchCompatibleUpdates(listOf(pluginId), buildNumber, channel)
            .first()
            .let { PluginDependencyNotation(it.pluginXmlId, it.version, it.channel) }

    private fun configureRunIdeForUiTestsTask(project: Project) {
        info(context, "Configuring run IDE for UI tests task")

        project.tasks.register(IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME, RunIdeForUiTestTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Runs the IDE instance with the developed plugin and robot-server installed and ready for UI testing."

            dependsOn(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
            finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)
        }
    }

    private fun configureBuildSearchableOptionsTask(project: Project) {
        info(context, "Configuring build searchable options task")

        project.tasks.register(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, BuildSearchableOptionsTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Builds an index of UI components (searchable options) for the plugin."

            outputDir.convention(project.provider {
                project.layout.projectDirectory.dir("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}")
            })
            showPaidPluginWarning.convention(project.provider {
                project.isBuildFeatureEnabled(PAID_PLUGIN_SEARCHABLE_OPTIONS_WARNING) && run {
                    sourcePluginXmlFiles(project).any {
                        parsePluginXml(it, context)?.productDescriptor != null
                    }
                }
            })

            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

            onlyIf {
                val number = ideBuildNumber(ideDir.get())
                Version.parse(number.split('-').last()) >= Version.parse("191.2752")
            }
        }
    }

    private fun prepareConventionMappingsForRunIdeTask(
        project: Project,
        extension: IntelliJPluginExtension,
        task: RunIdeBase,
        prepareSandBoxTaskName: String,
    ) {
        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(prepareSandBoxTaskName)
        val prepareSandboxTask = prepareSandboxTaskProvider.get()
        val setupDependenciesTaskProvider = project.tasks.named<SetupDependenciesTask>(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
        val setupDependenciesTask = setupDependenciesTaskProvider.get()
        val taskContext = task.logCategory()
        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it, taskContext)?.id }

        task.ideDir.convention(project.provider {
            val path = setupDependenciesTask.idea.get().classes.path
            project.file(path)
        })
        task.requiredPluginIds.convention(project.provider {
            pluginIds
        })
        task.configDir.convention(project.provider {
            project.file(prepareSandboxTask.configDir.get())
        })
        task.pluginsDir.convention(project.provider {
            val path = prepareSandboxTask.destinationDir.path
            project.layout.projectDirectory.dir(path)
        })
        task.systemDir.convention(project.provider {
            project.file("${extension.sandboxDir.get()}/system")
        })
        task.autoReloadPlugins.convention(project.provider {
            val number = ideBuildNumber(task.ideDir.get())
            Version.parse(number.split('-').last()) >= Version.parse("202.0")
        })
        task.projectWorkingDir.convention(project.provider {
            project.file("${task.ideDir.get()}/bin/")
        })
        task.projectExecutable.convention(project.provider {
            val jbrResolver = project.objects.newInstance(
                JbrResolver::class.java,
                extension.jreRepository.orNull.orEmpty(),
                project.gradle.startParameter.isOffline,
                archiveUtils,
                dependenciesDownloader,
                taskContext,
            )

            jbrResolver.resolveRuntime(
                jbrVersion = task.jbrVersion.orNull,
                jbrVariant = task.jbrVariant.orNull,
                ideDir = task.ideDir.orNull,
            )
        })

        task.dependsOn(setupDependenciesTaskProvider)
    }

    private fun configureJarSearchableOptionsTask(project: Project) {
        info(context, "Configuring jar searchable options task")

        project.tasks.register(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME, JarSearchableOptionsTask::class.java) {
            val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            val prepareSandboxTask = prepareSandboxTaskProvider.get()

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Creates a JAR file with searchable options to be distributed with the plugin."

            outputDir.convention(project.provider {
                project.layout.projectDirectory.dir(project.buildDir.resolve(IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME).canonicalPath)
            })
            pluginName.convention(prepareSandboxTask.pluginName)
            sandboxDir.convention(project.provider {
                prepareSandboxTask.destinationDir.canonicalPath
            })
            archiveBaseName.convention("lib/searchableOptions")
            destinationDirectory.convention(project.layout.buildDirectory.dir("libsSearchableOptions"))
            noSearchableOptionsWarning.convention(project.isBuildFeatureEnabled(NO_SEARCHABLE_OPTIONS_WARNING))

            dependsOn(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            onlyIf { outputDir.get().asFile.isDirectory }
        }
    }

    private fun configureInstrumentation(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring compile tasks")

        val jarTaskProvider = project.tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME)
        val jarTask = jarTaskProvider.get()
        if (extension.instrumentCode.get()) {
            jarTask.duplicatesStrategy = DuplicatesStrategy.EXCLUDE
        }

        val setupInstrumentCodeTaskProvider =
            project.tasks.register(IntelliJPluginConstants.SETUP_INSTRUMENT_CODE_TASK_NAME, SetupInstrumentCodeTask::class.java) {
                instrumentationEnabled.convention(project.provider {
                    extension.instrumentCode.get()
                })
                instrumentedDir.convention(project.layout.buildDirectory.dir("instrumented"))
            }
        val setupInstrumentCodeTask = setupInstrumentCodeTaskProvider.get()

        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
        sourceSets.forEach { sourceSet ->
            val name = sourceSet.getTaskName("instrument", "code")

            val instrumentTaskProvider =
                project.tasks.register(name, IntelliJInstrumentCodeTask::class.java) {
                    val setupDependenciesTaskProvider =
                        project.tasks.named<SetupDependenciesTask>(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
                    val setupDependenciesTask = setupDependenciesTaskProvider.get()
                    val instrumentCodeProvider = project.provider { extension.instrumentCode.get() }

                    sourceDirs.from(project.provider {
                        sourceSet.allJava.srcDirs
                    })
                    formsDirs.from(project.provider {
                        sourceDirs.asFileTree.filter { it.name.endsWith(".form") }
                    })
                    classesDirs.from(project.provider {
                        (sourceSet.output.classesDirs as ConfigurableFileCollection).from.run {
                            project.files(this).filter { it.exists() }
                        }
                    })
                    sourceSetCompileClasspath.from(project.provider {
                        sourceSet.compileClasspath
                    })
                    compilerVersion.convention(project.provider {
                        val version by lazy { extension.getVersionNumber() }
                        val localPath = extension.localPath.orNull
                        val ideaDependency = setupDependenciesTask.idea.get()

                        if (localPath.isNullOrBlank() && version.endsWith(RELEASE_SUFFIX_SNAPSHOT)) {
                            val type = extension.getVersionType()
                            if (version == IntelliJPluginConstants.DEFAULT_IDEA_VERSION && listOf("CL", "RD", "PY", "PS").contains(type)) {
                                ideProductInfo(ideaDependency.classes)?.buildNumber?.let { buildNumber ->
                                    Version.parse(buildNumber).let { v -> "${v.major}.${v.minor}$RELEASE_SUFFIX_EAP_CANDIDATE" }
                                } ?: version
                            } else {
                                when (type) {
                                    "CL" -> "CLION-$version"
                                    "RD" -> "RIDER-$version"
                                    "PY" -> "PYCHARM-$version"
                                    "PS" -> "PHPSTORM-$version"
                                    else -> version
                                }
                            }
                        } else {
                            val isEap = localPath?.let { ideProductInfo(ideaDependency.classes)?.versionSuffix == "EAP" } ?: false
                            val eapSuffix = IntelliJPluginConstants.RELEASE_SUFFIX_EAP.takeIf { isEap }.orEmpty()

                            IdeVersion.createIdeVersion(ideaDependency.buildNumber)
                                .stripExcessComponents()
                                .asStringWithoutProductCode() + eapSuffix
                        }
                    })
                    ideaDependency.convention(setupDependenciesTask.idea)
                    javac2.convention(project.provider {
                        project.file("${setupDependenciesTask.idea.get().classes}/lib/javac2.jar").takeIf(File::exists)
                    })
                    compilerClassPathFromMaven.convention(project.provider {
                        val compilerVersion = compilerVersion.get()
                        if (compilerVersion == IntelliJPluginConstants.DEFAULT_IDEA_VERSION ||
                            Version.parse(compilerVersion) >= Version(183, 3795, 13)
                        ) {
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
                                        IntelliJPluginConstants.INTELLIJ_DEPENDENCIES,
                                    ).map(::mavenRepository)
                                }, true)
                            }

                            listOf(
                                {
                                    runCatching {
                                        downloadCompiler(compilerVersion)
                                    }.fold(
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
                                    if (compilerVersion.endsWith(IntelliJPluginConstants.RELEASE_SUFFIX_EAP)) {
                                        val nonEapVersion = compilerVersion.replace(
                                            IntelliJPluginConstants.RELEASE_SUFFIX_EAP, ""
                                        )
                                        runCatching {
                                            downloadCompiler(nonEapVersion)
                                        }.fold(
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
                                    val closestCompilerVersion = URL(IntelliJPluginConstants.JAVA_COMPILER_ANT_TASKS_MAVEN_METADATA)
                                        .openStream().use { inputStream ->
                                            val version = Version.parse(compilerVersion)
                                            XmlExtractor<MavenMetadata>()
                                                .unmarshal(inputStream)
                                                .versioning?.versions?.let { versions ->
                                                    versions
                                                        .map(Version::parse)
                                                        .filter { it <= version }
                                                        .maxOf { it }.version
                                                }
                                        }

                                    if (closestCompilerVersion == null) {
                                        warn(logCategory(), "Cannot resolve java-compiler-ant-tasks Maven metadata")
                                        null
                                    } else {
                                        runCatching {
                                            downloadCompiler(closestCompilerVersion)
                                        }.fold(
                                            onSuccess = {
                                                warn(
                                                    logCategory(),
                                                    "Resolved closest lower java-compiler-ant-tasks version: $closestCompilerVersion"
                                                )
                                                it
                                            },
                                            onFailure = {
                                                warn(
                                                    logCategory(),
                                                    "Cannot resolve java-compiler-ant-tasks in version: $closestCompilerVersion"
                                                )
                                                null
                                            },
                                        )
                                    }
                                },
                            )
                                .asSequence()
                                .mapNotNull { it() }
                                .firstOrNull()
                        } else {
                            warn(
                                logCategory(),
                                "Compiler in '$compilerVersion' version can't be resolved from Maven. Minimal version supported: 2018.3+. Use higher 'intellij.version' or specify the 'compilerVersion' property manually.",
                            )
                            null
                        }
                    })

                    outputDir.convention(setupInstrumentCodeTask.instrumentedDir.map {
                        it.dir(name)
                    })

                    dependsOn(sourceSet.classesTaskName)
                    dependsOn(setupDependenciesTask)
                    dependsOn(setupInstrumentCodeTask)
                    onlyIf { instrumentCodeProvider.get() }
                }

            // A dedicated task ensures that sources substitution is always run,
            // even when the instrumentCode task is up-to-date.
            val updateTask = project.tasks.register("post${name.capitalize()}") {
                val instrumentTask = instrumentTaskProvider.get()
                val instrumentCodeProvider = project.provider { extension.instrumentCode.get() && instrumentTask.isEnabled }
                val classesDirs = sourceSet.output.classesDirs as ConfigurableFileCollection
                val outputDir = project.provider { instrumentTask.outputDir.get() }

                onlyIf { instrumentCodeProvider.get() }
                doLast { classesDirs.setFrom(outputDir.get()) }

                dependsOn(instrumentTask)
            }

            // Ensure that our task is invoked when the source set is built
            sourceSet.compiledBy(updateTask)
        }
    }

    private fun configureTestTasks(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring tests tasks")
        val setupDependenciesTaskProvider = project.tasks.named<SetupDependenciesTask>(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
        val setupDependenciesTask = setupDependenciesTaskProvider.get()
        val runIdeTaskProvider = project.tasks.named<RunIdeTask>(IntelliJPluginConstants.RUN_IDE_TASK_NAME)
        val runIdeTask = runIdeTaskProvider.get()
        val prepareTestingSandboxTaskProvider =
            project.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME)
        val prepareTestingSandboxTask = prepareTestingSandboxTaskProvider.get()

        val testTasks = project.tasks.withType(Test::class.java)

        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it, context)?.id }
        val buildNumberProvider = project.provider { setupDependenciesTask.idea.get().buildNumber }
        val ideDirProvider = project.provider { runIdeTask.ideDir.get() }
        val ideaDependencyLibrariesProvider = project.provider {
            val classes = setupDependenciesTask.idea.get().classes
            project.files("$classes/lib/resources.jar", "$classes/lib/idea.jar")
        }

        val sandboxDirProvider = project.provider {
            project.file(extension.sandboxDir.get())
        }
        val configDirectoryProvider = sandboxDirProvider.map {
            it.resolve("config-test").apply { mkdirs() }
        }
        val systemDirectoryProvider = sandboxDirProvider.map {
            it.resolve("system-test").apply { mkdirs() }
        }
        val pluginsDirectoryProvider = sandboxDirProvider.map {
            prepareTestingSandboxTask.destinationDir.apply { mkdirs() }
        }

        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
        val sourceSetsOutputs = project.provider {
            project.files(sourceSets.map {
                it.output.run {
                    classesDirs + generatedSourcesDirs + resourcesDir
                }
            })
        }

        val ideaConfigurationFiles = project.provider {
            project.files(project.configurations.getByName(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME).resolve())
        }
        val ideaPluginsConfigurationFiles = project.provider {
            project.files(project.configurations.getByName(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME).resolve())
        }

        testTasks.forEach { task ->
            task.enableAssertions = true

            // appClassLoader should be used for user's plugins. Otherwise, classes it won't be possible to use
            // its classes of application components or services in tests: class loaders will be different for
            // classes references by test code and for classes loaded by the platform (pico container).
            //
            // The proper way to handle that is to substitute Gradle's test class-loader and teach it
            // to understand PluginClassLoaders. Unfortunately, I couldn't find a way to do that.
            task.systemProperty("idea.use.core.classloader.for.plugin.path", "true")
            task.systemProperty("idea.force.use.core.classloader", "true")
            // the same as previous – setting appClassLoader but outdated. Works for part of 203 builds.
            task.systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

            task.outputs.dir(systemDirectoryProvider)
                .withPropertyName("System directory")
            task.inputs.dir(configDirectoryProvider)
                .withPropertyName("Config Directory")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            task.inputs.files(pluginsDirectoryProvider)
                .withPropertyName("Plugins directory")
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .withNormalizer(ClasspathNormalizer::class.java)

            task.dependsOn(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
            task.dependsOn(IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME)
            task.finalizedBy(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME)

            task.doFirst {
                task.jvmArgs = getIdeJvmArgs(task, task.jvmArgs, ideDirProvider.get()) + OpenedPackages
                task.classpath += ideaDependencyLibrariesProvider.get()
                task.classpath -= task.classpath.filter { !it.isDirectory && !it.name.endsWith("jar") }

                // Rearrange classpath to put idea and plugins in the right order.
                task.classpath -= ideaConfigurationFiles.get()
                task.classpath -= ideaPluginsConfigurationFiles.get()
                task.classpath += ideaConfigurationFiles.get() + ideaPluginsConfigurationFiles.get()

                // Add source roots to the classpath.
                task.classpath += sourceSetsOutputs.get()

                task.systemProperties(
                    getIdeaSystemProperties(
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
                    task.systemProperty(
                        IntelliJPluginConstants.PLUGIN_PATH,
                        pluginsDirectoryProvider.get().listFiles()?.joinToString("${File.pathSeparator},") { it.path }.orEmpty(),
                    )
                }
            }
        }
    }

    private fun configureBuildPluginTask(project: Project) {
        info(context, "Configuring building plugin task")

        project.tasks.register(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME, Zip::class.java) {
            val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            val prepareSandboxTask = prepareSandboxTaskProvider.get()
            val jarSearchableOptionsTaskProvider =
                project.tasks.named<JarSearchableOptionsTask>(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)
            val jarSearchableOptionsTask = jarSearchableOptionsTaskProvider.get()

            description = "Assembles plugin and prepares ZIP archive for deployment."
            group = IntelliJPluginConstants.GROUP_NAME

            archiveBaseName.convention(prepareSandboxTask.pluginName)

            from(project.provider {
                "${prepareSandboxTask.destinationDir}/${prepareSandboxTask.pluginName.get()}"
            })
            from(jarSearchableOptionsTask.archiveFile) { into("lib") }
            into(prepareSandboxTask.pluginName)
            dependsOn(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)
            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)

            val archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
            ArchivePublishArtifact(this).let { zipArtifact ->
                archivesConfiguration.artifacts.add(zipArtifact)
                project.extensions.getByType(DefaultArtifactPublicationSet::class.java).addCandidate(zipArtifact)
                project.components.add(IntelliJPluginLibrary())
            }
        }
    }

    private fun configureSignPluginTask(project: Project) {
        info(context, "Configuring sign plugin task")

        project.tasks.register(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, SignPluginTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Signs the ZIP archive with the provided key using marketplace-zip-signer library."

            inputArchiveFile.convention(project.layout.file(project.provider {
                resolveBuildTaskOutput(project)
            }))
            outputArchiveFile.convention(project.layout.file(project.provider {
                val buildPluginTaskProvider = project.tasks.named<Zip>(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
                val buildPluginTask = buildPluginTaskProvider.get()
                val inputFile = buildPluginTask.archiveFile.get().asFile
                val inputFileExtension = inputFile.path.substring(inputFile.path.lastIndexOf('.'))
                val inputFileWithoutExtension = inputFile.path.substring(0, inputFile.path.lastIndexOf('.'))
                val outputFilePath = "$inputFileWithoutExtension-signed$inputFileExtension"
                File(outputFilePath)
            }))
            cliVersion.convention(IntelliJPluginConstants.VERSION_LATEST)
            cliPath.convention(project.provider {
                val resolvedCliVersion = resolveCliVersion(cliVersion.orNull)
                val url = resolveCliUrl(resolvedCliVersion)
                debug(context, "Using Marketplace ZIP Signer CLI in '$resolvedCliVersion' version")

                dependenciesDownloader.downloadFromRepository(logCategory(), {
                    create(
                        group = "org.jetbrains",
                        name = "marketplace-zip-signer-cli",
                        version = resolvedCliVersion,
                        ext = "jar",
                    )
                }, {
                    ivyRepository(url)
                }).first().canonicalPath
            })

            onlyIf {
                (privateKey.isPresent || privateKeyFile.isPresent) && (certificateChain.isPresent || certificateChainFile.isPresent)
            }
            dependsOn(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        }
    }

    private fun configurePublishPluginTask(project: Project) {
        info(context, "Configuring publish plugin task")

        project.tasks.register(IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME, PublishPluginTask::class.java) {
            val signPluginTaskProvider = project.tasks.named<SignPluginTask>(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)
            val isOffline = project.gradle.startParameter.isOffline

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Publishes plugin to the remote Marketplace repository."

            host.convention(IntelliJPluginConstants.MARKETPLACE_HOST)
            toolboxEnterprise.convention(false)
            channels.convention(listOf("default"))
            distributionFile.convention(project.layout.file(project.provider {
                signPluginTaskProvider.get().let { signPluginTask ->
                    signPluginTask.outputArchiveFile.orNull?.asFile.takeIf { signPluginTask.didWork } ?: resolveBuildTaskOutput(project)
                }
            }))

            dependsOn(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
            dependsOn(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
            dependsOn(signPluginTaskProvider)
            onlyIf { !isOffline }
        }
    }

    private fun configureListProductsReleasesTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring list products task")

        project.tasks.register(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME, ListProductsReleasesTask::class.java) {
            val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME)
            val patchPluginXmlTask = patchPluginXmlTaskProvider.get()
            val repositoryVersion = LocalDateTime.now().format(
                DateTimeFormatterBuilder()
                    .append(DateTimeFormatter.BASIC_ISO_DATE)
                    .appendValue(ChronoField.HOUR_OF_DAY, 2)
                    .toFormatter()
            )

            group = IntelliJPluginConstants.GROUP_NAME
            description = "List all available IntelliJ-based IDE releases with their updates."

            updatePaths.convention(project.provider {
                mapOf("idea-releases" to IntelliJPluginConstants.IDEA_PRODUCTS_RELEASES_URL).entries.map { (name, repository) ->
                    dependenciesDownloader.downloadFromRepository(logCategory(), {
                        create(
                            group = "org.jetbrains",
                            name = name,
                            version = repositoryVersion,
                            ext = "xml",
                        )
                    }, { ivyRepository(repository) }).first().canonicalPath
                }
            })
            androidStudioUpdatePath.convention(project.provider {
                dependenciesDownloader.downloadFromRepository(logCategory(), {
                    create(
                        group = "org.jetbrains",
                        name = "android-studio-products-releases",
                        version = repositoryVersion,
                        ext = "xml",
                    )
                }, { ivyRepository(IntelliJPluginConstants.ANDROID_STUDIO_PRODUCTS_RELEASES_URL) }).first().canonicalPath
            })
            outputFile.convention {
                File(project.buildDir, "${IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME}.txt")
            }
            types.convention(project.provider {
                listOf(extension.type.get())
            })
            sinceBuild.convention(patchPluginXmlTask.sinceBuild)
            untilBuild.convention(patchPluginXmlTask.untilBuild)
            releaseChannels.convention(EnumSet.allOf(ListProductsReleasesTask.Channel::class.java))
        }
    }

    private fun configureProcessResources(project: Project) {
        info(context, "Configuring resources task")
        val patchPluginXmlTaskProvider = project.tasks.named<PatchPluginXmlTask>(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME)

        project.tasks.named<ProcessResources>(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) {
            from(patchPluginXmlTaskProvider) {
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
                into("META-INF")
            }
        }
    }

    private fun configureSetupDependenciesTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring setup dependencies task")

        project.tasks.register(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME, SetupDependenciesTask::class.java) {
            var defaultDependenciesResolved = false

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Setups required dependencies for building and running project."

            val ideaConfiguration = with(project.configurations) {
                val idea = create(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME).setVisible(false)
                val ideaPlugins = create(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME).setVisible(false).apply {
                    configurePluginDependencies(project, this@register, extension, this)
                }
                val defaultDependencies =
                    create(IntelliJPluginConstants.INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME).setVisible(false).apply {
                        defaultDependencies {
                            add(
                                project.dependencies.create(
                                    group = "org.jetbrains",
                                    name = "annotations",
                                    version = IntelliJPluginConstants.ANNOTATIONS_DEPENDENCY_VERSION,
                                )
                            )
                        }
                    }

                getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(defaultDependencies, idea, ideaPlugins)
                getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom(defaultDependencies, idea, ideaPlugins)
                project.pluginManager.withPlugin("java-test-fixtures") {
                    getByName("testFixturesCompileOnly").extendsFrom(defaultDependencies, idea, ideaPlugins)
                }
                idea
            }

            idea.apply {
                convention(project.provider {
                    val dependencyManager = project.objects.newInstance(
                        IdeaDependencyManager::class.java,
                        extension.intellijRepository.get(),
                        extension.ideaDependencyCachePath.orNull.orEmpty(),
                        archiveUtils,
                        dependenciesDownloader,
                        context,
                    )
                    val ideaDependency = when (val localPath = extension.localPath.orNull) {
                        null -> {
                            info(context, "Using IDE from remote repository")
                            val version = extension.getVersionNumber()
                            val extraDependencies = extension.extraDependencies.get()
                            dependencyManager.resolveRemote(
                                project,
                                version,
                                extension.getVersionType(),
                                extension.downloadSources.get(),
                                extraDependencies,
                            )
                        }

                        else -> {
                            if (extension.version.orNull != null) {
                                throw GradleException("Both 'intellij.localPath' and 'intellij.version' are specified, but one of these is allowed to be present.")
                            }
                            info(context, "Using path to locally installed IDE: $localPath")
                            dependencyManager.resolveLocal(project, localPath, extension.localSourcesPath.orNull)
                        }
                    }
                    if (extension.configureDefaultDependencies.get() && !defaultDependenciesResolved) {
                        defaultDependenciesResolved = true
                        info(context, "${ideaDependency.buildNumber} is used for building")
                        dependencyManager.register(project, ideaDependency, ideaConfiguration.dependencies)
                        ideaConfiguration.resolve()

                        if (!ideaDependency.extraDependencies.isEmpty()) {
                            info(
                                context,
                                "Note: ${ideaDependency.buildNumber} extra dependencies (${ideaDependency.extraDependencies}) should be applied manually",
                            )
                        }
                    } else {
                        info(context, "IDE ${ideaDependency.buildNumber} dependencies are applied manually")
                    }

                    ideaDependency
                })
                finalizeValueOnRead()
            }

            Jvm.current().toolsJar?.let { toolsJar ->
                project.dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, project.files(toolsJar))
            }
        }
    }

    private fun configurePluginDependencies(
        project: Project,
        task: SetupDependenciesTask,
        extension: IntelliJPluginExtension,
        configuration: Configuration,
    ) {
        configuration.withDependencies {
            info(context, "Configuring plugin dependencies")
            val ideaDependency = task.idea.get()
            val ideVersion = IdeVersion.createIdeVersion(ideaDependency.buildNumber)
            val resolver = project.objects.newInstance(
                PluginDependencyManager::class.java,
                project.gradle.gradleUserHomeDir.absolutePath,
                ideaDependency,
                extension.getPluginsRepositories(),
                archiveUtils,
                context,
            )
            extension.plugins.get().forEach {
                info(context, "Configuring plugin: $it")
                if (it is Project) {
                    configureProjectPluginDependency(project, it, this, extension)
                } else {
                    val pluginDependency = PluginDependencyNotation.parsePluginDependencyString(it.toString())
                    if (pluginDependency.id.isEmpty()) {
                        throw BuildException("Failed to resolve plugin: $it", null)
                    }
                    val plugin = resolver.resolve(project, pluginDependency) ?: throw BuildException("Failed to resolve plugin $it", null)
                    if (!plugin.isCompatible(ideVersion)) {
                        throw BuildException("Plugin '$it' is not compatible to: ${ideVersion.asString()}", null)
                    }
                    configurePluginDependency(project, plugin, extension, this, resolver)
                }
            }
            if (extension.configureDefaultDependencies.get()) {
                configureBuiltinPluginsDependencies(project, this, resolver, extension, ideaDependency)
            }
            verifyJavaPluginDependency(project, ideaDependency, extension.plugins.get())
            extension.getPluginsRepositories().forEach {
                it.postResolve(project, context)
            }
        }
    }

    private fun configureClassPathIndexCleanupTask(project: Project) {
        info(context, "Configuring setup dependencies task")

        project.tasks.register(IntelliJPluginConstants.CLASSPATH_INDEX_CLEANUP_TASK_NAME, ClasspathIndexCleanupTask::class.java) {
            val setupDependenciesTaskProvider =
                project.tasks.named<SetupDependenciesTask>(IntelliJPluginConstants.SETUP_DEPENDENCIES_TASK_NAME)
            val setupDependenciesTask = setupDependenciesTaskProvider.get()

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Removes classpath index files created by PathClassLoader"

            classpathIndexFiles.from(project.provider {
                (project.extensions.findByName("sourceSets") as SourceSetContainer)
                    .flatMap { it.output.classesDirs + it.output.generatedSourcesDirs + project.files(it.output.resourcesDir) }
                    .mapNotNull { dir -> dir.resolve("classpath.index").takeIf { it.exists() } }
            })

            val buildNumberProvider = project.provider {
                setupDependenciesTask.idea.get().buildNumber
            }

            onlyIf {
                val ideVersion = IdeVersion.createIdeVersion(buildNumberProvider.get())
                ideVersion.baselineVersion >= 221
            }

            dependsOn(setupDependenciesTaskProvider)
        }
    }

    private fun resolveBuildTaskOutput(project: Project): File? {
        val buildPluginTaskProvider = project.tasks.named<Zip>(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        val buildPluginTask = buildPluginTaskProvider.get()
        return buildPluginTask.archiveFile.orNull?.asFile?.takeIf { it.exists() }
    }

    private fun getCurrentVersion() =
        IntelliJPlugin::class.java
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
    private fun IdeVersion.stripExcessComponents() = asStringWithoutProductCode().split(".")
        .filterIndexed { index, component -> index < 3 || component == "SNAPSHOT" || component == "*" }
        .joinToString(prefix = "$productCode-", separator = ".")
        .let(IdeVersion::createIdeVersion)
}
