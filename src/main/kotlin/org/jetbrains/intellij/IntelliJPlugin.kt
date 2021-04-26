package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
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
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.gradle.util.VersionNumber
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginDependencyManager
import org.jetbrains.intellij.dependency.PluginDependencyNotation
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.jbr.JbrResolver
import org.jetbrains.intellij.model.IdeaPlugin
import org.jetbrains.intellij.tasks.BuildSearchableOptionsTask
import org.jetbrains.intellij.tasks.DownloadRobotServerPluginTask
import org.jetbrains.intellij.tasks.IntelliJInstrumentCodeTask
import org.jetbrains.intellij.tasks.JarSearchableOptionsTask
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.intellij.tasks.RunIdeBase
import org.jetbrains.intellij.tasks.RunIdeForUiTestTask
import org.jetbrains.intellij.tasks.RunIdeTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask
import org.jetbrains.intellij.tasks.RunPluginVerifierTask.Companion.VERIFIER_VERSION_LATEST
import org.jetbrains.intellij.tasks.VerifyPluginTask
import java.io.File
import java.util.EnumSet

@Suppress("UnstableApiUsage")
open class IntelliJPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        checkGradleVersion(project)
        project.plugins.apply(JavaPlugin::class.java)
        val intellijExtension = project.extensions.create(IntelliJPluginConstants.EXTENSION_NAME,
            IntelliJPluginExtension::class.java,
            project.objects) as IntelliJPluginExtension
        intellijExtension.apply {
            pluginName.convention(project.provider {
                project.name
            })
            updateSinceUntilBuild.convention(true)
            sameSinceUntilBuild.convention(false)
            instrumentCode.convention(true)
            sandboxDirectory.convention(project.provider {
                File(project.buildDir, IntelliJPluginConstants.DEFAULT_SANDBOX).absolutePath
            })
            intellijRepo.convention(IntelliJPluginConstants.DEFAULT_INTELLIJ_REPO)
            downloadSources.convention(!System.getenv().containsKey("CI"))
            configureDefaultDependencies.convention(true)
            type.convention("IC")
        }
        configureConfigurations(project, intellijExtension)
        configureTasks(project, intellijExtension)
    }

    private fun checkGradleVersion(project: Project) {
        if (VersionNumber.parse(project.gradle.gradleVersion) < VersionNumber.parse("5.1")) {
            throw PluginInstantiationException("gradle-intellij-plugin requires Gradle 5.1 and higher")
        }
    }

    private fun configureConfigurations(project: Project, extension: IntelliJPluginExtension) {
        val idea = project.configurations.create(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME).setVisible(false)
        configureIntellijDependency(project, extension, idea)

        val ideaPlugins = project.configurations.create(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME).setVisible(false)
        configurePluginDependencies(project, extension, ideaPlugins)

        val defaultDependencies = project.configurations.create("intellijDefaultDependencies").setVisible(false)
        defaultDependencies.defaultDependencies { dependencies ->
            dependencies.add(project.dependencies.create("org.jetbrains:annotations:19.0.0"))
        }

        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom(defaultDependencies, idea, ideaPlugins)
        project.configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME)
            .extendsFrom(defaultDependencies, idea, ideaPlugins)
    }

    private fun configureTasks(project: Project, extension: IntelliJPluginExtension) {
        info(project, "Configuring plugin")
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
            info(project, "Configuring IDE dependency")
            val resolver = IdeaDependencyManager(extension.intellijRepo.get(), extension.ideaDependencyCachePath.orNull)
            val localPath = extension.localPath.orNull
            val ideaDependency = if (localPath != null) {
                if (extension.version.orNull != null) {
                    warn(project, "Both `localPath` and `version` specified, second would be ignored")
                }
                info(project, "Using path to locally installed IDE: '$localPath'")
                resolver.resolveLocal(project, localPath, extension.localSourcesPath.orNull)
            } else {
                info(project, "Using IDE from remote repository")
                val version = extension.getVersionNumber() ?: IntelliJPluginConstants.DEFAULT_IDEA_VERSION
                val extraDependencies = extension.extraDependencies.get()
                resolver.resolveRemote(project, version, extension.getVersionType(), extension.downloadSources.get(), extraDependencies)
            }
            extension.ideaDependency.set(ideaDependency)
            if (extension.configureDefaultDependencies.get()) {
                info(project, "$ideaDependency.buildNumber is used for building")
                resolver.register(project, ideaDependency, dependencies)
                if (!ideaDependency.extraDependencies.isEmpty()) {
                    info(project,
                        "Note: $ideaDependency.buildNumber extra dependencies (${ideaDependency.extraDependencies}) should be applied manually")
                }
            } else {
                info(project, "IDE ${ideaDependency.buildNumber} dependencies are applied manually")
            }
        }
        Jvm.current().toolsJar?.let { toolsJar ->
            project.dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, project.files(toolsJar))
        }
    }

    private fun configurePluginDependencies(project: Project, extension: IntelliJPluginExtension, configuration: Configuration) {
        configuration.withDependencies { dependencies ->
            info(project, "Configuring plugin dependencies")
            val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
            val resolver = PluginDependencyManager(
                project.gradle.gradleUserHomeDir.absolutePath,
                extension.getIdeaDependency(project),
                extension.getPluginsRepos()
            )
            extension.plugins.get().forEach {
                info(project, "Configuring plugin $it")
                if (it is Project) {
                    configureProjectPluginDependency(project, it, dependencies, extension)
                } else {
                    val pluginDependency = PluginDependencyNotation.parsePluginDependencyString(it.toString())
                    // @TODO: check if needed
//                    if (pluginDependency.id == null) {
//                        throw BuildException("Failed to resolve plugin $it", null)
//                    }
                    val plugin = resolver.resolve(project, pluginDependency) ?: throw BuildException("Failed to resolve plugin $it", null)
                    if (!plugin.isCompatible(ideVersion)) {
                        throw BuildException("Plugin $it is not compatible to ${ideVersion.asString()}", null)
                    }
                    configurePluginDependency(project, plugin, extension, dependencies, resolver)
                }
            }
            if (extension.configureDefaultDependencies.get()) {
                configureBuiltinPluginsDependencies(project, dependencies, resolver, extension)
            }
            verifyJavaPluginDependency(extension, project)
            extension.getPluginsRepos().forEach {
                it.postResolve(project)
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
                val pluginXml = parseXml(file, IdeaPlugin::class.java)
                pluginXml.depends.forEach {
                    if (it.value == "com.intellij.modules.java") {
                        throw BuildException("The project depends on `com.intellij.modules.java` module but doesn't declare a compile dependency on it.\n" +
                            "Please delete `depends` tag from $file.absolutePath or add `java` plugin to Gradle dependencies (e.g. intellij { plugins = ['java'] })",
                            null)
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
        project.tasks.withType(PrepareSandboxTask::class.java).forEach {
            it.configureExternalPlugin(plugin)
        }
    }

    private fun configureProjectPluginTasksDependency(project: Project, dependency: Project) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPlugin::class.java) == null) {
            throw BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        val dependencySandboxTask = dependency.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        project.tasks.withType(PrepareSandboxTask::class.java).forEach {
            it.dependsOn(dependencySandboxTask)
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
            throw BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        dependencies.add(project.dependencies.create(dependency))
        val pluginDependency = PluginProjectDependency(dependency)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType(PrepareSandboxTask::class.java).forEach {
            it.configureCompositePlugin(pluginDependency)
        }
    }

    private fun configurePatchPluginXmlTask(project: Project, extension: IntelliJPluginExtension) {
        info(project, "Configuring patch plugin.xml task")
        val patchPluginXmlTask = project.tasks.create(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask::class.java)

        patchPluginXmlTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"

            task.version.convention(project.provider {
                project.version.toString()
            })
            task.pluginXmlFiles.convention(project.provider {
                sourcePluginXmlFiles(project)
            })
            task.destinationDir.convention(project.layout.dir(project.provider {
                File(project.buildDir, IntelliJPluginConstants.PLUGIN_XML_DIR_NAME)
            }))
            task.sinceBuild.convention(project.provider {
                if (extension.updateSinceUntilBuild.get()) {
                    val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
                    "${ideVersion.baselineVersion}.${ideVersion.build}"
                } else {
                    null
                }
            })
            task.untilBuild.convention(project.provider {
                if (extension.updateSinceUntilBuild.get()) {
                    if (extension.sameSinceUntilBuild.get()) {
                        "${task.sinceBuild.get()}.*"
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
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, "")
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME, "-test")
        val prepareUiTestingSandboxTask = configurePrepareSandboxTask(project,
            extension,
            IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME,
            "-uiTest") as PrepareSandboxTask
        val downloadPluginTask =
            project.tasks.getByName(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME) as DownloadRobotServerPluginTask

        prepareUiTestingSandboxTask.also { task ->
            task.from(downloadPluginTask.outputDir)
            task.dependsOn(downloadPluginTask)
        }
    }

    private fun configureRobotServerDownloadTask(project: Project) {
        info(project, "Configuring robot-server download Task")

        val downloadRobotServerPluginTask =
            project.tasks.create(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME, DownloadRobotServerPluginTask::class.java)

        downloadRobotServerPluginTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Download robot-server plugin."
        }
    }

    private fun configurePrepareSandboxTask(project: Project, extension: IntelliJPluginExtension, taskName: String, testSuffix: String): Task {
        info(project, "Configuring $taskName task")
        val prepareSandboxTask = project.tasks.create(taskName, PrepareSandboxTask::class.java)

        return prepareSandboxTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Prepare sandbox directory with installed plugin and its dependencies."

            task.pluginName.convention(project.provider {
                extension.pluginName.get()
            })
            task.pluginJar.convention(project.layout.file(project.provider {
                (project.tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).archiveFile.orNull?.asFile
            }))
            task.conventionMapping("destinationDir") {
                project.file("${extension.sandboxDirectory.get()}/plugins$testSuffix")
            }
            task.configDirectory.convention(project.provider {
                "${extension.sandboxDirectory.get()}/config$testSuffix"
            })
            task.librariesToIgnore.convention(project.provider {
                project.files(extension.getIdeaDependency(project).jarFiles)
            })
            task.pluginDependencies.convention(project.provider {
                extension.getPluginDependenciesList(project)
            })

            task.dependsOn(JavaPlugin.JAR_TASK_NAME)
        }
    }

    private fun configureRunPluginVerifierTask(project: Project) {
        info(project, "Configuring run plugin verifier task")
        val runPluginVerifierTask =
            project.tasks.create(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, RunPluginVerifierTask::class.java)

        runPluginVerifierTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description =
                "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds."

            task.failureLevel.convention(
                EnumSet.of(RunPluginVerifierTask.FailureLevel.INVALID_PLUGIN)
            )
            task.verifierVersion.convention(VERIFIER_VERSION_LATEST)
            task.distributionFile.convention(project.layout.file(project.provider {
                resolveDistributionFile(project)
            }))
            task.verificationReportsDirectory.convention(project.provider {
                "${project.buildDir}/reports/pluginVerifier"
            })
            task.downloadDirectory.convention(project.provider {
                task.ideDownloadDirectory().toString()
            })
            task.teamCityOutputFormat.convention(false)

            task.dependsOn(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
            task.dependsOn(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)
        }
    }

    private fun configurePluginVerificationTask(project: Project) {
        info(project, "Configuring plugin verification task")
        val verifyPluginTask = project.tasks.create(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, VerifyPluginTask::class.java)

        verifyPluginTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Validates completeness and contents of plugin.xml descriptors as well as plugin’s archive structure."

            task.pluginDirectory.convention(project.provider {
                val prepareSandboxTask = project.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
                val path = File(prepareSandboxTask.destinationDir, prepareSandboxTask.pluginName.get()).path
                project.layout.projectDirectory.dir(path)
            })

            task.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private fun configureRunIdeaTask(project: Project) {
        info(project, "Configuring run IDE task")
        val runIdeTask = project.tasks.create(IntelliJPluginConstants.RUN_IDE_TASK_NAME, RunIdeTask::class.java)

        runIdeTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Runs Intellij IDEA with installed plugin."
            task.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private fun configureRunIdeaForUiTestsTask(project: Project) {
        info(project, "Configuring run IDE for ui tests task")
        val runIdeForUiTestsTaskName =
            project.tasks.create(IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME, RunIdeForUiTestTask::class.java)

        runIdeForUiTestsTaskName.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Runs Intellij IDEA with installed plugin and robot-server plugin for ui tests."
            task.dependsOn(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
        }
    }

    private fun configureBuildSearchableOptionsTask(project: Project) {
        info(project, "Configuring build searchable options task")
        val buildSearchableOptionsTask =
            project.tasks.create(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, BuildSearchableOptionsTask::class.java)

        buildSearchableOptionsTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Builds searchable options for plugin."
            task.args = listOf("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}", "true")
            task.outputs.dir("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}")
            task.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            task.onlyIf {
                val number = ideBuildNumber(task.ideDirectory.get().asFile)
                VersionNumber.parse(number.split('-').last()) >= VersionNumber.parse("191.2752")
            }
        }
    }

    private fun prepareConventionMappingsForRunIdeTask(
        project: Project,
        extension: IntelliJPluginExtension,
        task: RunIdeBase,
        prepareSandBoxTaskName: String,
    ) {
        val prepareSandboxTask = project.tasks.findByName(prepareSandBoxTaskName) as PrepareSandboxTask

        task.ideDirectory.convention(project.provider {
            val path = extension.getIdeaDependency(project).classes.path
            project.layout.projectDirectory.dir(path)
        })
        task.requiredPluginIds.convention(project.provider {
            getPluginIds(project)
        })
        task.configDirectory.convention(project.provider {
            project.file(prepareSandboxTask.configDirectory.get())
        })
        task.pluginsDirectory.convention(project.provider {
            val path = prepareSandboxTask.destinationDir.path
            project.layout.projectDirectory.dir(path)
        })
        task.systemDirectory.convention(project.provider {
            project.file("${extension.sandboxDirectory.get()}/system")
        })
        task.autoReloadPlugins.convention(project.provider {
            val number = ideBuildNumber(task.ideDirectory.get().asFile)
            VersionNumber.parse(number.split('-').last()) >= VersionNumber.parse("202.0")
        })
        task.conventionMapping("executable") {
            val jbrResolver = JbrResolver(project, task, extension.jreRepo.orNull)

            task.jbrVersion.orNull?.let {
                jbrResolver.resolve(it)?.javaExecutable ?: null.apply {
                    warn(task, "Cannot resolve JBR $it. Falling back to builtin JBR.")
                }
            } ?: getBuiltinJbrVersion(task.ideDirectory.get().asFile)?.let {
                jbrResolver.resolve(it)?.javaExecutable ?: null.apply {
                    warn(task, "Cannot resolve builtin JBR $it. Falling local Java.")
                }
            } ?: Jvm.current().javaExecutable.absolutePath
        }
    }

    fun configureJarSearchableOptionsTask(project: Project) {
        info(project, "Configuring jar searchable options task")
        val jarSearchableOptionsTask =
            project.tasks.create(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME, JarSearchableOptionsTask::class.java)

        jarSearchableOptionsTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Jars searchable options."

            task.archiveBaseName.convention("lib/searchableOptions")
            task.destinationDirectory.convention(project.layout.buildDirectory.dir("libsSearchableOptions"))

            task.dependsOn(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
            task.onlyIf { File(project.buildDir, IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME).isDirectory }
        }
    }

    fun configureInstrumentation(project: Project, extension: IntelliJPluginExtension) {
        info(project, "Configuring compile tasks")
        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer

        sourceSets.forEach { sourceSet ->
            val instrumentTask = project.tasks.create(sourceSet.getTaskName("instrument", "code"), IntelliJInstrumentCodeTask::class.java)

            instrumentTask.also { task ->
                task.sourceSet.convention(sourceSet)
                task.compilerVersion.convention(project.provider {
                    val version = extension.getVersionNumber() ?: IntelliJPluginConstants.DEFAULT_IDEA_VERSION
                    if (extension.localPath.orNull.isNullOrEmpty() && version.isEmpty() && version.endsWith("-SNAPSHOT")) {
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
                task.ideaDependency.convention(project.provider {
                    extension.getIdeaDependency(project)
                })
                task.javac2.convention(project.layout.file(project.provider {
                    val javac2 = project.file("${extension.getIdeaDependency(project).classes}/lib/javac2.jar")
                    javac2.takeIf { it.exists() }
                }))

                val classesDir = sourceSet.output.classesDirs.first()
                val outputDir = File(classesDir.parentFile, "${sourceSet.name}-instrumented")
                task.outputDir.convention(project.layout.projectDirectory.dir(outputDir.path))

                task.dependsOn(sourceSet.classesTaskName)
                task.onlyIf { extension.instrumentCode.get() }
            }

            // A dedicated task ensures that sources substitution is always run,
            // even when the instrumentCode task is up-to-date.
            val updateTask = project.tasks.create("post${instrumentTask.name.capitalize()}")
            updateTask.also { task ->
                task.dependsOn(instrumentTask)
                task.onlyIf { extension.instrumentCode.get() }
                task.doLast {
                    // Set the classes dir to the one with the instrumented classes
                    (sourceSet.output.classesDirs as ConfigurableFileCollection).from(instrumentTask.outputDir)
                }
            }

            // Ensure that our task is invoked when the source set is built
            sourceSet.compiledBy(updateTask)
        }
    }

    private fun configureTestTasks(project: Project, extension: IntelliJPluginExtension) {
        info(project, "Configuring tests tasks")
        val testTasks = project.tasks.withType(Test::class.java) as TaskCollection
        val prepareTestingSandboxTask =
            project.tasks.findByName(IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME) as PrepareSandboxTask
        val runIdeTask = project.tasks.findByName(IntelliJPluginConstants.RUN_IDE_TASK_NAME) as RunIdeTask

        val pluginIds = getPluginIds(project)
        val configDirectory = project.file("${extension.sandboxDirectory.get()}/config-test")
        val systemDirectory = project.file("${extension.sandboxDirectory.get()}/system-test")
        val pluginsDirectory = project.file("${extension.sandboxDirectory.get()}/plugins-test")

        testTasks.forEach { task ->
            task.enableAssertions = true
            task.systemProperties(getIdeaSystemProperties(configDirectory, systemDirectory, pluginsDirectory, pluginIds))

            // appClassLoader should be used for user's plugins. Otherwise, classes it won't be possible to use
            // its classes of application components or services in tests: class loaders will be different for
            // classes references by test code and for classes loaded by the platform (pico container).
            //
            // The proper way to handle that is to substitute Gradle's test class-loader and teach it
            // to understand PluginClassLoaders. Unfortunately, I couldn't find a way to do that.
            task.systemProperty("idea.use.core.classloader.for.plugin.path", "true")
            // the same as previous – setting appClassLoader but outdated. Works for part of 203 builds.
            task.systemProperty("idea.use.core.classloader.for", pluginIds.joinToString(","))

            task.outputs.dir(systemDirectory)
            task.outputs.dir(configDirectory)
            task.inputs.files(prepareTestingSandboxTask)

            task.doFirst {
                val ideDirectory = runIdeTask.ideDirectory.get().asFile
                task.jvmArgs = getIdeJvmArgs(task, task.jvmArgs ?: emptyList(), ideDirectory)
                task.classpath += project.files(
                    "${extension.getIdeaDependency(project).classes}/lib/resources.jar",
                    "${extension.getIdeaDependency(project).classes}/lib/idea.jar"
                )

                // since 193 plugins from classpath are loaded before plugins from plugins directory
                // to handle this, use plugin.path property as task's the very first source of plugins
                // we cannot do this for IDEA < 193, as plugins from plugin.path can be loaded twice
                val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
                if (ideVersion.baselineVersion >= 193) {
                    task.systemProperty(IntelliJPluginConstants.PLUGIN_PATH,
                        pluginsDirectory.listFiles().joinToString("${File.pathSeparator},") { it.path })
                }
            }
        }
    }

    private fun configureBuildPluginTask(project: Project) {
        info(project, "Configuring building plugin task")
        val buildPluginTask = project.tasks.create(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME, Zip::class.java)
        val prepareSandboxTask = project.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
        val jarSearchableOptionsTask =
            project.tasks.findByName(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME) as JarSearchableOptionsTask

        buildPluginTask.let { task ->
            task.description = "Bundles the project as a distribution."
            task.group = IntelliJPluginConstants.GROUP_NAME

            task.archiveBaseName.convention(project.provider {
                prepareSandboxTask.pluginName.get()
            })

            task.from(project.provider {
                "${prepareSandboxTask.destinationDir}/${prepareSandboxTask.pluginName.get()}"
            })
            task.into(project.provider {
                prepareSandboxTask.pluginName.get()
            })
            task.from(jarSearchableOptionsTask.archiveFile) { copy -> copy.into("lib") }
            task.dependsOn(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)
        }

        val archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        val zipArtifact = ArchivePublishArtifact(buildPluginTask)
        archivesConfiguration.artifacts.add(zipArtifact)
        project.extensions.getByType(DefaultArtifactPublicationSet::class.java).addCandidate(zipArtifact)
        project.components.add(IntelliJPluginLibrary())
    }

    private fun configurePublishPluginTask(project: Project) {
        info(project, "Configuring publish plugin task")
        val publishPluginTask = project.tasks.create(IntelliJPluginConstants.PUBLISH_PLUGIN_TASK_NAME, PublishTask::class.java)
        val buildPluginTask = project.tasks.findByName(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME)
        val verifyPluginTask = project.tasks.findByName(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME)

        publishPluginTask.also { task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Publish plugin distribution on plugins.jetbrains.com."

            task.distributionFile.convention(
                project.layout.file(project.provider {
                    resolveDistributionFile(project)
                })
            )

            task.dependsOn(buildPluginTask)
            task.dependsOn(verifyPluginTask)
        }
    }

    private fun configureProcessResources(project: Project) {
        info(project, "Configuring resources task")
        val processResourcesTask = project.tasks.findByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) as? ProcessResources
        val patchPluginXmlTask = project.tasks.findByName(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME) as PatchPluginXmlTask

        processResourcesTask?.also { task ->
            task.from(patchPluginXmlTask) {
                it.into("META-INF")
                it.duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        }
    }

    private fun resolveDistributionFile(project: Project): File? {
        val buildPluginTask = project.tasks.findByName(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME) as Zip
        return buildPluginTask.archiveFile.orNull?.asFile?.takeIf { it.exists() }
    }
}
