package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskCollection
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.language.jvm.tasks.ProcessResources
import org.jetbrains.intellij.tasks.IntelliJInstrumentCodeTask
import org.jetbrains.intellij.tasks.JarSearchableOptionsTask
import org.jetbrains.intellij.tasks.PatchPluginXmlTask
import org.jetbrains.intellij.tasks.PrepareSandboxTask
import org.jetbrains.intellij.tasks.PublishTask
import org.jetbrains.intellij.tasks.RunIdeTask
import java.io.File

@Suppress("UnstableApiUsage")
abstract class IntelliJPlugin : Plugin<Project> {

//    @Override
//    void apply(Project project) {
//        checkGradleVersion(project)
//        project.getPlugins().apply(JavaPlugin)
//        val intellijExtension = project.extensions.create(IntelliJPluginConstants.EXTENSION_NAME, IntelliJPluginExtension, project.objects) as IntelliJPluginExtension
//        intellijExtension.with {
//            pluginName.convention(project.provider {
//                project.name
//            })
//            updateSinceUntilBuild.convention(true)
//            sameSinceUntilBuild.convention(false)
//            instrumentCode.convention(true)
//            sandboxDirectory.convention(project.provider({
//                File(project.buildDir, IntelliJPluginConstants.DEFAULT_SANDBOX).absolutePath
//            }))
//            intellijRepo.convention(IntelliJPluginConstants.DEFAULT_INTELLIJ_REPO)
//            downloadSources.convention(!System.getenv().containsKey("CI"))
//            configureDefaultDependencies.convention(true)
//            type.convention("IC")
//        }
//        configureConfigurations(project, intellijExtension)
//        configureTasks(project, intellijExtension)
//    }
//
//    fun void checkGradleVersion(@NotNull Project project) {
//        if (VersionNumber.parse(project.gradle.gradleVersion) < VersionNumber.parse("5.1")) {
//            throw PluginInstantiationException("gradle-intellij-plugin requires Gradle 5.1 and higher")
//        }
//    }
//
//    fun void configureConfigurations(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
//        val idea = project.configurations.create(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME).setVisible(false)
//        configureIntellijDependency(project, extension, idea)
//
//        val ideaPlugins = project.configurations.create(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME).setVisible(false)
//        configurePluginDependencies(project, extension, ideaPlugins)
//
//        val defaultDependencies = project.configurations.create("intellijDefaultDependencies").setVisible(false)
//        defaultDependencies.defaultDependencies { dependencies ->
//            dependencies.add(project.dependencies.create('org.jetbrains:annotations:19.0.0'))
//        }
//
//        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom defaultDependencies, idea, ideaPlugins
//        project.configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom defaultDependencies, idea, ideaPlugins
//    }
//
//    fun val configureTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
//        info(project, "Configuring plugin")
//        project.tasks.whenTaskAdded {
//            if (it instanceof RunIdeBase) {
//                prepareConventionMappingsForRunIdeTask(project, extension, it, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
//            }
//            if (it instanceof RunIdeForUiTestTask) {
//                prepareConventionMappingsForRunIdeTask(project, extension, it, IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
//            }
//        }
//        configurePatchPluginXmlTask(project, extension)
//        configureRobotServerDownloadTask(project)
//        configurePrepareSandboxTasks(project, extension)
//        configureRunPluginVerifierTask(project)
//        configurePluginVerificationTask(project)
//        configureRunIdeaTask(project)
//        configureRunIdeaForUiTestsTask(project)
//        configureBuildSearchableOptionsTask(project, extension)
//        configureJarSearchableOptionsTask(project)
//        configureBuildPluginTask(project)
//        configurePublishPluginTask(project)
//        configureProcessResources(project)
//        configureInstrumentation(project, extension)
//        configureDependencyExtensions(project, extension)
//        assert !project.state.executed: "afterEvaluate is a no-op for an executed project"
//        project.afterEvaluate { Project p -> configureProjectAfterEvaluate(p, extension) }
//    }
//
//    fun void configureProjectAfterEvaluate(@NotNull Project project,
//    @NotNull IntelliJPluginExtension extension) {
//        for (val subproject : project.subprojects) {
//            if (subproject.plugins.findPlugin(IntelliJPluginGr) != null) {
//                continue
//            }
//            val subprojectExtension = subproject.extensions.findByType(IntelliJPluginExtension)
//            if (subprojectExtension) {
//                configureProjectAfterEvaluate(subproject, subprojectExtension)
//            }
//        }
//
//        configureTestTasks(project, extension)
//    }
//
//    fun void configureDependencyExtensions(@NotNull Project project,
//    @NotNull IntelliJPluginExtension extension) {
//        project.dependencies.ext.intellij = { Closure filter = {} ->
//            if (!project.state.executed) {
//                throw GradleException('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
//            }
//            project.files(extension.getIdeaDependency(project).jarFiles).asFileTree.matching(filter)
//        }
//
//        project.dependencies.ext.intellijPlugin = { String plugin, Closure filter = {} ->
//            if (!project.state.executed) {
//                throw GradleException("intellij plugin '$plugin' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
//            }
//            val pluginDep = extension.getPluginDependenciesList(project).find { it.id == plugin }
//            if (pluginDep == null || pluginDep.jarFiles == null || pluginDep.jarFiles.empty) {
//                throw GradleException("intellij plugin '$plugin' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
//            }
//            project.files(pluginDep.jarFiles).asFileTree.matching(filter)
//        }
//
//        project.dependencies.ext.intellijPlugins = { String... plugins ->
//            val selectedPlugins = HashSet<PluginDependency>()
//            val nonValidPlugins = []
//            for (pluginName in plugins) {
//                val plugin = extension.getPluginDependenciesList(project).find { it.id == pluginName }
//                if (plugin == null || plugin.jarFiles == null || plugin.jarFiles.empty) {
//                    nonValidPlugins.add(pluginName)
//                } else {
//                    selectedPlugins.add(plugin)
//                }
//            }
//            if (!nonValidPlugins.empty) {
//                throw GradleException("intellij plugins $nonValidPlugins are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
//            }
//            project.files(selectedPlugins.collect { it.jarFiles })
//        }
//
//        project.dependencies.ext.intellijExtra = { String extra, Closure filter = {} ->
//            if (!project.state.executed) {
//                throw GradleException('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
//            }
//            val dependency = extension.getIdeaDependency(project)
//            val extraDep = dependency != null ? dependency.extraDependencies.find { it.name == extra } : null
//            if (extraDep == null || extraDep.jarFiles == null || extraDep.jarFiles.empty) {
//                throw GradleException("intellij extra artifact '$extra' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
//            }
//            project.files(extraDep.jarFiles).asFileTree.matching(filter)
//        }
//    }
//
//    fun void configureIntellijDependency(@NotNull Project project,
//    @NotNull IntelliJPluginExtension extension,
//    @NotNull Configuration configuration) {
//        configuration.withDependencies { dependencies ->
//            info(project, "Configuring IDE dependency")
//            val resolver = IdeaDependencyManager(extension.intellijRepo.get(), extension.ideaDependencyCachePath.orNull)
//            val ideaDependency
//                val localPath = extension.localPath.orNull
//                if (localPath != null) {
//                    if (extension.version.orNull != null) {
//                        warn(project, "Both `localPath` and `version` specified, second would be ignored")
//                    }
//                    info(project, "Using path to locally installed IDE: '$localPath'")
//                    ideaDependency = resolver.resolveLocal(project, localPath, extension.localSourcesPath.orNull)
//                } else {
//                    info(project, "Using IDE from remote repository")
//                    val version = extension.getVersionNumber() ?: IntelliJPluginConstants.DEFAULT_IDEA_VERSION
//                    val extraDependencies = extension.extraDependencies.get()
//                    ideaDependency = resolver.resolveRemote(project, version, extension.getVersionType(), extension.downloadSources.get(), extraDependencies)
//                }
//            extension.ideaDependency = ideaDependency
//            if (extension.configureDefaultDependencies.get()) {
//                info(project, "$ideaDependency.buildNumber is used for building")
//                resolver.register(project, ideaDependency, dependencies)
//                if (!ideaDependency.extraDependencies.empty) {
//                    info(project, "Note: $ideaDependency.buildNumber extra dependencies (${ideaDependency.extraDependencies}) should be applied manually")
//                }
//            } else {
//                info(project, "IDE ${ideaDependency.buildNumber} dependencies are applied manually")
//            }
//        }
//        val toolsJar = Jvm.current().toolsJar
//        if (toolsJar) {
//            project.dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, project.files(toolsJar))
//        }
//    }
//
//    fun void configurePluginDependencies(@NotNull Project project,
//    @NotNull IntelliJPluginExtension extension,
//    @NotNull Configuration configuration) {
//        configuration.withDependencies { dependencies ->
//            info(project, "Configuring plugin dependencies")
//            val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
//            val resolver = PluginDependencyManager(project.gradle.gradleUserHomeDir.absolutePath, extension.getIdeaDependency(project), extension.pluginsRepos)
//            extension.plugins.get().each {
//                info(project, "Configuring plugin $it")
//                if (it instanceof Project) {
//                    configureProjectPluginDependency(project, it, dependencies, extension)
//                } else {
//                    val pluginDependency = PluginDependencyNotation.parsePluginDependencyString(it.toString())
//                    if (pluginDependency.id == null) {
//                        throw BuildException("Failed to resolve plugin $it", null)
//                    }
//                    val plugin = resolver.resolve(project, pluginDependency)
//                    if (plugin == null) {
//                        throw BuildException("Failed to resolve plugin $it", null)
//                    }
//                    if (ideVersion != null && !plugin.isCompatible(ideVersion)) {
//                        throw BuildException("Plugin $it is not compatible to ${ideVersion.asString()}", null)
//                    }
//                    configurePluginDependency(project, plugin, extension, dependencies, resolver)
//                }
//            }
//            if (extension.configureDefaultDependencies.get()) {
//                configureBuiltinPluginsDependencies(project, dependencies, resolver, extension)
//            }
//            verifyJavaPluginDependency(extension, project)
//            for (PluginsRepository repository : extension.getPluginsRepos()) {
//            repository.postResolve(project)
//        }
//        }
//
//        project.afterEvaluate {
//            extension.plugins.get().findAll { it instanceof Project }.each { Project dependency ->
//                if (dependency.state.executed) {
//                    configureProjectPluginTasksDependency(project, dependency)
//                } else {
//                    dependency.afterEvaluate {
//                        configureProjectPluginTasksDependency(project, dependency)
//                    }
//                }
//            }
//        }
//    }
//
//    fun void verifyJavaPluginDependency(IntelliJPluginExtension extension, Project project) {
//        val plugins = extension.plugins.get()
//        val hasJavaPluginDependency = plugins.contains('java') || plugins.contains('com.intellij.java')
//        if (!hasJavaPluginDependency && File(extension.getIdeaDependency(project).classes, "plugins/java").exists()) {
//            sourcePluginXmlFiles(project).each { file ->
//                val pluginXml = parseXml(file, IdeaPlugin)
//                pluginXml.depends.each {
//                    if (it.value == 'com.intellij.modules.java') {
//                        throw BuildException("The project depends on `com.intellij.modules.java` module but doesn't declare a compile dependency on it.\n" +
//                            "Please delete `depends` tag from $file.absolutePath or add `java` plugin to Gradle dependencies (e.g. intellij { plugins = ['java'] })", null)
//                    }
//                }
//            }
//        }
//    }
//
//    fun void configureBuiltinPluginsDependencies(@NotNull Project project,
//    @NotNull DependencySet dependencies,
//    @NotNull PluginDependencyManager resolver,
//    @NotNull IntelliJPluginExtension extension) {
//        val configuredPlugins = extension.unresolvedPluginDependencies
//            .findAll { it.builtin }
//            .collect { it.id }
//        extension.ideaDependency.get().pluginsRegistry.collectBuiltinDependencies(configuredPlugins).forEach {
//            val plugin = resolver.resolve(project, PluginDependencyNotation(it, null, null))
//            configurePluginDependency(project, plugin, extension, dependencies, resolver)
//        }
//    }
//
//    fun void configurePluginDependency(@NotNull Project project,
//    @NotNull PluginDependency plugin,
//    @NotNull IntelliJPluginExtension extension,
//    @NotNull DependencySet dependencies,
//    @NotNull PluginDependencyManager resolver) {
//        if (extension.configureDefaultDependencies.get()) {
//            resolver.register(project, plugin, dependencies)
//        }
//        extension.addPluginDependency(plugin)
//        project.tasks.withType(PrepareSandboxTask).each {
//            it.configureExternalPlugin(plugin)
//        }
//    }
//
//    fun void configureProjectPluginTasksDependency(@NotNull Project project, @NotNull Project dependency) {
//        // invoke before tasks graph is ready
//        if (dependency.plugins.findPlugin(IntelliJPluginGr) == null) {
//            throw BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
//        }
//        val dependencySandboxTask = dependency.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
//        project.tasks.withType(PrepareSandboxTask).each {
//            it.dependsOn(dependencySandboxTask)
//        }
//    }
//
//    fun void configureProjectPluginDependency(@NotNull Project project,
//    @NotNull Project dependency,
//    @NotNull DependencySet dependencies,
//    @NotNull IntelliJPluginExtension extension) {
//        // invoke on demand, when plugins artifacts are needed
//        if (dependency.plugins.findPlugin(IntelliJPluginGr) == null) {
//            throw BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
//        }
//        dependencies.add(project.dependencies.create(dependency))
//        val pluginDependency = PluginProjectDependency(dependency)
//        extension.addPluginDependency(pluginDependency)
//        project.tasks.withType(PrepareSandboxTask).each {
//            it.configureCompositePlugin(pluginDependency)
//        }
//    }
//
//    fun void configurePatchPluginXmlTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
//        info(project, "Configuring patch plugin.xml task")
//        project.tasks.create(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask).with {
//            group = IntelliJPluginConstants.GROUP_NAME
//            description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"
//            it.version.convention(project.provider({
//                project.version?.toString()
//            }))
//            it.pluginXmlFiles.convention(project.provider({
//                sourcePluginXmlFiles(project)
//            }))
//            it.destinationDir.convention(
//                project.layout.dir(project.provider({
//                    File(project.buildDir, IntelliJPluginConstants.PLUGIN_XML_DIR_NAME)
//                }))
//            )
//            it.sinceBuild.convention(project.provider({
//                if (extension.updateSinceUntilBuild.get()) {
//                    val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
//                    return "$ideVersion.baselineVersion.$ideVersion.build".toString()
//                }
//            }))
//            it.untilBuild.convention(project.provider({
//                if (extension.updateSinceUntilBuild.get()) {
//                    val ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
//                    return extension.sameSinceUntilBuild.get() ? "${sinceBuild.get()}.*".toString()
//                    : "$ideVersion.baselineVersion.*".toString()
//                }
//            }))
//        }
//    }
//
//    fun void configurePrepareSandboxTasks(@NotNull Project project,
//    @NotNull IntelliJPluginExtension extension) {
//        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, "")
//        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME, "-test")
//        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME, "-uiTest").with { task ->
//            val downloadPluginTask = project.tasks.getByName(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME) as DownloadRobotServerPluginTask
//            task.from(downloadPluginTask.outputDir)
//            task.dependsOn(downloadPluginTask)
//        }
//    }
//
//    fun void configureRobotServerDownloadTask(@NotNull Project project) {
//        info(project, "Configuring robot-server download Task")
//
//        project.tasks.create(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME, DownloadRobotServerPluginTask).with { task ->
//            group = IntelliJPluginConstants.GROUP_NAME
//            description = "Download robot-server plugin."
//        }
//    }
//
//    fun Task configurePrepareSandboxTask(@NotNull Project project,
//    @NotNull IntelliJPluginExtension extension,
//    String taskName,
//    String testSuffix) {
//        info(project, "Configuring $taskName task")
//        return project.tasks.create(taskName, PrepareSandboxTask).with { task ->
//            group = IntelliJPluginConstants.GROUP_NAME
//            description = "Prepare sandbox directory with installed plugin and its dependencies."
//            task.pluginName.convention(project.provider({
//                extension.pluginName.get()
//            }))
//            task.pluginJar.convention(
//                project.layout.file(project.provider({
//                    VersionNumber.parse(project.gradle.gradleVersion) >= VersionNumber.parse("5.1")
//                    ? (project.tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).archiveFile.getOrNull().asFile
//                    : (project.tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).archivePath
//                }))
//            )
//            conventionMapping('destinationDir', {
//                project.file("${extension.sandboxDirectory.get()}/plugins$testSuffix")
//            })
//            task.configDirectory.convention(project.provider({
//                "${extension.sandboxDirectory.get()}/config$testSuffix"
//            }))
//            task.librariesToIgnore.convention(project.provider({
//                project.files(extension.getIdeaDependency(project).jarFiles)
//            }))
//            task.pluginDependencies.convention(project.provider({
//                extension.getPluginDependenciesList(project)
//            }))
//            dependsOn(JavaPlugin.JAR_TASK_NAME)
//        }
//    }
//
//    fun void configureRunPluginVerifierTask(@NotNull Project project) {
//        info(project, "Configuring run plugin verifier task")
//        project.tasks.create(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, RunPluginVerifierTask).with {
//            group = IntelliJPluginConstants.GROUP_NAME
//            description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds."
//
//            it.failureLevel.convention(
//                EnumSet.of(RunPluginVerifierTask.FailureLevel.INVALID_PLUGIN)
//            )
//            it.verifierVersion.convention(VERIFIER_VERSION_LATEST)
//            it.distributionFile.convention(
//                project.layout.file(project.provider({
//                    resolveDistributionFile(project)
//                }))
//            )
//            it.verificationReportsDirectory.convention(project.provider({
//                "${project.buildDir}/reports/pluginVerifier".toString()
//            }))
//            it.downloadDirectory.convention(project.provider({
//                ideDownloadDirectory().toString()
//            }))
//            it.teamCityOutputFormat.convention(false)
//
//            dependsOn { project.getTasksByName(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME, false) }
//            dependsOn { project.getTasksByName(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, false) }
//        }
//    }
//
//    fun void configurePluginVerificationTask(@NotNull Project project) {
//        info(project, "Configuring plugin verification task")
//        project.tasks.create(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, VerifyPluginTask).with {
//            group = IntelliJPluginConstants.GROUP_NAME
//            description = "Validates completeness and contents of plugin.xml descriptors as well as plugin’s archive structure."
//
//            it.pluginDirectory.convention(project.provider({
//                val prepareSandboxTask = project.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
//                val path = File(prepareSandboxTask.getDestinationDir(), prepareSandboxTask.getPluginName().get()).path
//                project.layout.projectDirectory.dir(path)
//            }))
//
//            dependsOn { project.getTasksByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, false) }
//        }
//    }
//
//    fun void configureRunIdeaTask(@NotNull Project project) {
//        info(project, "Configuring run IDE task")
//        project.tasks.create(IntelliJPluginConstants.RUN_IDE_TASK_NAME, RunIdeTask).with { RunIdeTask task ->
//            task.group = IntelliJPluginConstants.GROUP_NAME
//            task.description = "Runs Intellij IDEA with installed plugin."
//            task.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
//        }
//    }
//
//    fun void configureRunIdeaForUiTestsTask(@NotNull Project project) {
//        info(project, "Configuring run IDE for ui tests task")
//        project.tasks.create(IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME, RunIdeForUiTestTask).with { RunIdeForUiTestTask task ->
//            task.group = IntelliJPluginConstants.GROUP_NAME
//            task.description = "Runs Intellij IDEA with installed plugin and robot-server plugin for ui tests."
//            task.dependsOn(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
//        }
//    }
//
//    fun void configureBuildSearchableOptionsTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
//        info(project, "Configuring build searchable options task")
//        project.tasks.create(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, BuildSearchableOptionsTask).with { BuildSearchableOptionsTask task ->
//            task.group = IntelliJPluginConstants.GROUP_NAME
//            task.description = "Builds searchable options for plugin."
//            task.setArgs(["${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}", "true"])
//            task.outputs.dir("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}")
//            task.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
//            task.onlyIf {
//                val number = ideBuildNumber(task.ideDirectory.get().asFile)
//                VersionNumber.parse(number[number.indexOf('-') + 1..-1]) >= VersionNumber.parse("191.2752")
//            }
//        }
//    }
//
//    fun prepareConventionMappingsForRunIdeTask(
//        project: Project,
//        extension: IntelliJPluginExtension,
//        task: RunIdeBase,
//        prepareSandBoxTaskName: String,
//    ) {
//        val prepareSandboxTask = project.tasks.findByName(prepareSandBoxTaskName) as PrepareSandboxTask
//
//        task.ideDirectory.convention(project.provider {
//            val path = extension.getIdeaDependency(project).classes.path
//            project.layout.projectDirectory.dir(path)
//        })
//        task.requiredPluginIds.convention(project.provider {
//            getPluginIds(project)
//        })
//        task.configDirectory.convention(project.provider {
//            project.file(prepareSandboxTask.configDirectory.get())
//        })
//        task.pluginsDirectory.convention(project.provider {
//            val path = prepareSandboxTask.destinationDir.path
//            project.layout.projectDirectory.dir(path)
//        })
//        task.systemDirectory.convention(project.provider {
//            project.file("${extension.sandboxDirectory.get()}/system")
//        })
//        task.autoReloadPlugins.convention(project.provider {
//            val number = ideBuildNumber(task.ideDirectory.get().asFile)
//            VersionNumber.parse(number.split('-').last()) >= VersionNumber.parse("202.0")
//        })
//        task.conventionMapping("executable") {
//            val jbrResolver = JbrResolver(project, task, extension.jreRepo.orNull)
//            task.jbrVersion.orNull?.let { jbrVersion ->
//                jbrResolver.resolve(jbrVersion)?.let { jbr ->
//                    return@conventionMapping jbr.javaExecutable
//                }
//                warn(task, "Cannot resolve JBR $jbrVersion. Falling back to builtin JBR.")
//            }
//            getBuiltinJbrVersion(task.ideDirectory.get().asFile)?.let { builtinJbrVersion ->
//                jbrResolver.resolve(builtinJbrVersion)?.let { builtinJbr ->
//                    return@conventionMapping builtinJbr.javaExecutable
//                }
//                warn(task, "Cannot resolve builtin JBR $builtinJbrVersion. Falling local Java.")
//            }
//            Jvm.current().javaExecutable.absolutePath
//        }
//    }
//
//    fun configureJarSearchableOptionsTask(project: Project) {
//        info(project, "Configuring jar searchable options task")
//        val jarSearchableOptionsTask =
//            project.tasks.create(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME, JarSearchableOptionsTask::class.java)
//
//        jarSearchableOptionsTask.also { task ->
//            task.group = IntelliJPluginConstants.GROUP_NAME
//            task.description = "Jars searchable options."
//            task.archiveBaseName.convention("lib/searchableOptions")
//            task.destinationDirectory.convention(project.layout.buildDirectory.dir("libsSearchableOptions"))
//            task.dependsOn(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
//            task.onlyIf { File(project.buildDir, IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME).isDirectory }
//        }
//    }

    fun configureInstrumentation(project: Project, extension: IntelliJPluginExtension) {
        info(project, "Configuring compile tasks")
        val sourceSets = project.extensions.findByName("sourceSets") as SourceSetContainer

        sourceSets.forEach { sourceSet ->
            val instrumentTask = project.tasks.create(sourceSet.getTaskName("instrument", "code"), IntelliJInstrumentCodeTask::class.java)

            instrumentTask.also { task ->
                task.sourceSet.convention(sourceSet)
                task.dependsOn(sourceSet.classesTaskName)
                task.onlyIf { extension.instrumentCode.get() }
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

    fun configureTestTasks(project: Project, extension: IntelliJPluginExtension) {
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

    fun configureBuildPluginTask(project: Project) {
        info(project, "Configuring building plugin task")
        val buildPluginTask = project.tasks.create(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME, Zip::class.java)
        val prepareSandboxTask = project.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
        val jarSearchableOptionsTask =
            project.tasks.findByName(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME) as JarSearchableOptionsTask

        buildPluginTask.let { task ->
            task.description = "Bundles the project as a distribution."
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.from(project.provider {
                "${prepareSandboxTask.destinationDir}/${prepareSandboxTask.pluginName.get()}"
            })
            task.into(project.provider {
                prepareSandboxTask.pluginName.get()
            })
            task.from(jarSearchableOptionsTask.archiveFile) { copy -> copy.into("lib") }
            task.dependsOn(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME)
            task.archiveBaseName.convention(project.provider {
                prepareSandboxTask.pluginName.get()
            })
        }

        val archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        val zipArtifact = ArchivePublishArtifact(buildPluginTask)
        archivesConfiguration.artifacts.add(zipArtifact)
        project.extensions.getByType(DefaultArtifactPublicationSet::class.java).addCandidate(zipArtifact)
        project.components.add(IntelliJPluginLibrary())
    }

    fun configurePublishPluginTask(project: Project) {
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

    fun configureProcessResources(project: Project) {
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

    fun resolveDistributionFile(project: Project): File? {
        val buildPluginTask = project.tasks.findByName(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME) as Zip
        return buildPluginTask.archiveFile.orNull?.asFile?.takeIf { it.exists() }
    }
}
