package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.IdeVersion
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.gradle.util.VersionNumber
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.*
import org.jetbrains.intellij.jbr.JbrResolver
import org.jetbrains.intellij.model.IdeaPlugin
import org.jetbrains.intellij.tasks.*

class IntelliJPluginGr extends IntelliJPlugin {

    @Override
    void apply(Project project) {
        checkGradleVersion(project)
        project.getPlugins().apply(JavaPlugin)
        def intellijExtension = project.extensions.create(IntelliJPluginConstants.EXTENSION_NAME, IntelliJPluginExtension, project.objects) as IntelliJPluginExtension
        intellijExtension.with {
            pluginName.convention(project.provider {
                project.name
            })
            updateSinceUntilBuild.convention(true)
            sameSinceUntilBuild.convention(false)
            instrumentCode.convention(true)
            sandboxDirectory.convention(project.provider({
                new File(project.buildDir, IntelliJPluginConstants.DEFAULT_SANDBOX).absolutePath
            }))
            intellijRepo.convention(IntelliJPluginConstants.DEFAULT_INTELLIJ_REPO)
            downloadSources.convention(!System.getenv().containsKey("CI"))
            configureDefaultDependencies.convention(true)
            type.convention("IC")
        }
        configureConfigurations(project, intellijExtension)
        configureTasks(project, intellijExtension)
    }

    private void checkGradleVersion(@NotNull Project project) {
        if (VersionNumber.parse(project.gradle.gradleVersion) < VersionNumber.parse("5.1")) {
            throw new PluginInstantiationException("gradle-intellij-plugin requires Gradle 5.1 and higher")
        }
    }

    private void configureConfigurations(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        def idea = project.configurations.create(IntelliJPluginConstants.IDEA_CONFIGURATION_NAME).setVisible(false)
        configureIntellijDependency(project, extension, idea)

        def ideaPlugins = project.configurations.create(IntelliJPluginConstants.IDEA_PLUGINS_CONFIGURATION_NAME).setVisible(false)
        configurePluginDependencies(project, extension, ideaPlugins)

        def defaultDependencies = project.configurations.create("intellijDefaultDependencies").setVisible(false)
        defaultDependencies.defaultDependencies { dependencies ->
            dependencies.add(project.dependencies.create('org.jetbrains:annotations:19.0.0'))
        }

        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom defaultDependencies, idea, ideaPlugins
        project.configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom defaultDependencies, idea, ideaPlugins
    }

    private def configureTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring plugin")
        project.tasks.whenTaskAdded {
            if (it instanceof RunIdeBase) {
                prepareConventionMappingsForRunIdeTask(project, extension, it, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            }
            if (it instanceof RunIdeForUiTestTask) {
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
        configureBuildSearchableOptionsTask(project, extension)
        configureJarSearchableOptionsTask(project)
        configureBuildPluginTask(project)
        configurePublishPluginTask(project)
        configureProcessResources(project)
        configureInstrumentation(project, extension)
        configureDependencyExtensions(project, extension)
        assert !project.state.executed: "afterEvaluate is a no-op for an executed project"
        project.afterEvaluate { Project p -> configureProjectAfterEvaluate(p, extension) }
    }

    private void configureProjectAfterEvaluate(@NotNull Project project,
                                                      @NotNull IntelliJPluginExtension extension) {
        for (def subproject : project.subprojects) {
            if (subproject.plugins.findPlugin(IntelliJPluginGr) != null) {
                continue
            }
            def subprojectExtension = subproject.extensions.findByType(IntelliJPluginExtension)
            if (subprojectExtension) {
                configureProjectAfterEvaluate(subproject, subprojectExtension)
            }
        }

        configureTestTasks(project, extension)
    }

    private void configureDependencyExtensions(@NotNull Project project,
                                                      @NotNull IntelliJPluginExtension extension) {
        project.dependencies.ext.intellij = { Closure filter = {} ->
            if (!project.state.executed) {
                throw new GradleException('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
            }
            project.files(extension.getIdeaDependency(project).jarFiles).asFileTree.matching(filter)
        }

        project.dependencies.ext.intellijPlugin = { String plugin, Closure filter = {} ->
            if (!project.state.executed) {
                throw new GradleException("intellij plugin '$plugin' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
            }
            def pluginDep = extension.getPluginDependenciesList(project).find { it.id == plugin }
            if (pluginDep == null || pluginDep.jarFiles == null || pluginDep.jarFiles.empty) {
                throw new GradleException("intellij plugin '$plugin' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
            }
            project.files(pluginDep.jarFiles).asFileTree.matching(filter)
        }

        project.dependencies.ext.intellijPlugins = { String... plugins ->
            def selectedPlugins = new HashSet<PluginDependency>()
            def nonValidPlugins = []
            for (pluginName in plugins) {
                def plugin = extension.getPluginDependenciesList(project).find { it.id == pluginName }
                if (plugin == null || plugin.jarFiles == null || plugin.jarFiles.empty) {
                    nonValidPlugins.add(pluginName)
                } else {
                    selectedPlugins.add(plugin)
                }
            }
            if (!nonValidPlugins.empty) {
                throw new GradleException("intellij plugins $nonValidPlugins are not (yet) configured or not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
            }
            project.files(selectedPlugins.collect { it.jarFiles })
        }

        project.dependencies.ext.intellijExtra = { String extra, Closure filter = {} ->
            if (!project.state.executed) {
                throw new GradleException('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
            }
            def dependency = extension.getIdeaDependency(project)
            def extraDep = dependency != null ? dependency.extraDependencies.find { it.name == extra } : null
            if (extraDep == null || extraDep.jarFiles == null || extraDep.jarFiles.empty) {
                throw new GradleException("intellij extra artifact '$extra' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
            }
            project.files(extraDep.jarFiles).asFileTree.matching(filter)
        }
    }

    private void configureIntellijDependency(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension,
                                                    @NotNull Configuration configuration) {
        configuration.withDependencies { dependencies ->
            Utils.info(project, "Configuring IDE dependency")
            def resolver = new IdeaDependencyManager(extension.intellijRepo.get(), extension.ideaDependencyCachePath.orNull)
            def ideaDependency
            def localPath = extension.localPath.orNull
            if (localPath != null) {
                if (extension.version.orNull != null) {
                    Utils.warn(project, "Both `localPath` and `version` specified, second would be ignored")
                }
                Utils.info(project, "Using path to locally installed IDE: '$localPath'")
                ideaDependency = resolver.resolveLocal(project, localPath, extension.localSourcesPath.orNull)
            } else {
                Utils.info(project, "Using IDE from remote repository")
                def version = extension.getVersionNumber() ?: IntelliJPluginConstants.DEFAULT_IDEA_VERSION
                def extraDependencies = extension.extraDependencies.get()
                ideaDependency = resolver.resolveRemote(project, version, extension.getVersionType(), extension.downloadSources.get(), extraDependencies)
            }
            extension.ideaDependency = ideaDependency
            if (extension.configureDefaultDependencies.get()) {
                Utils.info(project, "$ideaDependency.buildNumber is used for building")
                resolver.register(project, ideaDependency, dependencies)
                if (!ideaDependency.extraDependencies.empty) {
                    Utils.info(project, "Note: $ideaDependency.buildNumber extra dependencies (${ideaDependency.extraDependencies}) should be applied manually")
                }
            } else {
                Utils.info(project, "IDE ${ideaDependency.buildNumber} dependencies are applied manually")
            }
        }
        def toolsJar = Jvm.current().toolsJar
        if (toolsJar) {
            project.dependencies.add(JavaPlugin.RUNTIME_ONLY_CONFIGURATION_NAME, project.files(toolsJar))
        }
    }

    private void configurePluginDependencies(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension,
                                                    @NotNull Configuration configuration) {
        configuration.withDependencies { dependencies ->
            Utils.info(project, "Configuring plugin dependencies")
            def ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
            def resolver = new PluginDependencyManager(project.gradle.gradleUserHomeDir.absolutePath, extension.getIdeaDependency(project), extension.pluginsRepos)
            extension.plugins.get().each {
                Utils.info(project, "Configuring plugin $it")
                if (it instanceof Project) {
                    configureProjectPluginDependency(project, it, dependencies, extension)
                } else {
                    def pluginDependency = PluginDependencyNotation.parsePluginDependencyString(it.toString())
                    if (pluginDependency.id == null) {
                        throw new BuildException("Failed to resolve plugin $it", null)
                    }
                    def plugin = resolver.resolve(project, pluginDependency)
                    if (plugin == null) {
                        throw new BuildException("Failed to resolve plugin $it", null)
                    }
                    if (ideVersion != null && !plugin.isCompatible(ideVersion)) {
                        throw new BuildException("Plugin $it is not compatible to ${ideVersion.asString()}", null)
                    }
                    configurePluginDependency(project, plugin, extension, dependencies, resolver)
                }
            }
            if (extension.configureDefaultDependencies.get()) {
                configureBuiltinPluginsDependencies(project, dependencies, resolver, extension)
            }
            verifyJavaPluginDependency(extension, project)
            for (PluginsRepository repository : extension.getPluginsRepos()) {
                repository.postResolve(project)
            }
        }

        project.afterEvaluate {
            extension.plugins.get().findAll { it instanceof Project }.each { Project dependency ->
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

    private void verifyJavaPluginDependency(IntelliJPluginExtension extension, Project project) {
        def plugins = extension.plugins.get()
        def hasJavaPluginDependency = plugins.contains('java') || plugins.contains('com.intellij.java')
        if (!hasJavaPluginDependency && new File(extension.getIdeaDependency(project).classes, "plugins/java").exists()) {
            Utils.sourcePluginXmlFiles(project).each { file ->
                def pluginXml = Utils.parseXml(file, IdeaPlugin)
                pluginXml.depends.each {
                    if (it.value == 'com.intellij.modules.java') {
                        throw new BuildException("The project depends on `com.intellij.modules.java` module but doesn't declare a compile dependency on it.\n" +
                                "Please delete `depends` tag from $file.absolutePath or add `java` plugin to Gradle dependencies (e.g. intellij { plugins = ['java'] })", null)
                    }
                }
            }
        }
    }

    private void configureBuiltinPluginsDependencies(@NotNull Project project,
                                                            @NotNull DependencySet dependencies,
                                                            @NotNull PluginDependencyManager resolver,
                                                            @NotNull IntelliJPluginExtension extension) {
        def configuredPlugins = extension.unresolvedPluginDependencies
                .findAll { it.builtin }
                .collect { it.id }
        extension.ideaDependency.get().pluginsRegistry.collectBuiltinDependencies(configuredPlugins).forEach {
            def plugin = resolver.resolve(project, new PluginDependencyNotation(it, null, null))
            configurePluginDependency(project, plugin, extension, dependencies, resolver)
        }
    }

    private void configurePluginDependency(@NotNull Project project,
                                                  @NotNull PluginDependency plugin,
                                                  @NotNull IntelliJPluginExtension extension,
                                                  @NotNull DependencySet dependencies,
                                                  @NotNull PluginDependencyManager resolver) {
        if (extension.configureDefaultDependencies.get()) {
            resolver.register(project, plugin, dependencies)
        }
        extension.addPluginDependency(plugin)
        project.tasks.withType(PrepareSandboxTask).each {
            it.configureExternalPlugin(plugin)
        }
    }

    private void configureProjectPluginTasksDependency(@NotNull Project project, @NotNull Project dependency) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPluginGr) == null) {
            throw new BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        def dependencySandboxTask = dependency.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        project.tasks.withType(PrepareSandboxTask).each {
            it.dependsOn(dependencySandboxTask)
        }
    }

    private void configureProjectPluginDependency(@NotNull Project project,
                                                         @NotNull Project dependency,
                                                         @NotNull DependencySet dependencies,
                                                         @NotNull IntelliJPluginExtension extension) {
        // invoke on demand, when plugins artifacts are needed
        if (dependency.plugins.findPlugin(IntelliJPluginGr) == null) {
            throw new BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        dependencies.add(project.dependencies.create(dependency))
        def pluginDependency = new PluginProjectDependency(dependency)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType(PrepareSandboxTask).each {
            it.configureCompositePlugin(pluginDependency)
        }
    }

    private void configurePatchPluginXmlTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring patch plugin.xml task")
        project.tasks.create(IntelliJPluginConstants.PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask).with {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"
            it.version.convention(project.provider({
                project.version?.toString()
            }))
            it.pluginXmlFiles.convention(project.provider({
                Utils.sourcePluginXmlFiles(project)
            }))
            it.destinationDir.convention(
                    project.layout.dir(project.provider({
                        new File(project.buildDir, IntelliJPluginConstants.PLUGIN_XML_DIR_NAME)
                    }))
            )
            it.sinceBuild.convention(project.provider({
                if (extension.updateSinceUntilBuild.get()) {
                    def ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
                    return "$ideVersion.baselineVersion.$ideVersion.build".toString()
                }
            }))
            it.untilBuild.convention(project.provider({
                if (extension.updateSinceUntilBuild.get()) {
                    def ideVersion = IdeVersion.createIdeVersion(extension.getIdeaDependency(project).buildNumber)
                    return extension.sameSinceUntilBuild.get() ? "${sinceBuild.get()}.*".toString()
                            : "$ideVersion.baselineVersion.*".toString()
                }
            }))
        }
    }

    private void configurePrepareSandboxTasks(@NotNull Project project,
                                                     @NotNull IntelliJPluginExtension extension) {
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, "")
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_TESTING_SANDBOX_TASK_NAME, "-test")
        configurePrepareSandboxTask(project, extension, IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME, "-uiTest").with { task ->
            def downloadPluginTask = project.tasks.getByName(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME) as DownloadRobotServerPluginTask
            task.from(downloadPluginTask.outputDir)
            task.dependsOn(downloadPluginTask)
        }
    }

    private void configureRobotServerDownloadTask(@NotNull Project project) {
        Utils.info(project, "Configuring robot-server download Task")

        project.tasks.create(IntelliJPluginConstants.DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME, DownloadRobotServerPluginTask).with { task ->
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Download robot-server plugin."
        }
    }

    private Task configurePrepareSandboxTask(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension,
                                                    String taskName,
                                                    String testSuffix) {
        Utils.info(project, "Configuring $taskName task")
        return project.tasks.create(taskName, PrepareSandboxTask).with { task ->
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Prepare sandbox directory with installed plugin and its dependencies."
            task.pluginName.convention(project.provider({
                extension.pluginName.get()
            }))
            task.pluginJar.convention(
                    project.layout.file(project.provider({
                        VersionNumber.parse(project.gradle.gradleVersion) >= VersionNumber.parse("5.1")
                                ? (project.tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).archiveFile.getOrNull().asFile
                                : (project.tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).archivePath
                    }))
            )
            conventionMapping('destinationDir', {
                project.file("${extension.sandboxDirectory.get()}/plugins$testSuffix")
            })
            task.configDirectory.convention(project.provider({
                "${extension.sandboxDirectory.get()}/config$testSuffix"
            }))
            task.librariesToIgnore.convention(project.provider({
                project.files(extension.getIdeaDependency(project).jarFiles)
            }))
            task.pluginDependencies.convention(project.provider({
                extension.getPluginDependenciesList(project)
            }))
            dependsOn(JavaPlugin.JAR_TASK_NAME)
        }
    }

    private void configureRunPluginVerifierTask(@NotNull Project project) {
        Utils.info(project, "Configuring run plugin verifier task")
        project.tasks.create(IntelliJPluginConstants.RUN_PLUGIN_VERIFIER_TASK_NAME, RunPluginVerifierTask).with {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds."

            it.failureLevel.convention(
                    EnumSet.of(RunPluginVerifierTask.FailureLevel.INVALID_PLUGIN)
            )
            it.verifierVersion.convention(VERIFIER_VERSION_LATEST)
            it.distributionFile.convention(
                    project.layout.file(project.provider({
                        resolveDistributionFile(project)
                    }))
            )
            it.verificationReportsDirectory.convention(project.provider({
                "${project.buildDir}/reports/pluginVerifier".toString()
            }))
            it.downloadDirectory.convention(project.provider({
                ideDownloadDirectory().toString()
            }))
            it.teamCityOutputFormat.convention(false)

            dependsOn { project.getTasksByName(IntelliJPluginConstants.BUILD_PLUGIN_TASK_NAME, false) }
            dependsOn { project.getTasksByName(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, false) }
        }
    }

    private void configurePluginVerificationTask(@NotNull Project project) {
        Utils.info(project, "Configuring plugin verification task")
        project.tasks.create(IntelliJPluginConstants.VERIFY_PLUGIN_TASK_NAME, VerifyPluginTask).with {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Validates completeness and contents of plugin.xml descriptors as well as plugin’s archive structure."

            it.pluginDirectory.convention(project.provider({
                def prepareSandboxTask = project.tasks.findByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
                def path = new File(prepareSandboxTask.getDestinationDir(), prepareSandboxTask.getPluginName().get()).path
                project.layout.projectDirectory.dir(path)
            }))

            dependsOn { project.getTasksByName(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME, false) }
        }
    }

    private void configureRunIdeaTask(@NotNull Project project) {
        Utils.info(project, "Configuring run IDE task")
        project.tasks.create(IntelliJPluginConstants.RUN_IDE_TASK_NAME, RunIdeTask).with { RunIdeTask task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Runs Intellij IDEA with installed plugin."
            task.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private void configureRunIdeaForUiTestsTask(@NotNull Project project) {
        Utils.info(project, "Configuring run IDE for ui tests task")
        project.tasks.create(IntelliJPluginConstants.RUN_IDE_FOR_UI_TESTS_TASK_NAME, RunIdeForUiTestTask).with { RunIdeForUiTestTask task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Runs Intellij IDEA with installed plugin and robot-server plugin for ui tests."
            task.dependsOn(IntelliJPluginConstants.PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
        }
    }

    private void configureBuildSearchableOptionsTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring build searchable options task")
        project.tasks.create(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME, BuildSearchableOptionsTask).with { BuildSearchableOptionsTask task ->
            task.group = IntelliJPluginConstants.GROUP_NAME
            task.description = "Builds searchable options for plugin."
            task.setArgs(["${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}", "true"])
            task.outputs.dir("${project.buildDir}/${IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME}")
            task.dependsOn(IntelliJPluginConstants.PREPARE_SANDBOX_TASK_NAME)
            task.onlyIf {
                def number = Utils.ideBuildNumber(task.ideDirectory.get().asFile)
                VersionNumber.parse(number[number.indexOf('-') + 1..-1]) >= VersionNumber.parse("191.2752")
            }
        }
    }

    private void prepareConventionMappingsForRunIdeTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension,
                                                               @NotNull RunIdeBase task, @NotNull String prepareSandBoxTaskName) {
        def prepareSandboxTask = project.tasks.findByName(prepareSandBoxTaskName) as PrepareSandboxTask
        task.ideDirectory.convention(project.provider({
            def path = extension.getIdeaDependency(project).classes.path
            project.layout.projectDirectory.dir(path)
        }))
        task.requiredPluginIds.convention(project.provider({
            Utils.getPluginIds(project)
        }))
        task.configDirectory.convention(project.provider({
            project.file(prepareSandboxTask.getConfigDirectory().get())
        }))
        task.pluginsDirectory.convention(project.provider({
            def path = prepareSandboxTask.getDestinationDir().path
            project.layout.projectDirectory.dir(path)
        }))
        task.systemDirectory.convention(project.provider({
            project.file("${extension.sandboxDirectory.get()}/system")
        }))
        task.autoReloadPlugins.convention(project.provider({
            def number = Utils.ideBuildNumber(task.ideDirectory.get().asFile)
            VersionNumber.parse(number[number.indexOf('-') + 1..-1]) >= VersionNumber.parse("202.0")
        }))
        task.conventionMapping("executable", {
            def jbrResolver = new JbrResolver(project, task, extension.jreRepo.orNull)
            def jbrVersion = task.getJbrVersion().getOrNull()
            if (jbrVersion != null) {
                def jbr = jbrResolver.resolve(jbrVersion)
                if (jbr != null) {
                    return jbr.javaExecutable
                }
                Utils.warn(task, "Cannot resolve JBR $jbrVersion. Falling back to builtin JBR.")
            }
            def builtinJbrVersion = Utils.getBuiltinJbrVersion(task.ideDirectory.get().asFile)
            if (builtinJbrVersion != null) {
                def builtinJbr = jbrResolver.resolve(builtinJbrVersion)
                if (builtinJbr != null) {
                    return builtinJbr.javaExecutable
                }
                Utils.warn(task, "Cannot resolve builtin JBR $builtinJbrVersion. Falling local Java.")
            }
            return Jvm.current().javaExecutable.absolutePath
        })
    }

    private void configureJarSearchableOptionsTask(@NotNull Project project) {
        Utils.info(project, "Configuring jar searchable options task")
        project.tasks.create(IntelliJPluginConstants.JAR_SEARCHABLE_OPTIONS_TASK_NAME, JarSearchableOptionsTask).with {
            group = IntelliJPluginConstants.GROUP_NAME
            description = "Jars searchable options."
            if (VersionNumber.parse(project.gradle.gradleVersion) >= VersionNumber.parse("5.1")) {
                archiveBaseName.set('lib/searchableOptions')
                destinationDirectory.set(project.layout.buildDirectory.dir("libsSearchableOptions"))
            } else {
                conventionMapping('baseName', { 'lib/searchableOptions' })
                destinationDir = new File(project.buildDir, "libsSearchableOptions")
            }

            dependsOn(IntelliJPluginConstants.BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
            onlyIf { new File(project.buildDir, IntelliJPluginConstants.SEARCHABLE_OPTIONS_DIR_NAME).isDirectory() }
        }
    }
}
