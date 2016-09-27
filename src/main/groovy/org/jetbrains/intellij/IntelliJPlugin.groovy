package org.jetbrains.intellij

import com.intellij.structure.domain.IdeVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.dependency.PluginDependencyManager
import org.jetbrains.intellij.tasks.PublishTask

class IntelliJPlugin implements Plugin<Project> {
    public static final GROUP_NAME = "intellij"
    public static final EXTENSION_NAME = "intellij"
    public static final String DEFAULT_SANDBOX = 'idea-sandbox'
    public static final String PATCH_PLUGIN_XML_TASK_NAME = "patchPluginXml"
    public static final String PLUGIN_XML_DIR_NAME = "patchedPluginXmlFiles"
    public static final String PREPARE_SANDBOX_TASK_NAME = "prepareSandbox"
    public static final String PREPARE_TESTING_SANDBOX_TASK_NAME = "prepareTestingSandbox"
    public static final String BUILD_PLUGIN_TASK_NAME = "buildPlugin"
    public static final String PUBLISH_PLUGIN_TASK_NAME = "publishPlugin"


    public static final LOG = Logging.getLogger(IntelliJPlugin)
    public static final String DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"
    public static final String DEFAULT_INTELLIJ_REPO = 'https://www.jetbrains.com/intellij-repository'

    @Override
    void apply(Project project) {
        project.getPlugins().apply(JavaPlugin)
        def intellijExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension)
        intellijExtension.with {
            plugins = []
            version = DEFAULT_IDEA_VERSION
            type = 'IC'
            pluginName = project.name
            sandboxDirectory = new File(project.buildDir, DEFAULT_SANDBOX).absolutePath
            instrumentCode = true
            updateSinceUntilBuild = true
            sameSinceUntilBuild = false
            intellijRepo = DEFAULT_INTELLIJ_REPO
            downloadSources = true
            publish = new IntelliJPluginExtension.Publish()
        }
        configureTasks(project, intellijExtension)
    }

    private static def configureTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ IDEA gradle plugin")
        configurePatchPluginXmlTask(project, extension)
        configurePrepareSandboxTasks(project, extension)
        configureBuildPluginTask(project)
        configurePublishPluginTask(project, extension)
        project.afterEvaluate {
            configureInstrumentation(it, extension)
            configureIntellijDependency(it, extension)
            configurePluginDependencies(it, extension)
            if (Utils.sourcePluginXmlFiles(it)) {
                configureRunIdeaTask(it, extension)
            } else {
                LOG.warn("plugin.xml with 'idea-plugin' root is not found. IntelliJ specific tasks will be unavailable for :$project.name.")
            }
            configureTestTasks(it, extension)
        }
    }

    private static void configureIntellijDependency(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ IDEA dependency")
        def resolver = new IdeaDependencyManager(extension.intellijRepo ?: DEFAULT_INTELLIJ_REPO)
        def ideaDependency = resolver.resolve(project, extension.version, extension.type, extension.downloadSources)
        if (ideaDependency == null) {
            throw new BuildException("Failed to resolve IntelliJ IDEA ${extension.version}", null)
        }
        extension.ideaDependency = ideaDependency
        LOG.info("IntelliJ IDEA ${ideaDependency.buildNumber} is used for building")
        resolver.register(project, ideaDependency)

        def toolsJar = Jvm.current().toolsJar
        if (toolsJar) {
            project.dependencies.add(JavaPlugin.RUNTIME_CONFIGURATION_NAME, project.files(toolsJar))
        }
    }

    private static void configurePluginDependencies(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ IDEA plugin dependencies")
        def ideVersion = IdeVersion.createIdeVersion(extension.ideaDependency.buildNumber)
        def resolver = new PluginDependencyManager(project, extension.ideaDependency)
        extension.plugins.each {
            LOG.info("Configuring IntelliJ plugin $it")
            def (pluginId, pluginVersion, channel) = Utils.parsePluginDependencyString(it)
            if (!pluginId) {
                throw new BuildException("Failed to resolve plugin $it", null)
            }
            def plugin = resolver.resolve(pluginId, pluginVersion, channel)
            if (plugin == null) {
                throw new BuildException("Failed to resolve plugin $it", null)
            }
            if (ideVersion != null && !plugin.isCompatible(ideVersion)) {
                throw new BuildException("Plugin $it is not compatible to ${ideVersion.asString()}", null)
            }
            extension.pluginDependencies.add(plugin)
            resolver.register(project, plugin)
        }
    }

    private static void configurePatchPluginXmlTask(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring patch plugin.xml task")
        project.tasks.create(PATCH_PLUGIN_XML_TASK_NAME, PatchPluginXmlTask).with {
            group = GROUP_NAME
            description = "Patch plugin xml files with corresponding since/until build numbers and version attributes"
            conventionMapping('version', { project.version?.toString() })
            conventionMapping('pluginXmlFiles', { Utils.outPluginXmlFiles(project) })
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
            dependsOn { project.getTasks().withType(ProcessResources) }
        }
    }

    private static void configurePrepareSandboxTasks(@NotNull Project project,
                                                     @NotNull IntelliJPluginExtension extension) {
        def patchPluginXmlTask = project.tasks.findByName(PATCH_PLUGIN_XML_TASK_NAME) as PatchPluginXmlTask
        configurePrepareSandboxTask(project, extension, patchPluginXmlTask, false)
        configurePrepareSandboxTask(project, extension, patchPluginXmlTask, true)
    }

    private static void configurePrepareSandboxTask(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension,
                                                    @NotNull PatchPluginXmlTask patchPluginXmlTask,
                                                    boolean inTest) {
        LOG.info("Configuring prepare IntelliJ sandbox task")
        def taskName = inTest ? PREPARE_TESTING_SANDBOX_TASK_NAME : PREPARE_SANDBOX_TASK_NAME
        project.tasks.create(taskName, PrepareSandboxTask).with {
            group = GROUP_NAME
            description = "Prepare sandbox directory with installed plugin and its dependencies."
            conventionMapping('pluginName', { extension.pluginName })
            conventionMapping('destinationDir', { project.file(Utils.pluginsDir(extension.sandboxDirectory, inTest)) })
            conventionMapping('librariesToIgnore', { project.files(extension.ideaDependency.jarFiles) })
            conventionMapping('pluginDependencies', { extension.pluginDependencies })
            conventionMapping('patchedPluginXmlDirectory', { patchPluginXmlTask.getDestinationDir() })
            dependsOn(project.getTasksByName(JavaPlugin.CLASSES_TASK_NAME, false))
            dependsOn(project.getTasksByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, false))
            dependsOn(patchPluginXmlTask)
        }
    }

    private static void configureRunIdeaTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring run IntelliJ task")
        project.tasks.create(RunIdeaTask.NAME, RunIdeaTask).with {
            group = GROUP_NAME
            description = "Runs Intellij IDEA with installed plugin."
            dependsOn(project.getTasksByName(PREPARE_SANDBOX_TASK_NAME, false))
            outputs.dir(Utils.systemDir(extension, false))
            outputs.dir(Utils.configDir(extension.sandboxDirectory, false))
            outputs.upToDateWhen { false }
        }
    }

    private static void configureInstrumentation(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ compile tasks")
        def abstractCompileDependencies = { String taskName ->
            project.tasks.findByName(taskName).collect {
                it.taskDependencies.getDependencies(it).findAll { it instanceof AbstractCompile }
            }.flatten() as Collection<AbstractCompile>
        }
        abstractCompileDependencies(JavaPlugin.CLASSES_TASK_NAME).each {
            it.inputs.property("intellijIdeaDependency", extension.ideaDependency)
            it.doLast(new IntelliJInstrumentCodeAction(false))
        }
        abstractCompileDependencies(JavaPlugin.TEST_CLASSES_TASK_NAME).each {
            it.inputs.property("intellijIdeaDependency", extension.ideaDependency)
            it.doLast(new IntelliJInstrumentCodeAction(true))
        }
    }

    private static void configureTestTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ tests tasks")
        project.tasks.withType(Test).each {
            it.dependsOn(project.getTasksByName(PREPARE_TESTING_SANDBOX_TASK_NAME, false))
            it.enableAssertions = true
            it.systemProperties = Utils.getIdeaSystemProperties(project, it.systemProperties, extension, true)
            it.jvmArgs = Utils.getIdeaJvmArgs(it, it.jvmArgs, extension)
            it.classpath += project.files("$extension.ideaDependency.classes/lib/resources.jar",
                    "$extension.ideaDependency.classes/lib/idea.jar")
            it.outputs.dir(Utils.systemDir(extension, true))
            it.outputs.dir(Utils.configDir(extension.sandboxDirectory, true))
        }
    }

    private static void configureBuildPluginTask(@NotNull Project project) {
        LOG.info("Configuring building IntelliJ IDEA plugin task")
        def prepareSandboxTask = project.tasks.findByName(PREPARE_SANDBOX_TASK_NAME) as PrepareSandboxTask
        Zip zip = project.tasks.create(BUILD_PLUGIN_TASK_NAME, Zip).with {
            description = "Bundles the project as a distribution."
            group = GROUP_NAME
            from { "${prepareSandboxTask.getDestinationDir()}/${prepareSandboxTask.getPluginName()}" }
            into { prepareSandboxTask.getPluginName() }
            dependsOn(prepareSandboxTask)
            conventionMapping.map('baseName', { prepareSandboxTask.getPluginName() })
            it
        }
        ArchivePublishArtifact zipArtifact = new ArchivePublishArtifact(zip)
        Configuration runtimeConfiguration = project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME)
        runtimeConfiguration.getArtifacts().add(zipArtifact)
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(zipArtifact)
        project.getComponents().add(new IntelliJPluginLibrary())
    }

    private static void configurePublishPluginTask(@NotNull Project project,
                                                   @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring publishing IntelliJ IDEA plugin task")
        project.tasks.create(PUBLISH_PLUGIN_TASK_NAME, PublishTask).with {
            group = GROUP_NAME
            description = "Publish plugin distribution on plugins.jetbrains.com."
            conventionMapping('username', { extension.publish.username })
            conventionMapping('password', { extension.publish.password })
            conventionMapping('channels', { extension.publish.channels })
            conventionMapping('distributionFile', {
                def buildPluginTask = project.tasks.findByName(BUILD_PLUGIN_TASK_NAME) as Zip
                def distributionFile = buildPluginTask?.archivePath
                return distributionFile?.exists() ? distributionFile : null
            })
            dependsOn { project.getTasksByName(BUILD_PLUGIN_TASK_NAME, false) }
        }
    }
}
