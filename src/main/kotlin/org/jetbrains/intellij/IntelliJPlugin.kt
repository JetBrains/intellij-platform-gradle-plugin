package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.tasks.ClasspathNormalizer
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.named
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.jetbrains.intellij.IntelliJPluginConstants.VERSION_LATEST
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginDependencyManager
import org.jetbrains.intellij.dependency.PluginDependencyNotation
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.jbr.JbrResolver
import org.jetbrains.intellij.tasks.BuildSearchableOptionsTask
import org.jetbrains.intellij.tasks.DownloadRobotServerPluginTask
import org.jetbrains.intellij.tasks.IntelliJInstrumentCodeTask
import org.jetbrains.intellij.tasks.JarSearchableOptionsTask
import org.jetbrains.intellij.tasks.ListProductsReleasesTask
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.intellij.tasks.RunIdeBase
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.intellij.tasks.SignPluginTask
import org.jetbrains.intellij.tasks.VerifyPluginTask
import org.jetbrains.intellij.utils.ArchiveUtils
import org.jetbrains.intellij.utils.DependenciesDownloader
import org.jetbrains.intellij.utils.ivyRepository
import org.jetbrains.intellij.utils.mavenRepository
import java.io.File
import java.net.URL
import java.util.EnumSet
import java.util.jar.Manifest

@Suppress("UnstableApiUsage", "unused")
open class IntelliJPlugin : Plugin<Project> {

    private lateinit var archiveUtils: ArchiveUtils
    private lateinit var dependenciesDownloader: DependenciesDownloader
    private lateinit var context: String

    override fun apply(project: Project) {
        archiveUtils = project.objects.newInstance(ArchiveUtils::class.java)
        dependenciesDownloader = project.objects.newInstance(DependenciesDownloader::class.java)
        context = project.logCategory()

        checkGradleVersion(project)
        project.plugins.apply(JavaPlugin::class.java)

        val intellijExtension = project.extensions.create(
            IntelliJPluginConstants.EXTENSION_NAME,
            IntelliJPluginExtension::class.java,
        )

        intellijExtension.apply {
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

        configureConfigurations(project, intellijExtension)
        configureTasks(project, intellijExtension)
    }

    private fun checkGradleVersion(project: Project) {
        if (Version.parse(project.gradle.gradleVersion) < Version.parse("6.6")) {
            throw PluginInstantiationException("gradle-intellij-plugin requires Gradle 6.6 and higher")
        }
    }

    private fun configureConfigurations(project: Project, extension: IntelliJPluginExtension) {
        val idea = project.configurations.create(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME)
            .setVisible(false)
            .apply {
                configureIntellijDependency(project, extension, this)
            }

        val ideaPlugins = project.configurations.create(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME)
            .setVisible(false)
            .apply {
                configurePluginDependencies(project, extension, this)
            }

        val defaultDependencies = project.configurations.create(IntelliJPluginConstants.INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME)
            .setVisible(false)
            .apply {
                defaultDependencies {
                    add(project.dependencies.create(
                        group = "org.jetbrains",
                        name = "annotations",
                        version = IntelliJPluginConstants.ANNOTATIONS_DEPENDENCY_VERSION,
                    ))
                }
            }

        project.configurations
            .getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
            .extendsFrom(defaultDependencies, idea, ideaPlugins)
        project.configurations
            .getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME)
            .extendsFrom(defaultDependencies, idea, ideaPlugins)
    }

    private fun configureTasks(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring plugin")
        project.tasks.whenTaskAdded {
            when (this) {
                is RunIdeForUiTestTask -> prepareConventionMappingsForRunIdeTask(project,
                    extension,
                    this,
                    IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
                is RunIdeBase -> prepareConventionMappingsForRunIdeTask(project,
                    extension,
                    this,
                    IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            }
        }
        configurePatchPluginXmlTask(project, extension)
        configureRobotServerDownloadTask(project)
        configurePrepareSandboxTasks(project, extension)
        configureListProductsReleasesTask(project, extension)
        configureRunPluginVerifierTask(project, extension)
        configurePluginVerificationTask(project)
        configureRunIdeaTask(project)
        configureRunIdeaForUiTestsTask(project)
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
        }
    }

    private fun configureProjectAfterEvaluate(project: Project, extension: IntelliJPluginExtension) {
        project.subprojects.forEach { subproject ->
            if (subproject.plugins.findPlugin(IntelliJPlugin::class.java) == null) {
                subproject.extensions.getByType(IntelliJPluginExtension::class.java).let {
                    configureProjectAfterEvaluate(subproject, it)
                }
            }
        }

        configureTestTasks(project, extension)
    }

    private fun configureIntellijDependency(project: Project, extension: IntelliJPluginExtension, configuration: Configuration) {
        info(context, "Configuring IDE dependency")
        var defaultDependenciesResolved = false

        extension.ideaDependency.convention(project.provider {
            val dependencyManager = project.objects.newInstance(
                IdeaDependencyManager::class.java,
                extension.intellijRepository.get(),
                extension.ideaDependencyCachePath.orNull ?: "",
                archiveUtils,
                dependenciesDownloader,
                context,
            )
            val ideaDependency = when (val localPath = extension.localPath.orNull) {
                null -> {
                    info(context, "Using IDE from remote repository")
                    val version = extension.getVersionNumber() ?: IntelliJPluginConstants.DEFAULT_IDEA_VERSION
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
                        warn(context, "Both 'localPath' and 'version' specified, second would be ignored")
                    }
                    info(context, "Using path to locally installed IDE: $localPath")
                    dependencyManager.resolveLocal(project, localPath, extension.localSourcesPath.orNull)
                }
            }
            if (extension.configureDefaultDependencies.get() && !defaultDependenciesResolved) {
                defaultDependenciesResolved = true
                info(context, "${ideaDependency.buildNumber} is used for building")
                dependencyManager.register(project, ideaDependency, configuration.dependencies)
                configuration.resolve()

                if (!ideaDependency.extraDependencies.isEmpty()) {
                    info(context,
                        "Note: ${ideaDependency.buildNumber} extra dependencies (${ideaDependency.extraDependencies}) should be applied manually")
                }
            } else {
                info(context, "IDE ${ideaDependency.buildNumber} dependencies are applied manually")
            }

            ideaDependency
        })
        Jvm.current().toolsJar?.let { toolsJar ->
            project.dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, project.files(toolsJar))
        }

        project.afterEvaluate {
            extension.ideaDependency.get()
        }
    }

    private fun configurePluginDependencies(project: Project, extension: IntelliJPluginExtension, configuration: Configuration) {
        configuration.withDependencies {
            info(context, "Configuring plugin dependencies")
            val ideaDependency = extension.ideaDependency.get()
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
                configureBuiltinPluginsDependencies(project, this, resolver, extension)
            }
            verifyJavaPluginDependency(extension, project)
            extension.getPluginsRepositories().forEach {
                it.postResolve(project, context)
            }
        }

        project.afterEvaluate {
            extension.plugins.get().filterIsInstance<Project>().forEach { dependency ->
                if (dependency.state.executed) {
                    configureProjectPluginTasksDependency(project, dependency)
                } else {
                    dependency.afterEvaluate {
                        configureProjectPluginTasksDependency(project, dependency)
                    }
                }
            }
        }
    }

    private fun verifyJavaPluginDependency(extension: IntelliJPluginExtension, project: Project) {
        val plugins = extension.plugins.get()
        val hasJavaPluginDependency = plugins.contains("java") || plugins.contains("com.intellij.java")
        if (!hasJavaPluginDependency && File(extension.ideaDependency.get().classes, "plugins/java").exists()) {
            sourcePluginXmlFiles(project).forEach { file ->
                parsePluginXml(file, context)?.dependencies?.forEach {
                    if (it.dependencyId == "com.intellij.modules.java") {
                        throw BuildException(
                            "The project depends on 'com.intellij.modules.java' module but doesn't declare a compile dependency on it.\n" +
                                "Please delete 'depends' tag from '${file.absolutePath}' or add 'java' plugin to Gradle dependencies (e.g. intellij { plugins = ['java'] })",
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
    ) {
        val configuredPlugins = extension.getUnresolvedPluginDependencies()
            .filter(PluginDependency::builtin)
            .map(PluginDependency::id)
        extension.ideaDependency.get().pluginsRegistry.collectBuiltinDependencies(configuredPlugins).forEach {
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

    private fun configureProjectPluginTasksDependency(project: Project, dependency: Project) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPlugin::class.java) == null) {
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin not found." + dependency.plugins, null)
        }
        dependency.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) {
            project.tasks.withType(PrepareSandboxTask::class.java).forEach {
                it.dependsOn(this)
            }
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
        val dependencyDirectory = prepareSandboxTaskProvider.get().run { File(destinationDir, pluginName.get()) }

        val pluginDependency = PluginProjectDependency(dependencyDirectory, context)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType(PrepareSandboxTask::class.java).forEach {
            it.configureCompositePlugin(pluginDependency)
        }
    }

    private fun configurePatchPluginXmlTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring patch plugin.xml task")
        project.tasks.register(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"

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
                    val ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.get().buildNumber)
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
                        val ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.get().buildNumber)
                        "${ideVersion.baselineVersion}.*"
                    }
                } else {
                    null
                }
            })
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
            description = "Download robot-server plugin."

            version.convention(VERSION_LATEST)
            outputDir.convention(project.provider {
                project.layout.projectDirectory.dir("${project.buildDir}/robotServerPlugin")
            })
            pluginArchive.convention(project.provider {
                val resolvedVersion = DownloadRobotServerPluginTask.resolveVersion(version.orNull)
                val (group, name) = DownloadRobotServerPluginTask.getDependency(resolvedVersion).split(':')
                dependenciesDownloader.downloadFromRepository(logCategory(), {
                    create(
                        group = group,
                        name = name,
                        version = resolvedVersion,
                    )
                }, {
                    mavenRepository(IntelliJPluginConstants.INTELLIJ_DEPENDENCIES)
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
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Prepare sandbox directory with installed plugin and its dependencies."

            pluginName.convention(extension.pluginName)
            pluginJar.convention(project.layout.file(project.provider {
                val jarTaskProvider = project.tasks.named<Jar>(JavaPlugin.JAR_TASK_NAME)
                val jarTask = jarTaskProvider.get()
                jarTask.manifest.attributes(
                    "Created-By" to "Gradle ${project.gradle.gradleVersion}",
                    "Build-JVM" to Jvm.current(),
                    "Version" to project.version,
                    "Build-Plugin" to IntelliJPluginConstants.NAME,
                    "Build-Plugin-Version" to getVersion(),
                    "Build-OS" to OperatingSystem.current(),
                    "Build-SDK" to when (extension.localPath.orNull) {
                        null -> "${extension.getVersionType()}-${extension.getVersionNumber()}"
                        else -> ideProductInfo(extension.ideaDependency.get().classes)?.run { "$productCode-$version" }
                    },
                )
                jarTask.archiveFile.orNull?.asFile
            }))
            defaultDestinationDir.convention(project.provider {
                project.file("${extension.sandboxDir.get()}/plugins$testSuffix")
            })
            configDir.convention(project.provider {
                "${extension.sandboxDir.get()}/config$testSuffix"
            })
            librariesToIgnore.convention(project.provider {
                project.files(extension.ideaDependency.get().jarFiles)
            })
            pluginDependencies.convention(project.provider {
                extension.getPluginDependenciesList(project)
            })

            dependsOn(JavaPlugin.JAR_TASK_NAME)
            dependsOn(project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME))

            configure?.invoke(this)
        }
    }

    private fun configureRunPluginVerifierTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring run plugin verifier task")
        project.tasks.register(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, RunPluginVerifierTask::class.java) {
            val taskContext = logCategory()
            val listProductsReleasesTaskProvider =
                project.tasks.named<ListProductsReleasesTask>(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME)
            val listProductsReleasesTask = listProductsReleasesTaskProvider.get()

            group = IntelliJPluginConstants.GROUP_NAME
            description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds."

            failureLevel.convention(EnumSet.of(RunPluginVerifierTask.FailureLevel.COMPATIBILITY_PROBLEMS))
            verifierVersion.convention(VERSION_LATEST)
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
                val ideVersions = ideVersions.get().takeIf(List<String>::isNotEmpty)
                    ?: run {
                        when {
                            localPaths.get().isEmpty() -> productsReleasesFile.get().takeIf(File::exists)?.readLines()
                            else -> null
                        }
                    }
                    ?: emptyList()

                ideVersions.map { ideVersion ->
                    val downloadDir = File(downloadDir.get())

                    RunPluginVerifierTask.resolveIdePath(ideVersion, downloadDir, taskContext) { type, version, buildType ->
                        val name = "$type-$version"
                        val ideDir = downloadDir.resolve(name)
                        info(context, "Downloading IDE '$name' to: $ideDir")

                        val url = RunPluginVerifierTask.resolveIdeUrl(type, version, buildType, taskContext)
                        debug(context, "Downloading IDE from $url")

                        try {
                            val ideArchive = dependenciesDownloader.downloadFromRepository(logCategory(), {
                                create(
                                    group = "com.jetbrains",
                                    name = "ides",
                                    version = "$type-$version-$buildType",
                                    ext = "tar.gz",
                                )
                            }, {
                                ivyRepository(url)
                            }).first()

                            debug(context, "IDE downloaded, extracting...")
                            archiveUtils.extract(ideArchive, ideDir, taskContext)
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
                val resolvedVerifierVersion = RunPluginVerifierTask.resolveVerifierVersion(verifierVersion.orNull)
                debug(context, "Using Verifier in '$resolvedVerifierVersion' version")

                dependenciesDownloader.downloadFromRepository(taskContext, {
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
            description = "Validates completeness and contents of plugin.xml descriptors as well as plugin’s archive structure."

            pluginDir.convention(project.provider {
                val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
                val prepareSandboxTask = prepareSandboxTaskProvider.get()
                val path = File(prepareSandboxTask.destinationDir, prepareSandboxTask.pluginName.get()).path
                project.layout.projectDirectory.dir(path)
            })

            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private fun configureRunIdeaTask(project: Project) {
        info(context, "Configuring run IDE task")
        project.tasks.register(IntelliJPluginConstants.RUN_IDE_TASK_NAME, RunIdeTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Runs Intellij IDEA with installed plugin."

            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private fun configureRunIdeaForUiTestsTask(project: Project) {
        info(context, "Configuring run IDE for ui tests task")
        project.tasks.register(IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME, RunIdeForUiTestTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Runs Intellij IDEA with installed plugin and robot-server plugin for ui tests."

            dependsOn(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
        }
    }

    private fun configureBuildSearchableOptionsTask(project: Project) {
        info(context, "Configuring build searchable options task")
        project.tasks.register(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, BuildSearchableOptionsTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Builds searchable options for plugin."

            outputDir.convention(project.provider {
                project.layout.projectDirectory.dir("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}")
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
        val taskContext = task.logCategory()
        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it, taskContext)?.id }

        task.ideDir.convention(project.provider {
            val path = extension.ideaDependency.get().classes.path
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
                extension.jreRepository.orNull ?: "",
                project.gradle.startParameter.isOffline,
                archiveUtils,
                dependenciesDownloader,
                taskContext,
            )

            jbrResolver.resolveRuntimeDir(
                jbrVersion = task.jbrVersion.orNull,
                ideDir = task.ideDir.orNull,
            )
        })
    }

    private fun configureJarSearchableOptionsTask(project: Project) {
        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        val prepareSandboxTask = prepareSandboxTaskProvider.get()

        info(context, "Configuring jar searchable options task")
        val buildDir = project.buildDir
        project.tasks.register(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME, JarSearchableOptionsTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Jars searchable options."

            outputDir.convention(project.provider {
                project.layout.projectDirectory.dir("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}")
            })
            pluginName.convention(prepareSandboxTask.pluginName)
            sandboxDir.convention(project.provider {
                prepareSandboxTask.destinationDir.canonicalPath
            })
            archiveBaseName.convention("lib/searchableOptions")
            destinationDirectory.convention(project.layout.buildDirectory.dir("libsSearchableOptions"))

            dependsOn(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
            dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            onlyIf { File(buildDir, IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME).isDirectory }
        }
    }

    private fun configureInstrumentation(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring compile tasks")
        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
        val instrumentCode = project.provider { extension.instrumentCode.get() }

        sourceSets.forEach { sourceSet ->
            val instrumentTask =
                project.tasks.register(sourceSet.getTaskName("instrument", "code"), IntelliJInstrumentCodeTask::class.java) {
                    sourceSetOutputClassesDirs.convention(project.provider {
                        sourceSet.output.classesDirs.files
                    })
                    sourceSetAllDirs.convention(project.provider {
                        sourceSet.allSource.srcDirs
                    })
                    sourceSetResources.convention(project.provider {
                        sourceSet.resources.files
                    })
                    sourceSetCompileClasspath.convention(project.provider {
                        sourceSet.compileClasspath
                    })
                    compilerVersion.convention(project.provider {
                        val version = extension.getVersionNumber() ?: IntelliJPluginConstants.DEFAULT_IDEA_VERSION
                        val localPath = extension.localPath.orNull
                        val ideaDependency = extension.ideaDependency.get()

                        if (localPath.isNullOrBlank() && version.endsWith("-SNAPSHOT")) {
                            val type = extension.getVersionType()
                            if (version == IntelliJPluginConstants.DEFAULT_IDEA_VERSION && listOf("CL", "RD", "PY").contains(type)) {
                                ideProductInfo(ideaDependency.classes)?.buildNumber?.let { buildNumber ->
                                    Version.parse(buildNumber).let { v -> "${v.major}.${v.minor}-EAP-CANDIDATE-SNAPSHOT" }
                                } ?: version
                            } else {
                                when (type) {
                                    "CL" -> "CLION-$version"
                                    "RD" -> "RIDER-$version"
                                    "PY" -> "PYCHARM-$version"
                                    else -> version
                                }
                            }
                        } else {
                            val isEap = localPath?.let { ideProductInfo(ideaDependency.classes)?.versionSuffix == "EAP" } ?: false
                            val eapSuffix = "-EAP-SNAPSHOT".takeIf { isEap } ?: ""

                            IdeVersion.createIdeVersion(ideaDependency.buildNumber).asStringWithoutProductCode() + eapSuffix
                        }
                    })
                    ideaDependency.convention(extension.ideaDependency)
                    javac2.convention(project.provider {
                        project.file("${extension.ideaDependency.get().classes}/lib/javac2.jar").takeIf(File::exists)
                    })
                    compilerClassPathFromMaven.convention(project.provider {
                        val compilerVersion = compilerVersion.get()
                        if (compilerVersion == IntelliJPluginConstants.DEFAULT_IDEA_VERSION ||
                            Version.parse(compilerVersion) >= Version(183, 3795, 13)
                        ) {
                            dependenciesDownloader.downloadFromMultipleRepositories(
                                logCategory(),
                                {
                                    create(
                                        group = "com.jetbrains.intellij.java",
                                        name = "java-compiler-ant-tasks",
                                        version = compilerVersion,
                                    )
                                },
                                {
                                    listOf(
                                        "${extension.intellijRepository.get()}/${releaseType(compilerVersion)}",
                                        IntelliJPluginConstants.INTELLIJ_DEPENDENCIES,
                                    ).map { url -> mavenRepository(url) }
                                }
                            )
                        } else {
                            warn(
                                logCategory(),
                                "Compiler in '$compilerVersion' version can't be resolved from Maven. Minimal version supported: 2018.3+. " +
                                    "Use higher 'intellij.version' or specify the 'compilerVersion' property manually.",
                            )
                            null
                        }
                    })

                    outputDir.convention(project.provider {
                        val classesDir = sourceSet.output.classesDirs.first()
                        val outputDir = File(classesDir.parentFile, "${sourceSet.name}-instrumented")
                        project.layout.projectDirectory.dir(outputDir.path)
                    })

                    dependsOn(sourceSet.classesTaskName)
                    onlyIf { instrumentCode.get() }
                }

            // A dedicated task ensures that sources substitution is always run,
            // even when the instrumentCode task is up-to-date.
            val updateTask = project.tasks.register("post${instrumentTask.name.capitalize()}") {
                val classesDirs = sourceSet.output.classesDirs as ConfigurableFileCollection
                val outputDir = instrumentTask.get().outputDir

                dependsOn(instrumentTask)
                onlyIf { instrumentCode.get() }
                // Set the classes' dir to the one with the instrumented classes
                doLast { classesDirs.setFrom(outputDir) }
            }

            // Ensure that our task is invoked when the source set is built
            sourceSet.compiledBy(updateTask)
        }
    }

    private fun configureTestTasks(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring tests tasks")
        val testTasks = project.tasks.withType(Test::class.java)
        val prepareTestingSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME)
        val runIdeTaskProvider = project.tasks.named<RunIdeTask>(IntelliJPluginConstants.RUN_IDE_TASK_NAME)
        val runIdeTask = runIdeTaskProvider.get()

        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it, context)?.id }
        val sandboxDir = extension.sandboxDir.get()
        val configDirectory = project.file("$sandboxDir/config-test").apply { mkdirs() }
        val systemDirectory = project.file("$sandboxDir/system-test").apply { mkdirs() }
        val pluginsDirectory = project.file("$sandboxDir/plugins-test").apply { mkdirs() }

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

            task.outputs
                .dir(project.provider {
                    systemDirectory.apply { mkdirs() }
                })
                .withPropertyName("System directory")
            task.inputs
                .dir(project.provider {
                    configDirectory.apply { mkdirs() }
                })
                .withPropertyName("Config Directory")
                .withPathSensitivity(PathSensitivity.RELATIVE)
            task.inputs
                .files(prepareTestingSandboxTaskProvider.map {
                    it.destinationDir
                })
                .withPropertyName("Plugins directory")
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .withNormalizer(ClasspathNormalizer::class.java)

            val ideaDependencyLibraries =
                project.provider {
                    val classes = extension.ideaDependency.get().classes
                    project.files(
                        "$classes/lib/resources.jar",
                        "$classes/lib/idea.jar"
                    )
                }
            val ideDirectory = project.provider {
                runIdeTask.ideDir.get()
            }

            // Use an anonymous class, since lambdas disable caching for the task.
            @Suppress("ObjectLiteralToLambda")
            task.doFirst(object : Action<Task> {
                override fun execute(t: Task) {
                    task.jvmArgs = getIdeJvmArgs(task, task.jvmArgs, ideDirectory.get())
                    task.classpath += ideaDependencyLibraries.get()

                    task.systemProperties(getIdeaSystemProperties(configDirectory, systemDirectory, pluginsDirectory, pluginIds))

                    // since 193 plugins from classpath are loaded before plugins from plugins directory
                    // to handle this, use plugin.path property as task's the very first source of plugins
                    // we cannot do this for IDEA < 193, as plugins from plugin.path can be loaded twice
                    val ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.get().buildNumber)
                    if (ideVersion.baselineVersion >= 193) {
                        task.systemProperty(
                            IntelliJPluginConstants.PLUGIN_PATH,
                            pluginsDirectory.listFiles()?.joinToString("${File.pathSeparator},") { it.path } ?: "",
                        )
                    }
                }
            })
        }
    }

    private fun configureBuildPluginTask(project: Project) {
        info(context, "Configuring building plugin task")
        val prepareSandboxTaskProvider = project.tasks.named<PrepareSandboxTask>(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        val prepareSandboxTask = prepareSandboxTaskProvider.get()
        val jarSearchableOptionsTaskProvider = project.tasks.named<JarSearchableOptionsTask>(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)
        val jarSearchableOptionsTask = jarSearchableOptionsTaskProvider.get()

        project.tasks.register(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME, Zip::class.java) {
            description = "Bundles the project as a distribution."
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
            description = "Sign plugin with your private key and certificate chain."

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
            cliVersion.convention(VERSION_LATEST)
            cliPath.convention(project.provider {
                val resolvedCliVersion = SignPluginTask.resolveCliVersion(cliVersion.orNull)
                val url = SignPluginTask.resolveCliUrl(resolvedCliVersion)
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

            onlyIf { (privateKey.isPresent || privateKeyFile.isPresent) && (certificateChain.isPresent || certificateChainFile.isPresent) }
            dependsOn(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        }
    }

    private fun configurePublishPluginTask(project: Project) {
        info(context, "Configuring publish plugin task")
        val buildPluginTaskProvider = project.tasks.named<Zip>(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        val verifyPluginTaskProvider = project.tasks.named<VerifyPluginTask>(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        val signPluginTaskProvider = project.tasks.named<SignPluginTask>(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)
        val isOffline = project.gradle.startParameter.isOffline

        project.tasks.register(IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME, PublishPluginTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Publish plugin distribution on plugins.jetbrains.com."

            host.convention(IntelliJPluginConstants.MARKETPLACE_HOST)
            channels.convention(listOf("default"))
            distributionFile.convention(project.layout.file(project.provider {
                signPluginTaskProvider.get().let { signPluginTask ->
                    signPluginTask.outputArchiveFile.orNull?.asFile.takeIf { signPluginTask.didWork } ?: resolveBuildTaskOutput(project)
                }
            }))

            dependsOn(buildPluginTaskProvider)
            dependsOn(verifyPluginTaskProvider)
            dependsOn(signPluginTaskProvider)
            onlyIf { !isOffline }
        }
    }

    private fun configureListProductsReleasesTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring list products task")

        project.tasks.register(IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME, ListProductsReleasesTask::class.java) {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "List all available IntelliJ-based IDEs with their updates."

            updatesPath.convention(project.provider {
                dependenciesDownloader.downloadFromRepository(logCategory(), {
                    create(
                        group = "org.jetbrains",
                        name = "products-releases",
                        version = "1.0",
                        ext = "xml",
                    )
                }, {
                    ivyRepository(IntelliJPluginConstants.PRODUCTS_RELEASES_URL)
                }).first().canonicalPath
            })
            outputFile.convention {
                File(project.buildDir, "${IntelliJPluginConstants.LIST_PRODUCTS_RELEASES_TASK_NAME}.txt")
            }
            types.convention(project.provider {
                listOf(extension.type.get())
            })
            sinceVersion.convention(extension.version)
            includeEAP.convention(true)
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

    private fun resolveBuildTaskOutput(project: Project): File? {
        val buildPluginTaskProvider = project.tasks.named<Zip>(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        val buildPluginTask = buildPluginTaskProvider.get()
        return buildPluginTask.archiveFile.orNull?.asFile?.takeIf { it.exists() }
    }

    private fun getVersion() = IntelliJPlugin::class.java
        .run { getResource("$simpleName.class")?.toString() }
        ?.takeIf { it.startsWith("jar") }
        ?.runCatching {
            val manifestPath = substring(0, lastIndexOf("!") + 1) + "/META-INF/MANIFEST.MF"
            Manifest(URL(manifestPath).openStream()).mainAttributes.getValue("Version")
        }
        ?.getOrNull() ?: ""
}
