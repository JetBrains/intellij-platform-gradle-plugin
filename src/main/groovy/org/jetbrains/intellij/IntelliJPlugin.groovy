package org.jetbrains.intellij

import com.jetbrains.plugin.structure.intellij.version.*
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.logging.Logger
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.PluginInstantiationException
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.tasks.Jar
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.gradle.util.VersionNumber
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginDependencyManager
import org.jetbrains.intellij.dependency.PluginDependencyNotation
import org.jetbrains.intellij.dependency.PluginProjectDependency
import org.jetbrains.intellij.dependency.PluginsRepository
import org.jetbrains.intellij.jbr.JbrResolver
import org.jetbrains.intellij.tasks.*

class IntelliJPlugin implements Plugin<Project> {
    public static final GROUP_NAME = "intellij"
    public static final EXTENSION_NAME = "intellij"
    public static final String DEFAULT_SANDBOX = 'idea-sandbox'
    public static final String PATCH_PLUGIN_XML_TASK_NAME = "patchPluginXml"
    public static final String PLUGIN_XML_DIR_NAME = "patchedPluginXmlFiles"
    public static final String PREPARE_SANDBOX_TASK_NAME = "prepareSandbox"
    public static final String PREPARE_TESTING_SANDBOX_TASK_NAME = "prepareTestingSandbox"
    public static final String PREPARE_UI_TESTING_SANDBOX_TASK_NAME = "prepareUiTestingSandbox"
    public static final String DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME = "downloadRobotServerPlugin"
    public static final String RUN_PLUGIN_VERIFIER_TASK_NAME = "runPluginVerifier"
    public static final String VERIFY_PLUGIN_TASK_NAME = "verifyPlugin"
    public static final String RUN_IDE_TASK_NAME = "runIde"
    public static final String RUN_IDE_FOR_UI_TESTS_TASK_NAME = "runIdeForUiTests"
    public static final String BUILD_SEARCHABLE_OPTIONS_TASK_NAME = "buildSearchableOptions"
    public static final String SEARCHABLE_OPTIONS_DIR_NAME = "searchableOptions"
    public static final String JAR_SEARCHABLE_OPTIONS_TASK_NAME = "jarSearchableOptions"
    public static final String BUILD_PLUGIN_TASK_NAME = "buildPlugin"
    public static final String PUBLISH_PLUGIN_TASK_NAME = "publishPlugin"

    public static final String IDEA_CONFIGURATION_NAME = "idea"
    public static final String IDEA_PLUGINS_CONFIGURATION_NAME = "ideaPlugins"

    public static final Logger LOG = Logging.getLogger(IntelliJPlugin)
    public static final String DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"
    public static final String DEFAULT_INTELLIJ_REPO = 'https://cache-redirector.jetbrains.com/www.jetbrains.com/intellij-repository'
    public static final String DEFAULT_JBR_REPO = 'https://cache-redirector.jetbrains.com/intellij-jbr'
    public static final String DEFAULT_INTELLIJ_PLUGIN_VERIFIER_REPO = 'https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/intellij-plugin-verifier/intellij-plugin-verifier'
    public static final String OLD_INTELLIJ_PLUGIN_VERIFIER_REPO = 'https://cache-redirector.jetbrains.com/jetbrains.bintray.com/intellij-plugin-service'
    public static final String DEFAULT_INTELLIJ_PLUGINS_REPO = 'https://cache-redirector.jetbrains.com/plugins.jetbrains.com/maven'
    public static final String PLUGIN_PATH = 'plugin.path'

    @Override
    void apply(Project project) {
        checkGradleVersion(project)
        project.getPlugins().apply(JavaPlugin)
        def intellijExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension) as IntelliJPluginExtension
        intellijExtension.with {
            extensionProject = project
            pluginName = project.name
            sandboxDirectory = { new File(project.buildDir, DEFAULT_SANDBOX).absolutePath }
        }
        configureConfigurations(project, intellijExtension)
        configureTasks(project, intellijExtension)
    }

    private static void checkGradleVersion(@NotNull Project project) {
        if (VersionNumber.parse(project.gradle.gradleVersion) < VersionNumber.parse("4.9")) {
            throw new PluginInstantiationException("gradle-intellij-plugin requires Gradle 4.9 and higher")
        }
    }

    private static void configureConfigurations(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        def idea = project.configurations.create(IDEA_CONFIGURATION_NAME).setVisible(false)
        configureIntellijDependency(project, extension, idea)

        def ideaPlugins = project.configurations.create(IDEA_PLUGINS_CONFIGURATION_NAME).setVisible(false)
        configurePluginDependencies(project, extension, ideaPlugins)

        def defaultDependencies = project.configurations.create("intellijDefaultDependencies").setVisible(false)
        defaultDependencies.defaultDependencies { dependencies ->
            dependencies.add(project.dependencies.create('org.jetbrains:annotations:19.0.0'))
        }

        project.configurations.getByName(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME).extendsFrom defaultDependencies, idea, ideaPlugins
        project.configurations.getByName(JavaPlugin.TEST_IMPLEMENTATION_CONFIGURATION_NAME).extendsFrom defaultDependencies, idea, ideaPlugins
    }

    private static def configureTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring plugin")
        project.tasks.whenTaskAdded {
            if (it instanceof RunIdeBase) {
                prepareConventionMappingsForRunIdeTask(project, extension, it, PREPARE_SANDBOX_TASK_NAME)
            }
            if (it instanceof RunIdeForUiTestTask) {
                prepareConventionMappingsForRunIdeTask(project, extension, it, PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
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

    private static void configureProjectAfterEvaluate(@NotNull Project project,
                                                      @NotNull IntelliJPluginExtension extension) {
        for (def subproject : project.subprojects) {
            if (subproject.plugins.findPlugin(IntelliJPlugin) != null) {
                continue
            }
            def subprojectExtension = subproject.extensions.findByType(IntelliJPluginExtension)
            if (subprojectExtension) {
                configureProjectAfterEvaluate(subproject, subprojectExtension)
            }
        }

        configureTestTasks(project, extension)
    }

    private static void configureDependencyExtensions(@NotNull Project project,
                                                      @NotNull IntelliJPluginExtension extension) {
        project.dependencies.ext.intellij = { Closure filter = {} ->
            if (!project.state.executed) {
                throw new GradleException('intellij is not (yet) configured. Please note that you should configure intellij dependencies in the afterEvaluate block')
            }
            project.files(extension.ideaDependency.jarFiles).asFileTree.matching(filter)
        }

        project.dependencies.ext.intellijPlugin = { String plugin, Closure filter = {} ->
            if (!project.state.executed) {
                throw new GradleException("intellij plugin '$plugin' is not (yet) configured. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
            }
            def pluginDep = extension.pluginDependencies.find { it.id == plugin }
            if (pluginDep == null || pluginDep.jarFiles == null || pluginDep.jarFiles.empty) {
                throw new GradleException("intellij plugin '$plugin' is not found. Please note that you should specify plugins in the intellij.plugins property and configure dependencies on them in the afterEvaluate block")
            }
            project.files(pluginDep.jarFiles).asFileTree.matching(filter)
        }

        project.dependencies.ext.intellijPlugins = { String... plugins ->
            def selectedPlugins = new HashSet<PluginDependency>()
            def nonValidPlugins = []
            for (pluginName in plugins) {
                def plugin = extension.pluginDependencies.find { it.id == pluginName }
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
            def dependency = extension.ideaDependency
            def extraDep = dependency != null ? dependency.extraDependencies.find { it.name == extra } : null
            if (extraDep == null || extraDep.jarFiles == null || extraDep.jarFiles.empty) {
                throw new GradleException("intellij extra artifact '$extra' is not found. Please note that you should specify extra dependencies in the intellij.extraDependencies property and configure dependencies on them in the afterEvaluate block")
            }
            project.files(extraDep.jarFiles).asFileTree.matching(filter)
        }
    }

    private static void configureIntellijDependency(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension,
                                                    @NotNull Configuration configuration) {
        configuration.withDependencies { dependencies ->
            Utils.info(project, "Configuring IDE dependency")
            def resolver = new IdeaDependencyManager(extension.intellijRepo ?: DEFAULT_INTELLIJ_REPO)
            def ideaDependency
            if (extension.localPath != null) {
                if (extension.version != null) {
                    Utils.warn(project, "Both `localPath` and `version` specified, second would be ignored")
                }
                Utils.info(project, "Using path to locally installed IDE: '${extension.localPath}'")
                ideaDependency = resolver.resolveLocal(project, extension.localPath, extension.localSourcesPath)
            } else {
                Utils.info(project, "Using IDE from remote repository")
                def version = extension.version ?: DEFAULT_IDEA_VERSION
                ideaDependency = resolver.resolveRemote(project, version, extension.type, extension.downloadSources,
                        extension.extraDependencies)
            }
            extension.ideaDependency = ideaDependency
            if (extension.configureDefaultDependencies) {
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

    private static void configurePluginDependencies(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension,
                                                    @NotNull Configuration configuration) {
        configuration.withDependencies { dependencies ->
            Utils.info(project, "Configuring plugin dependencies")
            def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
            def resolver = new PluginDependencyManager(project.gradle.gradleUserHomeDir.absolutePath,
                    extension.ideaDependency, extension.pluginsRepos)
            extension.plugins.each {
                Utils.info(project, "Configuring plugin $it")
                if (it instanceof Project) {
                    configureProjectPluginDependency(project, it, dependencies, extension)
                } else {
                    def pluginDependency = Utils.parsePluginDependencyString(it.toString())
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
            if (extension.configureDefaultDependencies) {
                configureBuiltinPluginsDependencies(project, dependencies, resolver, extension)
            }
            verifyJavaPluginDependency(extension, project)
            for (PluginsRepository repository : extension.getPluginsRepos()) {
                repository.postResolve()
            }
        }

        project.afterEvaluate {
            extension.plugins.findAll { it instanceof Project }.each { Project dependency ->
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

    private static void verifyJavaPluginDependency(IntelliJPluginExtension extension, Project project) {
        def hasJavaPluginDependency = extension.plugins.contains('java') || extension.plugins.contains('com.intellij.java')
        if (!hasJavaPluginDependency && new File(extension.ideaDependency.classes, "plugins/java").exists()) {
            Utils.sourcePluginXmlFiles(project).each { file ->
                def pluginXml = Utils.parseXml(file)
                if (pluginXml) {
                    pluginXml.depends.each {
                        if (it.text() == 'com.intellij.modules.java') {
                            throw new BuildException("The project depends on `com.intellij.modules.java` module but doesn't declare a compile dependency on it.\n" +
                                    "Please delete `depends` tag from $file.absolutePath or add `java` plugin to Gradle dependencies (e.g. intellij { plugins = ['java'] })", null)
                        }
                    }
                }
            }
        }
    }

    private static void configureBuiltinPluginsDependencies(@NotNull Project project,
                                                            @NotNull DependencySet dependencies,
                                                            @NotNull PluginDependencyManager resolver,
                                                            @NotNull IntelliJPluginExtension extension) {
        def configuredPlugins = extension.unresolvedPluginDependencies
                .findAll { it.builtin }
                .collect { it.id }
        extension.ideaDependency.pluginsRegistry.collectBuiltinDependencies(configuredPlugins).forEach {
            def plugin = resolver.resolve(project, new PluginDependencyNotation(it, null, null))
            configurePluginDependency(project, plugin, extension, dependencies, resolver)
        }
    }

    private static void configurePluginDependency(@NotNull Project project,
                                                  @NotNull PluginDependency plugin,
                                                  @NotNull IntelliJPluginExtension extension,
                                                  @NotNull DependencySet dependencies,
                                                  @NotNull PluginDependencyManager resolver) {
        if (extension.configureDefaultDependencies) {
            resolver.register(project, plugin, dependencies)
        }
        extension.addPluginDependency(plugin)
        project.tasks.withType(PrepareSandboxTask).each {
            it.configureExternalPlugin(plugin)
        }
    }

    private static void configureProjectPluginTasksDependency(@NotNull Project project, @NotNull Project dependency) {
        // invoke before tasks graph is ready
        if (dependency.plugins.findPlugin(IntelliJPlugin) == null) {
            throw new BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        def dependencySandboxTask = dependency.tasks.findByName(PREPARE_SANDBOX_TASK_NAME)
        project.tasks.withType(PrepareSandboxTask).each {
            it.dependsOn(dependencySandboxTask)
        }
    }

    private static void configureProjectPluginDependency(@NotNull Project project,
                                                         @NotNull Project dependency,
                                                         @NotNull DependencySet dependencies,
                                                         @NotNull IntelliJPluginExtension extension) {
        // invoke on demand, when plugins artifacts are needed
        if (dependency.plugins.findPlugin(IntelliJPlugin) == null) {
            throw new BuildException("Cannot use $dependency as a plugin dependency. IntelliJ Plugin is not found." + dependency.plugins, null)
        }
        dependencies.add(project.dependencies.create(dependency))
        def pluginDependency = new PluginProjectDependency(dependency)
        extension.addPluginDependency(pluginDependency)
        project.tasks.withType(PrepareSandboxTask).each {
            it.configureCompositePlugin(pluginDependency)
        }
    }

    private static void configurePatchPluginXmlTask(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring patch plugin.xml task")
        project.tasks.create(PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask).with {
            group = GROUP_NAME
            description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"
            conventionMapping('version', { project.version?.toString() })
            conventionMapping('pluginXmlFiles', { Utils.sourcePluginXmlFiles(project) })
            conventionMapping('destinationDir', { new File(project.buildDir, PLUGIN_XML_DIR_NAME) })
            conventionMapping('sinceBuild', {
                if (extension.updateSinceUntilBuild) {
                    def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
                    return "$ideVersion.baselineVersion.$ideVersion.build".toString()
                }
            })
            conventionMapping('untilBuild', {
                if (extension.updateSinceUntilBuild) {
                    def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
                    return extension.sameSinceUntilBuild ? "${getSinceBuild()}.*".toString()
                            : "$ideVersion.baselineVersion.*".toString()
                }
            })
        }
    }

    private static void configurePrepareSandboxTasks(@NotNull Project project,
                                                     @NotNull IntelliJPluginExtension extension) {
        configurePrepareSandboxTask(project, extension, PREPARE_SANDBOX_TASK_NAME, "")
        configurePrepareSandboxTask(project, extension, PREPARE_TESTING_SANDBOX_TASK_NAME, "-test")
        configurePrepareSandboxTask(project, extension, PREPARE_UI_TESTING_SANDBOX_TASK_NAME, "-uiTest").with { task ->
            def downloadPluginTask = project.tasks.getByName(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME) as DownloadRobotServerPluginTask
            task.from(downloadPluginTask.outputDir)
            task.dependsOn(downloadPluginTask)
        }
    }

    private static void configureRobotServerDownloadTask(@NotNull Project project) {
        Utils.info(project, "Configuring robot-server download Task")

        project.tasks.create(DOWNLOAD_ROBOT_SERVER_PLUGIN_TASK_NAME, DownloadRobotServerPluginTask).with { task ->
            group = GROUP_NAME
            description = "Download robot-server plugin."
        }
    }

    private static Task configurePrepareSandboxTask(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension,
                                                    String taskName,
                                                    String testSuffix) {
        Utils.info(project, "Configuring $taskName task")
        return project.tasks.create(taskName, PrepareSandboxTask).with { task ->
            group = GROUP_NAME
            description = "Prepare sandbox directory with installed plugin and its dependencies."
            conventionMapping('pluginName', { extension.pluginName })
            conventionMapping('pluginJar', {
                return VersionNumber.parse(project.gradle.gradleVersion) >= VersionNumber.parse("5.1")
                        ? (project.tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).archiveFile.getOrNull()?.getAsFile()
                        : (project.tasks.findByName(JavaPlugin.JAR_TASK_NAME) as Jar).archivePath
            })
            conventionMapping('destinationDir', { project.file("${Utils.stringInput(extension.sandboxDirectory)}/plugins$testSuffix") })
            conventionMapping('configDirectory', { "${Utils.stringInput(extension.sandboxDirectory)}/config$testSuffix".toString() })
            conventionMapping('librariesToIgnore', { project.files(extension.ideaDependency.jarFiles) })
            conventionMapping('pluginDependencies', { extension.pluginDependencies })
            dependsOn(JavaPlugin.JAR_TASK_NAME)
        }
    }

    private static void configureRunPluginVerifierTask(@NotNull Project project) {
        Utils.info(project, "Configuring run plugin verifier task")
        project.tasks.create(RUN_PLUGIN_VERIFIER_TASK_NAME, RunPluginVerifierTask).with {
            group = GROUP_NAME
            description = "Runs the IntelliJ Plugin Verifier tool to check the binary compatibility with specified IntelliJ IDE builds."
            conventionMapping('failureLevel', { EnumSet.of(RunPluginVerifierTask.FailureLevel.INVALID_PLUGIN) })
            conventionMapping('verifierVersion', { VERIFIER_VERSION_LATEST })
            conventionMapping('distributionFile', { resolveDistributionFile(project) })
            conventionMapping('verificationReportsDirectory', { "${project.buildDir}/reports/pluginVerifier".toString() })
            conventionMapping('downloadDirectory', { ideDownloadDirectory().toString() })
            dependsOn { project.getTasksByName(BUILD_PLUGIN_TASK_NAME, false) }
            dependsOn { project.getTasksByName(VERIFY_PLUGIN_TASK_NAME, false) }
        }
    }

    private static void configurePluginVerificationTask(@NotNull Project project) {
        Utils.info(project, "Configuring plugin verification task")
        project.tasks.create(VERIFY_PLUGIN_TASK_NAME, VerifyPluginTask).with {
            group = GROUP_NAME
            description = "Validates completeness and contents of plugin.xml descriptors as well as plugin’s archive structure."
            conventionMapping('pluginDirectory', {
                def prepareSandboxTask = project.tasks.findByName(PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
                new File(prepareSandboxTask.getDestinationDir(), prepareSandboxTask.getPluginName())
            })
            dependsOn { project.getTasksByName(PREPARE_SANDBOX_TASK_NAME, false) }
        }
    }

    private static void configureRunIdeaTask(@NotNull Project project) {
        Utils.info(project, "Configuring run IDE task")
        project.tasks.create(RUN_IDE_TASK_NAME, RunIdeTask).with { RunIdeTask task ->
            task.group = GROUP_NAME
            task.description = "Runs Intellij IDEA with installed plugin."
            task.dependsOn(PREPARE_SANDBOX_TASK_NAME)
        }
    }

    private static void configureRunIdeaForUiTestsTask(@NotNull Project project) {
        Utils.info(project, "Configuring run IDE for ui tests task")
        project.tasks.create(RUN_IDE_FOR_UI_TESTS_TASK_NAME, RunIdeForUiTestTask).with { RunIdeForUiTestTask task ->
            task.group = GROUP_NAME
            task.description = "Runs Intellij IDEA with installed plugin and robot-server plugin for ui tests."
            task.dependsOn(PREPARE_UI_TESTING_SANDBOX_TASK_NAME)
        }
    }

    private static void configureBuildSearchableOptionsTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring build searchable options task")
        project.tasks.create(BUILD_SEARCHABLE_OPTIONS_TASK_NAME, BuildSearchableOptionsTask).with { BuildSearchableOptionsTask task ->
            task.group = GROUP_NAME
            task.description = "Builds searchable options for plugin."
            task.args(["$project.buildDir/$SEARCHABLE_OPTIONS_DIR_NAME", "true"])
            task.outputs.dir("$project.buildDir/$SEARCHABLE_OPTIONS_DIR_NAME")
            task.dependsOn(PREPARE_SANDBOX_TASK_NAME)
            task.onlyIf {
                def number = Utils.ideBuildNumber(Utils.ideSdkDirectory(project, extension))
                VersionNumber.parse(number[number.indexOf('-') + 1..-1]) >= VersionNumber.parse("191.2752")
            }
        }
    }

    private static void prepareConventionMappingsForRunIdeTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension,
                                                               @NotNull RunIdeBase task, @NotNull String prepareSandBoxTaskName) {
        def prepareSandboxTask = project.tasks.findByName(prepareSandBoxTaskName) as PrepareSandboxTask
        task.conventionMapping("ideDirectory", { Utils.ideSdkDirectory(project, extension) })
        task.conventionMapping("requiredPluginIds", { Utils.getPluginIds(project) })
        task.conventionMapping("configDirectory", { project.file(prepareSandboxTask.getConfigDirectory()) })
        task.conventionMapping("pluginsDirectory", { prepareSandboxTask.getDestinationDir() })
        task.conventionMapping("systemDirectory", {
            project.file("${Utils.stringInput(extension.sandboxDirectory)}/system")
        })
        task.conventionMapping("autoReloadPlugins", {
            def number = Utils.ideBuildNumber(Utils.ideSdkDirectory(project, extension))
            VersionNumber.parse(number[number.indexOf('-') + 1..-1]) >= VersionNumber.parse("202.0")
        })
        task.conventionMapping("executable", {
            def jbrResolver = new JbrResolver(project, task)
            def jbrVersion = task.getJbrVersion() ?: task.getJbreVersion()
            if (jbrVersion != null) {
                def jbr = jbrResolver.resolve(jbrVersion)
                if (jbr != null) {
                    return jbr.javaExecutable
                }
                Utils.warn(task, "Cannot resolve JBR $jbrVersion. Falling back to builtin JBR.")
            }
            def builtinJbrVersion = Utils.getBuiltinJbrVersion(Utils.ideSdkDirectory(project, extension))
            if (builtinJbrVersion != null) {
                def builtinJbr = jbrResolver.resolve(builtinJbrVersion)
                if (builtinJbr != null) {
                    return builtinJbr.javaExecutable
                }
                Utils.warn(task, "Cannot resolve builtin JBR $builtinJbrVersion. Falling local Java.")
            }
            if (extension.alternativeIdePath) {
                def jbrPath = OperatingSystem.current().isMacOsX() ? "jbr/Contents/Home/bin/java" : "jbr/bin/java"
                def java = new File(Utils.ideaDir(extension.alternativeIdePath), jbrPath)
                if (java.exists()) {
                    return java.absolutePath
                }
                Utils.warn(task, "Cannot resolve JBR at $java.absolutePath. Falling back to current JVM.")
            }
            return Jvm.current().javaExecutable.absolutePath
        })
    }

    private static void configureJarSearchableOptionsTask(@NotNull Project project) {
        Utils.info(project, "Configuring jar searchable options task")
        project.tasks.create(JAR_SEARCHABLE_OPTIONS_TASK_NAME, JarSearchableOptionsTask).with {
            group = GROUP_NAME
            description = "Jars searchable options."
            if (VersionNumber.parse(project.gradle.gradleVersion) >= VersionNumber.parse("5.1")) {
                archiveBaseName.set('lib/searchableOptions')
                destinationDirectory.set(project.layout.buildDirectory.dir("libsSearchableOptions"))
            } else {
                conventionMapping('baseName', { 'lib/searchableOptions' })
                destinationDir = new File(project.buildDir, "libsSearchableOptions")
            }

            dependsOn(BUILD_SEARCHABLE_OPTIONS_TASK_NAME)
            onlyIf { new File(project.buildDir, SEARCHABLE_OPTIONS_DIR_NAME).isDirectory() }
        }
    }

    private static void configureInstrumentation(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring compile tasks")
        project.sourceSets.all { SourceSet sourceSet ->
            def instrumentTask = project.tasks.create(sourceSet.getTaskName('instrument', 'code'), IntelliJInstrumentCodeTask)
            instrumentTask.sourceSet = sourceSet
            instrumentTask.with {
                dependsOn sourceSet.classesTaskName
                onlyIf { extension.instrumentCode }
                conventionMapping('compilerVersion', {
                    def version = extension.version ?: DEFAULT_IDEA_VERSION
                    if (!extension.localPath && version && version.endsWith('-SNAPSHOT')) {
                        if (extension.type == 'CL') {
                            return "CLION-$version".toString()
                        }
                        if (extension.type == 'RD') {
                            return "RIDER-$version".toString()
                        }
                        if (extension.type == 'PY' || extension.type == 'PC') {
                            return "PYCHARM-$version".toString()
                        }
                        return version
                    }
                    return IdeVersion.createIdeVersion(ideaDependency.buildNumber).asStringWithoutProductCode()
                })
                conventionMapping('ideaDependency', { extension.ideaDependency })
                conventionMapping('javac2', {
                    def javac2 = project.file("$extension.ideaDependency.classes/lib/javac2.jar")
                    if (javac2?.exists()) {
                        return javac2
                    }
                })
                conventionMapping('outputDir', {
                    def output = sourceSet.output
                    def classesDir = output.classesDirs.first()
                    new File(classesDir.parentFile, "${sourceSet.name}-instrumented")
                })
            }

            // A dedicated task ensures that sources substitution is always run,
            // even when the instrumentCode task is up-to-date.
            def updateTask = project.tasks.create('post' + instrumentTask.name.capitalize())
            updateTask.with {
                dependsOn instrumentTask
                onlyIf { extension.instrumentCode }
                doLast {
                    // Set the classes dir to the new one with the instrumented classes
                    sourceSet.output.classesDirs.from = instrumentTask.outputDir
                }
            }

            // Ensure that our task is invoked when the source set is built
            sourceSet.compiledBy(updateTask)
        }
    }

    private static void configureTestTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        Utils.info(project, "Configuring tests tasks")
        project.tasks.withType(Test).each { task ->
            def configDirectory = project.file("${Utils.stringInput(extension.sandboxDirectory)}/config-test")
            def systemDirectory = project.file("${Utils.stringInput(extension.sandboxDirectory)}/system-test")
            def pluginsDirectory = project.file("${Utils.stringInput(extension.sandboxDirectory)}/plugins-test")
            task.enableAssertions = true
            def pluginIds = Utils.getPluginIds(project)
            task.systemProperties(Utils.getIdeaSystemProperties(configDirectory, systemDirectory, pluginsDirectory, pluginIds))


            // appClassLoader should be used for user's plugins. Otherwise, classes it won't be possible to use
            // its classes of application components or services in tests: class loaders will be different for
            // classes references by test code and for classes loaded by the platform (pico container).
            //
            // The proper way to handle that is to substitute Gradle's test class-loader and teach it
            // to understand PluginClassLoaders. Unfortunately, I couldn't find a way to do that.
            task.systemProperty("idea.use.core.classloader.for.plugin.path", "true")
            // the same as previous – setting appClassLoader but outdated. Works for part of 203 builds.
            task.systemProperty("idea.use.core.classloader.for", pluginIds.join(","))

            task.outputs.dir(systemDirectory)
            task.outputs.dir(configDirectory)

            PrepareSandboxTask prepareTestingSandboxTask = project.getTasksByName(PREPARE_TESTING_SANDBOX_TASK_NAME, false).find() as PrepareSandboxTask
            task.inputs.files(prepareTestingSandboxTask)

            task.doFirst {
                task.jvmArgs = Utils.getIdeJvmArgs(task, task.jvmArgs, Utils.ideSdkDirectory(project, extension))
                task.classpath += project.files(
                        "$extension.ideaDependency.classes/lib/resources.jar",
                        "$extension.ideaDependency.classes/lib/idea.jar"
                )

                // since 193 plugins from classpath are loaded before plugins from plugins directory
                // to handle this, use plugin.path property as task's the very first source of plugins
                // we cannot do this for IDEA < 193, as plugins from plugin.path can be loaded twice
                def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
                if (ideVersion.baselineVersion >= 193) {
                    task.systemProperty(PLUGIN_PATH, pluginsDirectory.listFiles().collect { it.path }.join("$File.pathSeparator,"))
                }
            }
        }
    }

    private static void configureBuildPluginTask(@NotNull Project project) {
        Utils.info(project, "Configuring building plugin task")
        def prepareSandboxTask = project.tasks.findByName(PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
        def jarSearchableOptionsTask = project.tasks.findByName(JAR_SEARCHABLE_OPTIONS_TASK_NAME) as Jar
        Zip zip = project.tasks.create(BUILD_PLUGIN_TASK_NAME, Zip).with {
            description = "Bundles the project as a distribution."
            group = GROUP_NAME
            from { "${prepareSandboxTask.getDestinationDir()}/${prepareSandboxTask.getPluginName()}" }
            into { prepareSandboxTask.getPluginName() }

            def searchableOptionsJar = VersionNumber.parse(project.gradle.gradleVersion) >= VersionNumber.parse("5.1")
                    ? jarSearchableOptionsTask.archiveFile : { jarSearchableOptionsTask.archivePath }
            from(searchableOptionsJar) { into 'lib' }
            dependsOn(JAR_SEARCHABLE_OPTIONS_TASK_NAME)
            if (VersionNumber.parse(project.gradle.gradleVersion) >= VersionNumber.parse("5.1")) {
                archiveBaseName.set(project.provider { prepareSandboxTask.getPluginName() })
            } else {
                conventionMapping('baseName', { prepareSandboxTask.getPluginName() })
            }
            it
        }
        Configuration archivesConfiguration = project.configurations.getByName(Dependency.ARCHIVES_CONFIGURATION)
        if (archivesConfiguration) {
            ArchivePublishArtifact zipArtifact = new ArchivePublishArtifact(zip)
            archivesConfiguration.getArtifacts().add(zipArtifact)
            project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(zipArtifact)
            project.getComponents().add(new IntelliJPluginLibrary())
        }
    }

    private static void configurePublishPluginTask(@NotNull Project project) {
        Utils.info(project, "Configuring publish plugin task")
        project.tasks.create(PUBLISH_PLUGIN_TASK_NAME, PublishTask).with {
            group = GROUP_NAME
            description = "Publish plugin distribution on plugins.jetbrains.com."
            conventionMapping('distributionFile', { resolveDistributionFile(project) })
            dependsOn { project.getTasksByName(BUILD_PLUGIN_TASK_NAME, false) }
            dependsOn { project.getTasksByName(VERIFY_PLUGIN_TASK_NAME, false) }
        }
    }

    private static void configureProcessResources(@NotNull Project project) {
        Utils.info(project, "Configuring resources task")
        def processResourcesTask = project.tasks.findByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME) as ProcessResources
        if (processResourcesTask) {
            processResourcesTask.from(project.tasks.findByName(PATCH_PLUGIN_XML_TASK_NAME)) {
                into("META-INF")
                duplicatesStrategy = DuplicatesStrategy.INCLUDE
            }
        }
    }

    private static File resolveDistributionFile(@NotNull Project project) {
        def buildPluginTask = project.tasks.findByName(BUILD_PLUGIN_TASK_NAME) as Zip
        def distributionFile =
                VersionNumber.parse(project.gradle.gradleVersion) >= VersionNumber.parse("5.1")
                        ? buildPluginTask?.archiveFile?.getOrNull()?.asFile : buildPluginTask.archivePath
        return distributionFile?.exists() ? distributionFile : null
    }
}
