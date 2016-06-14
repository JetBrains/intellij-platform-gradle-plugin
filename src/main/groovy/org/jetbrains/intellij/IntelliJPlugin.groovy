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
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.language.jvm.tasks.ProcessResources
import org.gradle.tooling.BuildException
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.dependency.IdeaDependencyManager
import org.jetbrains.intellij.dependency.PluginDependencyManager

class IntelliJPlugin implements Plugin<Project> {
    public static final GROUP_NAME = "intellij"
    public static final EXTENSION_NAME = "intellij"
    public static final String DEFAULT_SANDBOX = 'idea-sandbox'
    public static final String BUILD_PLUGIN_TASK_NAME = "buildPlugin"
    public static final LOG = Logging.getLogger(IntelliJPlugin)

    public static final String DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"
    public static final String DEFAULT_INTELLIJ_REPO = 'https://www.jetbrains.com/intellij-repository'

    @Override
    def void apply(Project project) {
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
        configurePlugin(project, intellijExtension)
    }

    private static def configurePlugin(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        project.afterEvaluate {
            LOG.info("Configuring IntelliJ IDEA gradle plugin")
            configureIntellijDependency(it, extension)
            configurePluginDependencies(it, extension)
            configureInstrumentTask(it, extension)
            if (Utils.sourcePluginXmlFiles(it)) {
                configurePatchPluginXmlTask(it)
                configurePrepareSandboxTask(it)
                configurePrepareTestsSandboxTask(it)
                configureRunIdeaTask(it, extension)
                configureBuildPluginTask(it, extension)
                configurePublishPluginTask(it)
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
        LOG.debug("IntelliJ IDEA ${ideaDependency.buildNumber} is used for building")
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
                throw new BuildException("Plugin $it is not compatible to ${ideVersion.asString(true, true)}", null)
            }
            extension.pluginDependencies.add(plugin)
            resolver.register(project, plugin)
        }
    }

    private static void configurePatchPluginXmlTask(@NotNull Project project) {
        LOG.info("Configuring patch plugin.xml task")
        def processResourcesTasks = project.tasks.withType(ProcessResources.class)
        def patchPluginXmlAction = new PatchPluginXmlAction(project)
        processResourcesTasks*.doLast(patchPluginXmlAction)
        processResourcesTasks*.inputs*.properties(patchPluginXmlAction.properties)
    }

    private static void configurePrepareSandboxTask(@NotNull Project project) {
        LOG.info("Configuring prepare IntelliJ sandbox task")
        project.tasks.create(PrepareSandboxTask.NAME, PrepareSandboxTask).with {
            group = GROUP_NAME
            description = "Creates a folder containing the plugins to run Intellij IDEA with."
            dependsOn(project.getTasksByName(JavaPlugin.CLASSES_TASK_NAME, false))
            dependsOn(project.getTasksByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, false))
        }
    }

    private static void configurePrepareTestsSandboxTask(@NotNull Project project) {
        LOG.info("Configuring prepare IntelliJ sandbox for tests task")
        project.tasks.create(PrepareTestsSandboxTask.NAME, PrepareTestsSandboxTask).with {
            group = GROUP_NAME
            description = "Creates a folder containing the plugins to run IntelliJ plugin tests with."
            dependsOn(project.getTasksByName(JavaPlugin.TEST_CLASSES_TASK_NAME, false))
            dependsOn(project.getTasksByName(JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME, false))
        }
    }

    private static void configureRunIdeaTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring run IntelliJ task")
        def task = project.tasks.create(RunIdeaTask.NAME, RunIdeaTask)
        task.group = GROUP_NAME
        task.description = "Runs Intellij IDEA with installed plugin."
        task.dependsOn(project.getTasksByName(PrepareSandboxTask.NAME, false))
        task.outputs.dir(Utils.systemDir(extension, false))
        task.outputs.dir(Utils.configDir(extension, false))
        task.outputs.upToDateWhen { false }
    }

    private static void configureInstrumentTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        if (!extension.instrumentCode) return
        LOG.info("Configuring IntelliJ compile tasks")
        project.tasks.findByName(JavaPlugin.CLASSES_TASK_NAME)*.doLast(new IntelliJInstrumentCodeAction(false))
        project.tasks.findByName(JavaPlugin.TEST_CLASSES_TASK_NAME)*.doLast(new IntelliJInstrumentCodeAction(true))
    }

    private static void configureTestTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ tests tasks")
        project.tasks.withType(Test).each {
            it.dependsOn(project.getTasksByName(PrepareTestsSandboxTask.NAME, false))
            it.enableAssertions = true
            it.systemProperties = Utils.getIdeaSystemProperties(project, it.systemProperties, extension, true)
            it.jvmArgs = Utils.getIdeaJvmArgs(it, it.jvmArgs, extension)
            it.classpath += project.files("$extension.ideaDependency.classes/lib/resources.jar",
                    "$extension.ideaDependency.classes/lib/idea.jar");
            it.outputs.dir(Utils.systemDir(extension, true))
            it.outputs.dir(Utils.configDir(extension, true))
        }
    }

    private static void configureBuildPluginTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring building IntelliJ IDEA plugin task")
        def prepareSandboxTask = project.tasks.findByName(PrepareSandboxTask.NAME) as PrepareSandboxTask
        Zip zip = project.tasks.create(BUILD_PLUGIN_TASK_NAME, Zip)
        zip.with {
            description = "Bundles the project as a distribution."
            group = GROUP_NAME
            baseName = extension.pluginName
            from("$prepareSandboxTask.destinationDir/$extension.pluginName")
            into(extension.pluginName)
            dependsOn(prepareSandboxTask)
        }

        ArchivePublishArtifact zipArtifact = new ArchivePublishArtifact(zip);
        Configuration runtimeConfiguration = project.getConfigurations().getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME);
        runtimeConfiguration.getArtifacts().add(zipArtifact);
        project.getExtensions().getByType(DefaultArtifactPublicationSet.class).addCandidate(zipArtifact);
        project.getComponents().add(new IntelliJPluginLibrary());
    }

    private static void configurePublishPluginTask(@NotNull Project project) {
        LOG.info("Configuring publishing IntelliJ IDEA plugin task")
        project.tasks.create(PublishTask.NAME, PublishTask).with {
            group = GROUP_NAME
            description = "Publish plugin distribution on plugins.jetbrains.com."
            dependsOn(project.getTasksByName(BUILD_PLUGIN_TASK_NAME, false))
        }
    }
}
