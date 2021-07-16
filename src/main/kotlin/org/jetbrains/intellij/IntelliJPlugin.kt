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
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
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
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishPluginTask
import org.jetbrains.intellij.tasks.RunIdeBase
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.intellij.tasks.SignPluginTask
import org.jetbrains.intellij.tasks.VerifyPluginTask
import java.io.File
import java.util.EnumSet

@Suppress("UnstableApiUsage", "unused")
open class IntelliJPlugin : Plugin<Project> {

    private lateinit var context: String

    override fun apply(project: Project) {
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
        val idea = project.configurations.create(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME).setVisible(false)
        configureIntellijDependency(project, extension, idea)

        val ideaPlugins = project.configurations.create(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME).setVisible(false)
        configurePluginDependencies(project, extension, ideaPlugins)

        val defaultDependencies = project.configurations.create(IntelliJPluginConstants.INTELLIJ_DEFAULT_DEPENDENCIES_CONFIGURATION_NAME).setVisible(false)
        defaultDependencies.defaultDependencies {
            it.add(project.dependencies.create(
                group = "org.jetbrains",
                name = "annotations",
                version = IntelliJPluginConstants.ANNOTATIONS_DEPENDENCY_VERSION,
            ))
        }

        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME)
            .extendsFrom(defaultDependencies, idea, ideaPlugins)
        project.configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME)
            .extendsFrom(defaultDependencies, idea, ideaPlugins)
    }

    private fun configureTasks(
        project: Project,
        extension: IntelliJPluginExtension,
    ) {
        info(context, "Configuring plugin")
        project.tasks.whenTaskAdded {
            if (it is RunIdeBase) {
                prepareConventionMappingsForRunIdeTask(project, extension, it, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            }
            if (it is RunIdeForUiTestTask) {
                prepareConventionMappingsForRunIdeTask(project, extension, it, IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
            }
        }
        configurePatchPluginXmlTask(project, extension)
        configureRobotServerDownloadTask(project)
        configurePrepareSandboxTasks(project, extension)
        configureRunPluginVerifierTask(project)
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
            configureProjectAfterEvaluate(it, extension)
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

    private fun configureIntellijDependency(project: Project, extension: IntelliJPluginExtension, configuration: Configuration) {
        configuration.withDependencies { dependencies ->
            info(context, "Configuring IDE dependency")
            val resolver = project.objects.newInstance(
                IdeaDependencyManager::class.java,
                extension.intellijRepository.get(),
                extension.ideaDependencyCachePath.orNull ?: "",
                context,
            )
            val ideaDependency = when (val localPath = extension.localPath.orNull) {
                null -> {
                    info(context, "Using IDE from remote repository")
                    val version = extension.getVersionNumber() ?: IntelliJPluginConstants.DEFAULT_IDEA_VERSION
                    val extraDependencies = extension.extraDependencies.get()
                    resolver.resolveRemote(project, version, extension.getVersionType(), extension.downloadSources.get(), extraDependencies)
                }
                else -> {
                    if (extension.version.orNull != null) {
                        warn(context, "Both 'localPath' and 'version' specified, second would be ignored")
                    }
                    info(context, "Using path to locally installed IDE: $localPath")
                    resolver.resolveLocal(project, localPath, extension.localSourcesPath.orNull)
                }
            }
            extension.ideaDependency.set(ideaDependency)
            if (extension.configureDefaultDependencies.get()) {
                info(context, "${ideaDependency.buildNumber} is used for building")
                resolver.register(project, ideaDependency, dependencies)
                if (!ideaDependency.extraDependencies.isEmpty()) {
                    info(context,
                        "Note: ${ideaDependency.buildNumber} extra dependencies (${ideaDependency.extraDependencies}) should be applied manually")
                }
            } else {
                info(context, "IDE ${ideaDependency.buildNumber} dependencies are applied manually")
            }
        }
        Jvm.current().toolsJar?.let { toolsJar ->
            project.dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, project.files(toolsJar))
        }
    }

    private fun configurePluginDependencies(project: Project, extension: IntelliJPluginExtension, configuration: Configuration) {
        configuration.withDependencies { dependencies ->
            info(context, "Configuring plugin dependencies")
            val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
            val resolver = project.objects.newInstance(
                PluginDependencyManager::class.java,
                project.gradle.gradleUserHomeDir.absolutePath,
                extension.getIdeaDependency(project),
                extension.getPluginsRepositories(),
                context,
            )
            extension.plugins.get().forEach {
                info(context, "Configuring plugin: $it")
                if (it is Project) {
                    configureProjectPluginDependency(project, it, dependencies, extension)
                } else {
                    val pluginDependency = PluginDependencyNotation.parsePluginDependencyString(it.toString())
                    if (pluginDependency.id.isEmpty()) {
                        throw BuildException("Failed to resolve plugin: $it", null)
                    }
                    val plugin = resolver.resolve(project, pluginDependency) ?: throw BuildException("Failed to resolve plugin $it", null)
                    if (!plugin.isCompatible(ideVersion)) {
                        throw BuildException("Plugin '$it' is not compatible to: ${ideVersion.asString()}", null)
                    }
                    configurePluginDependency(project, plugin, extension, dependencies, resolver)
                }
            }
            if (extension.configureDefaultDependencies.get()) {
                configureBuiltinPluginsDependencies(project, dependencies, resolver, extension)
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
        if (!hasJavaPluginDependency && File(extension.getIdeaDependency(project).classes, "plugins/java").exists()) {
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
            it.configureExternalPlugin(plugin)
        }
    }

    private fun configureProjectPluginTasksDependency(project: Project, dependency: Project) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPlugin::class.java) == null) {
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        dependency.tasks.named(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) { dependencySandboxTask ->
            project.tasks.withType(PrepareSandboxTask::class.java).forEach {
                it.dependsOn(dependencySandboxTask)
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
            throw BuildException("Cannot use '$dependency' as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        dependencies.add(project.dependencies.create(dependency))
        val pluginDependency = PluginProjectDependency(dependency, context)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType(PrepareSandboxTask::class.java).forEach {
            it.configureCompositePlugin(pluginDependency)
        }
    }

    private fun configurePatchPluginXmlTask(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring patch plugin.xml task")
        project.tasks.register(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"

            it.version.convention(project.provider {
                project.version.toString()
            })
            it.pluginXmlFiles.convention(project.provider {
                sourcePluginXmlFiles(project)
            })
            it.destinationDir.convention(project.layout.dir(project.provider {
                File(project.buildDir, IntelliJPluginConstants.PLUGIN_XML_DIR_NAME)
            }))
            it.sinceBuild.convention(project.provider {
                if (extension.updateSinceUntilBuild.get()) {
                    val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
                    "${ideVersion.baselineVersion}.${ideVersion.build}"
                } else {
                    null
                }
            })
            it.untilBuild.convention(project.provider {
                if (extension.updateSinceUntilBuild.get()) {
                    if (extension.sameSinceUntilBuild.get()) {
                        "${it.sinceBuild.get()}.*"
                    } else {
                        val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
                        "${ideVersion.baselineVersion}.*"
                    }
                } else {
                    null
                }
            })
        }
    }

    private fun configurePrepareSandboxTasks(project: Project, extension: IntelliJPluginExtension) {
        val downloadPluginTaskProvider = project.tasks.named(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME)

        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, "")
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME, "-test")
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME, "-uiTest") {
            val downloadPluginTask = downloadPluginTaskProvider.get() as DownloadRobotServerPluginTask

            it.from(downloadPluginTask.outputDir.get())
            it.dependsOn(downloadPluginTask)
        }
    }

    private fun configureRobotServerDownloadTask(project: Project) {
        info(context, "Configuring robot-server download Task")

        project.tasks.register(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME, DownloadRobotServerPluginTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Download robot-server plugin."

            it.version.convention(VERSION_LATEST)
            it.outputDir.convention(project.layout.projectDirectory.dir("${project.buildDir}/robotServerPlugin"))
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
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Prepare sandbox directory with installed plugin and its dependencies."

            it.pluginName.convention(project.provider {
                extension.pluginName.get()
            })
            it.pluginJar.convention(project.layout.file(project.provider {
                val jarTaskProvider = project.tasks.named(JavaPlugin.JAR_TASK_NAME)
                val jarTask = jarTaskProvider.get() as Zip
                jarTask.archiveFile.orNull?.asFile
            }))
            it.defaultDestinationDir.convention(project.provider {
                project.file("${extension.sandboxDir.get()}/plugins$testSuffix")
            })
            it.configDir.convention(project.provider {
                "${extension.sandboxDir.get()}/config$testSuffix"
            })
            it.librariesToIgnore.convention(project.provider {
                project.files(extension.getIdeaDependency(project).jarFiles)
            })
            it.pluginDependencies.convention(project.provider {
                extension.getPluginDependenciesList(project)
            })

            it.dependsOn(JavaPlugin.JAR_TASK_NAME)
            it.dependsOn(project.configurations.getByName(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME))

            configure?.invoke(it)
        }
    }

    private fun configureRunPluginVerifierTask(project: Project) {
        info(context, "Configuring run plugin verifier task")
        project.tasks.register(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, RunPluginVerifierTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds."

            it.failureLevel.convention(EnumSet.of(RunPluginVerifierTask.FailureLevel.INVALID_PLUGIN))
            it.verifierVersion.convention(VERSION_LATEST)
            it.distributionFile.convention(project.layout.file(project.provider {
                resolveBuildTaskOutput(project)
            }))
            it.verificationReportsDir.convention(project.provider {
                "${project.buildDir}/reports/pluginVerifier"
            })
            it.downloadDir.convention(project.provider {
                it.ideDownloadDir().toString()
            })
            it.teamCityOutputFormat.convention(false)
            it.subsystemsToCheck.convention("all")
            it.ideDir.convention(project.provider {
                val runIdeTaskProvider = project.tasks.named(IntelliJPluginConstants.RUN_IDE_TASK_NAME)
                val runIdeTask = runIdeTaskProvider.get() as RunIdeTask
                runIdeTask.ideDir.get()
            })

            it.dependsOn(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
            it.dependsOn(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)

            it.outputs.upToDateWhen { false }
        }
    }

    private fun configurePluginVerificationTask(project: Project) {
        info(context, "Configuring plugin verification task")
        project.tasks.register(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, VerifyPluginTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Validates completeness and contents of plugin.xml descriptors as well as plugin’s archive structure."

            it.pluginDir.convention(project.provider {
                val prepareSandboxTaskProvider = project.tasks.named(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
                val prepareSandboxTask = prepareSandboxTaskProvider.get() as PrepareSandboxTask
                val path = File(prepareSandboxTask.destinationDir, prepareSandboxTask.pluginName.get()).path
                project.layout.projectDirectory.dir(path)
            })

            it.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private fun configureRunIdeaTask(project: Project) {
        info(context, "Configuring run IDE task")
        project.tasks.register(IntelliJPluginConstants.RUN_IDE_TASK_NAME, RunIdeTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Runs Intellij IDEA with installed plugin."

            it.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private fun configureRunIdeaForUiTestsTask(project: Project) {
        info(context, "Configuring run IDE for ui tests task")
        project.tasks.register(IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME, RunIdeForUiTestTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Runs Intellij IDEA with installed plugin and robot-server plugin for ui tests."

            it.dependsOn(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
        }
    }

    private fun configureBuildSearchableOptionsTask(project: Project) {
        info(context, "Configuring build searchable options task")
        project.tasks.register(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, BuildSearchableOptionsTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Builds searchable options for plugin."

            it.args = listOf("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}", "true")
            it.outputs.dir("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}")

            it.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            it.onlyIf { _ ->
                val number = ideBuildNumber(it.ideDir.get().asFile)
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
        val prepareSandboxTaskProvider = project.tasks.named(prepareSandBoxTaskName)
        val prepareSandboxTask = prepareSandboxTaskProvider.get() as PrepareSandboxTask
        val taskContext = task.logCategory()
        val pluginIds = sourcePluginXmlFiles(project).mapNotNull { parsePluginXml(it, taskContext)?.id }

        task.ideDir.convention(project.provider {
            val path = extension.getIdeaDependency(project).classes.path
            project.layout.projectDirectory.dir(path)
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
            val number = ideBuildNumber(task.ideDir.get().asFile)
            Version.parse(number.split('-').last()) >= Version.parse("202.0")
        })
        task.projectWorkingDir.convention(project.provider {
            project.file("${task.ideDir.get().asFile}/bin/")
        })
        task.projectExecutable.convention(project.provider {
            val jbrResolver = project.objects.newInstance(
                JbrResolver::class.java,
                extension.jreRepository.orNull ?: "",
                project.gradle.startParameter.isOffline,
                taskContext,
            )

            task.jbrVersion.orNull?.let {
                jbrResolver.resolve(it)?.javaExecutable ?: null.apply {
                    warn(this, "Cannot resolve JetBrains Runtime '$it'. Falling back to builtin JetBrains Runtime.")
                }
            } ?: getBuiltinJbrVersion(task.ideDir.get().asFile)?.let {
                jbrResolver.resolve(it)?.javaExecutable ?: null.apply {
                    warn(this, "Cannot resolve builtin JetBrains Runtime '$it'. Falling local Java Runtime.")
                }
            } ?: Jvm.current().javaExecutable.absolutePath
        })
    }

    private fun configureJarSearchableOptionsTask(project: Project) {
        info(context, "Configuring jar searchable options task")
        val buildDir = project.buildDir
        project.tasks.register(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME, JarSearchableOptionsTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Jars searchable options."

            it.archiveBaseName.convention("lib/searchableOptions")
            it.destinationDirectory.convention(project.layout.buildDirectory.dir("libsSearchableOptions"))

            it.dependsOn(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
            it.onlyIf { File(buildDir, IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME).isDirectory }
        }
    }

    private fun configureInstrumentation(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring compile tasks")
        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer
        val instrumentCode = project.provider { extension.instrumentCode.get() }

        sourceSets.forEach { sourceSet ->
            val instrumentTask =
                project.tasks.register(sourceSet.getTaskName("instrument", "code"), IntelliJInstrumentCodeTask::class.java) {
                    it.sourceSetOutputClassesDirs.convention(project.provider {
                        sourceSet.output.classesDirs.files
                    })
                    it.sourceSetAllDirs.convention(project.provider {
                        sourceSet.allSource.srcDirs
                    })
                    it.intellijRepository.convention(project.provider {
                        extension.intellijRepository.get()
                    })
                    it.sourceSetResources.convention(project.provider {
                        sourceSet.resources.files
                    })
                    it.sourceSetCompileClasspath.convention(project.provider {
                        sourceSet.compileClasspath
                    })
                    it.compilerVersion.convention(project.provider {
                        val version = extension.getVersionNumber() ?: IntelliJPluginConstants.DEFAULT_IDEA_VERSION
                        if (extension.localPath.orNull.isNullOrEmpty() && version.endsWith("-SNAPSHOT")) {
                            when (extension.getVersionType()) {
                                "CL" -> "CLION-$version"
                                "RD" -> "RIDER-$version"
                                "PY" -> "PYCHARM-$version"
                                else -> version
                            }
                        } else {
                            IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber).asStringWithoutProductCode()
                        }
                    })
                    it.ideaDependency.convention(project.provider {
                        extension.getIdeaDependency(project)
                    })
                    it.javac2.convention(project.layout.file(project.provider {
                        project.file("${extension.getIdeaDependency(project).classes}/lib/javac2.jar").takeIf(File::exists)
                    }))

                    val classesDir = sourceSet.output.classesDirs.first()
                    val outputDir = File(classesDir.parentFile, "${sourceSet.name}-instrumented")
                    it.outputDir.convention(project.layout.projectDirectory.dir(outputDir.path))

                    it.dependsOn(sourceSet.classesTaskName)
                    it.onlyIf { instrumentCode.get() }
                }

            // A dedicated task ensures that sources substitution is always run,
            // even when the instrumentCode task is up-to-date.
            val updateTask = project.tasks.register("post${instrumentTask.name.capitalize()}") {
                val classesDirs = sourceSet.output.classesDirs as ConfigurableFileCollection
                val outputDir = instrumentTask.get().outputDir

                it.dependsOn(instrumentTask)
                it.onlyIf { instrumentCode.get() }
                // Set the classes dir to the one with the instrumented classes
                it.doLast { classesDirs.setFrom(outputDir) }
            }

            // Ensure that our task is invoked when the source set is built
            sourceSet.compiledBy(updateTask)
        }
    }

    private fun configureTestTasks(project: Project, extension: IntelliJPluginExtension) {
        info(context, "Configuring tests tasks")
        val testTasks = project.tasks.withType(Test::class.java) as TaskCollection
        val prepareTestingSandboxTaskProvider = project.tasks.named(IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME) as TaskProvider<PrepareSandboxTask>
        val runIdeTaskProvider = project.tasks.named(IntelliJPluginConstants.RUN_IDE_TASK_NAME)
        val runIdeTask = runIdeTaskProvider.get() as RunIdeTask

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
                .files(prepareTestingSandboxTaskProvider.map { it.destinationDir })
                .withPropertyName("Plugins directory")
                .withPathSensitivity(PathSensitivity.RELATIVE)
                .withNormalizer(ClasspathNormalizer::class.java)

            val ideaDependency = extension.getIdeaDependency(project)
            val ideaDependencyLibraries = project.files(
                "${ideaDependency.classes}/lib/resources.jar",
                "${ideaDependency.classes}/lib/idea.jar"
            )
            val ideDirectory = project.provider { runIdeTask.ideDir.get().asFile }

            // Use an anonymous class, since lambdas disable caching for the task.
            @Suppress("ObjectLiteralToLambda")
            task.doFirst(object : Action<Task> {
                override fun execute(t: Task) {
                    task.jvmArgs = getIdeJvmArgs(task, task.jvmArgs ?: emptyList(), ideDirectory.get())
                    task.classpath += ideaDependencyLibraries

                    task.systemProperties(getIdeaSystemProperties(configDirectory, systemDirectory, pluginsDirectory, pluginIds))

                    // since 193 plugins from classpath are loaded before plugins from plugins directory
                    // to handle this, use plugin.path property as task's the very first source of plugins
                    // we cannot do this for IDEA < 193, as plugins from plugin.path can be loaded twice
                    val ideVersion = IdeVersion.createIdeVersion(ideaDependency.buildNumber)
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
        val prepareSandboxTaskProvider = project.tasks.named(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        val prepareSandboxTask = prepareSandboxTaskProvider.get() as PrepareSandboxTask
        val jarSearchableOptionsTaskProvider = project.tasks.named(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)
        val jarSearchableOptionsTask = jarSearchableOptionsTaskProvider.get() as JarSearchableOptionsTask

        project.tasks.register(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME, Zip::class.java) {
            it.description = "Bundles the project as a distribution."
            it.group = IntelliJPluginConstants.GROUP_NAME

            it.archiveBaseName.convention(project.provider {
                prepareSandboxTask.pluginName.get()
            })

            it.from(project.provider {
                "${prepareSandboxTask.destinationDir}/${prepareSandboxTask.pluginName.get()}"
            })
            it.into(project.provider {
                prepareSandboxTask.pluginName.get()
            })
            it.from(jarSearchableOptionsTask.archiveFile) { copy -> copy.into("lib") }
            it.dependsOn(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)

            val archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
            ArchivePublishArtifact(it).let { zipArtifact ->
                archivesConfiguration.artifacts.add(zipArtifact)
                project.extensions.getByType(DefaultArtifactPublicationSet::class.java).addCandidate(zipArtifact)
                project.components.add(IntelliJPluginLibrary())
            }
        }
    }

    private fun configureSignPluginTask(project: Project) {
        info(context, "Configuring sign plugin task")

        project.tasks.register(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME, SignPluginTask::class.java) {

            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Sign plugin with your private key and certificate chain."

            it.inputArchiveFile.convention(project.layout.file(project.provider {
                resolveBuildTaskOutput(project)
            }))
            it.outputArchiveFile.convention(project.layout.file(project.provider {
                val buildPluginTaskProvider = project.tasks.named(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
                val buildPluginTask = buildPluginTaskProvider.get() as Zip
                val inputFile = buildPluginTask.archiveFile.get().asFile
                val inputFileExtension = inputFile.path.substring(inputFile.path.lastIndexOf('.'))
                val inputFileWithoutExtension = inputFile.path.substring(0, inputFile.path.lastIndexOf('.'))
                val outputFilePath = "$inputFileWithoutExtension-signed$inputFileExtension"
                File(outputFilePath)
            }))

            it.onlyIf { _ ->
                it as SignPluginTask
                it.privateKey.isPresent && it.certificateChain.isPresent
            }
            it.dependsOn(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        }
    }

    private fun configurePublishPluginTask(project: Project) {
        info(context, "Configuring publish plugin task")
        val buildPluginTaskProvider = project.tasks.named(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        val verifyPluginTaskProvider = project.tasks.named(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        val signPluginTaskProvider = project.tasks.named(IntelliJPluginConstants.SIGN_PLUGIN_TASK_NAME)

        project.tasks.register(IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME, PublishPluginTask::class.java) {
            it.group = IntelliJPluginConstants.GROUP_NAME
            it.description = "Publish plugin distribution on plugins.jetbrains.com."

            it.host.convention("https://plugins.jetbrains.com")
            it.channels.convention(listOf("default"))
            it.distributionFile.convention(project.layout.file(project.provider {
                signPluginTaskProvider.get().let { signPluginTask ->
                    signPluginTask as SignPluginTask
                    signPluginTask.outputArchiveFile.orNull?.asFile.takeIf { signPluginTask.didWork } ?: resolveBuildTaskOutput(project)
                }
            }))

            it.dependsOn(buildPluginTaskProvider)
            it.dependsOn(verifyPluginTaskProvider)
            it.dependsOn(signPluginTaskProvider)
        }
    }

    private fun configureProcessResources(project: Project) {
        info(context, "Configuring resources task")
        val patchPluginXmlTaskProvider = project.tasks.named(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME)

        project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) { processResourcesTask ->
            processResourcesTask as ProcessResources
            processResourcesTask.from(patchPluginXmlTaskProvider) { copy ->
                copy.duplicatesStrategy = DuplicatesStrategy.INCLUDE
                copy.into("META-INF")
            }
        }
    }

    private fun resolveBuildTaskOutput(project: Project): File? {
        val buildPluginTaskProvider = project.tasks.named(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        val buildPluginTask = buildPluginTaskProvider.get() as Zip
        return buildPluginTask.archiveFile.orNull?.asFile?.takeIf { it.exists() }
    }
}
