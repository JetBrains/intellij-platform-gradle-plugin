package org.jetbrains.intellij

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.annotations.NotNull

class IntelliJPlugin implements Plugin<Project> {
    public static final GROUP_NAME = "intellij"
    public static final EXTENSION_NAME = "intellij"
    public static final String DEFAULT_SANDBOX = 'idea-sandbox'
    public static final String BUILD_PLUGIN_TASK_NAME = "buildPlugin"
    public static final LOG = Logging.getLogger(IntelliJPlugin)

    private static final CONFIGURATION_NAME = "intellij"
    private static final SOURCES_CONFIGURATION_NAME = "intellij-sources"
    private static final String DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"
    private static final String DEFAULT_INTELLIJ_REPO = 'https://www.jetbrains.com/intellij-repository'

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
            intellijRepo = DEFAULT_INTELLIJ_REPO
            downloadSources = true
            publish = new IntelliJPluginExtension.Publish()
        }
        configurePlugin(project, intellijExtension)
    }

    private static def configurePlugin(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        project.afterEvaluate {
            LOG.info("Preparing IntelliJ IDEA dependency task")
            configureIntelliJDependency(it, extension)
            configurePluginDependencies(it, extension)
            configureInstrumentTask(it, extension)
            if (Utils.sourcePluginXmlFiles(it)) {
                configurePatchPluginXmlTask(it)
                configurePrepareSandboxTask(it)
                configureRunIdeaTask(it, extension)
                configureBuildPluginTask(it, extension)
                configurePublishPluginTask(it)
            } else {
                LOG.warn("plugin.xml with 'idea-plugin' root is not found. IntelliJ specific tasks will be unavailable for :$project.name.")
            }
            configureTestTasks(it, extension)
        }
    }

    private static void configureIntelliJDependency(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        def configuration = project.configurations.create(CONFIGURATION_NAME).setVisible(false)
                .setDescription("The IntelliJ IDEA distribution artifact to be used for this project.")
        LOG.info("Adding IntelliJ IDEA repository")
        def baseUrl = extension.intellijRepo ?: DEFAULT_INTELLIJ_REPO
        def releaseType = extension.version.contains('SNAPSHOT') ? 'snapshots' : 'releases'
        project.repositories.maven {
            it.url = "${baseUrl}/$releaseType"
        }
        LOG.info("Adding IntelliJ IDEA dependency")
        project.dependencies.add(configuration.name, "com.jetbrains.intellij.idea:idea$extension.type:$extension.version")
        extension.ideaDirectory = ideaDirectory(project, configuration)

        if (extension.downloadSources) {
            def sourcesConfiguration = project.configurations.create(SOURCES_CONFIGURATION_NAME).setVisible(false)
                    .setDescription("The IntelliJ IDEA Community Edition source artifact to be used for this project.")
            LOG.info("Adding IntelliJ IDEA sources repository")
            project.dependencies.add(sourcesConfiguration.name, "com.jetbrains.intellij.idea:ideaIC:$extension.version:sources@jar")
            def sourcesFiles = sourcesConfiguration.files
            if (sourcesFiles.size() == 1) {
                def sourcesFile = sourcesFiles.first()
                LOG.info("IDEA sources jar: " + sourcesFile.path)
                extension.ideaSourcesFile = sourcesFile
            } else {
                LOG.warn("Cannot attach IDEA sources. Found files: " + sourcesFiles)
            }
        }
    }

    private static void configurePluginDependencies(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        def moduleName = createIvyRepo(project, extension)
        def version = extension.version

        project.repositories.ivy { repo ->
            repo.url = extension.ideaDirectory
            repo.artifactPattern("$extension.ideaDirectory.path/com.jetbrains/$moduleName/$version/[artifact]-$project.name.[ext]") // ivy xml
            repo.artifactPattern("$extension.ideaDirectory.path/[artifact].[ext]") // idea libs

            def toolsJar = Jvm.current().toolsJar
            if (toolsJar != null) {
                repo.artifactPattern("$toolsJar.parent/[artifact].[ext]") // java libs
            }
            if (extension.ideaSourcesFile != null) { // sources
                repo.artifactPattern("$extension.ideaSourcesFile.parent/[artifact]-$version-[classifier].[ext]")
            }
        }
        project.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: moduleName, version: version, configuration: 'compile'
        ])
        project.dependencies.add(JavaPlugin.RUNTIME_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: moduleName, version: version, configuration: 'runtime'
        ])
    }

    private static void configurePatchPluginXmlTask(@NotNull Project project) {
        LOG.info("Configuring patch plugin.xml task")
        PatchPluginXmlTask task = project.tasks.create(PatchPluginXmlTask.NAME, PatchPluginXmlTask);
        task.dependsOn(project.getTasksByName(JavaPlugin.CLASSES_TASK_NAME, false))
        project.getTasksByName(JavaPlugin.JAR_TASK_NAME, false)*.dependsOn(task)
    }

    private static void configurePrepareSandboxTask(@NotNull Project project) {
        LOG.info("Configuring prepare IntelliJ sandbox task")
        project.tasks.create(PrepareSandboxTask.NAME, PrepareSandboxTask)
                .dependsOn(project.getTasksByName(PatchPluginXmlTask.NAME, false))
    }

    private static void configureRunIdeaTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring run IntelliJ task")
        def task = project.tasks.create(RunIdeaTask.NAME, RunIdeaTask)
        task.name = RunIdeaTask.NAME
        task.group = GROUP_NAME
        task.description = "Runs Intellij IDEA with installed plugin."
        task.dependsOn(project.getTasksByName(PrepareSandboxTask.NAME, false))
        task.outputs.files(Utils.systemDir(extension, false), Utils.configDir(extension, false))
        task.outputs.upToDateWhen { false }
    }

    private static void configureInstrumentTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        if (!extension.instrumentCode) return
        LOG.info("Configuring IntelliJ compile tasks")
        project.tasks.withType(JavaCompile)*.doLast(new IntelliJInstrumentCodeAction())
    }

    private static void configureTestTasks(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring IntelliJ tests tasks")
        project.tasks.withType(Test).each {
            it.dependsOn(project.getTasksByName(PrepareSandboxTask.NAME, false))
            it.enableAssertions = true
            it.systemProperties = Utils.getIdeaSystemProperties(project, it.systemProperties, extension, true)
            it.jvmArgs = Utils.getIdeaJvmArgs(it, it.jvmArgs, extension)

            def toolsJar = Jvm.current().getToolsJar()
            if (toolsJar != null) it.classpath += project.files(toolsJar)
            it.classpath += project.files("$extension.ideaDirectory/lib/resources.jar",
                    "$extension.ideaDirectory/lib/idea.jar");

            it.outputs.files(Utils.systemDir(extension, true), Utils.configDir(extension, true))
        }
    }

    private static void configureBuildPluginTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring building IntelliJ IDEA plugin task")
        def prepareSandboxTask = project.tasks.findByName(PrepareSandboxTask.NAME) as PrepareSandboxTask
        project.tasks.create(BUILD_PLUGIN_TASK_NAME, Zip).with {
            description = "Bundles the project as a distribution."
            group = GROUP_NAME
            baseName = extension.pluginName
            from("$prepareSandboxTask.destinationDir")
            into(extension.pluginName)
            dependsOn(prepareSandboxTask)
            project.getTasksByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, false)*.dependsOn(it)
        }
    }

    private static void configurePublishPluginTask(@NotNull Project project) {
        LOG.info("Configuring publishing IntelliJ IDEA plugin task")
        project.tasks.create(PublishTask.NAME, PublishTask)
                .dependsOn(project.getTasksByName(BUILD_PLUGIN_TASK_NAME, false))
    }

    @NotNull
    private static File ideaDirectory(@NotNull Project project, @NotNull Configuration configuration) {
        File zipFile = configuration.singleFile
        LOG.info("IDEA zip: " + zipFile.path)
        def directoryName = zipFile.name - ".zip"
        def cacheDirectory = new File(zipFile.parent, directoryName)
        def markerFile = new File(cacheDirectory, "markerFile")
        if (!markerFile.exists()) {
            if (cacheDirectory.exists()) cacheDirectory.deleteDir()
            cacheDirectory.mkdir()
            LOG.info("Unzipping idea")
            project.copy {
                it.from(project.zipTree(zipFile))
                it.into(cacheDirectory)
            }
            markerFile.createNewFile()
            LOG.info("Unzipped")
        }
        return cacheDirectory;
    }

    private static def createIvyRepo(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        def moduleName = "idea$extension.type"
        def generator = new IvyDescriptorFileGenerator(new DefaultIvyPublicationIdentity("com.jetbrains", moduleName, extension.version))
        generator.addConfiguration(new DefaultIvyConfiguration("compile"))
        generator.addConfiguration(new DefaultIvyConfiguration("sources"))
        generator.addConfiguration(new DefaultIvyConfiguration("runtime"))

        def ideaLibJars = project.fileTree(extension.ideaDirectory)
        ideaLibJars.include("lib*/*.jar")
        ideaLibJars.files.each {
            generator.addArtifact(Utils.createDependency(it, "compile", extension.ideaDirectory))
            extension.intellijFiles.add(it)
            extension.runClasspath.add(it)
        }

        def bundledPlugins = extension.plugins
        if (bundledPlugins.length > 0) {
            def bundledPluginJars = project.fileTree(extension.ideaDirectory)
            bundledPlugins.each { bundledPluginJars.include("plugins/$it/lib/*.jar") }
            bundledPluginJars.files.each {
                generator.addArtifact(Utils.createDependency(it, "compile", extension.ideaDirectory))
                extension.intellijFiles.add(it)
            }
        }

        def toolsJar = Jvm.current().toolsJar
        if (toolsJar != null) {
            generator.addArtifact(Utils.createDependency(toolsJar, "runtime", toolsJar.parentFile))
            extension.intellijFiles.add(toolsJar)
            extension.runClasspath.add(toolsJar)
        }

        if (extension.ideaSourcesFile != null) {
            // source dependency must be named in the same way as module name
            def artifact = new DefaultIvyArtifact(extension.ideaSourcesFile, moduleName, "jar", "sources", "sources")
            artifact.conf = "sources"
            generator.addArtifact(artifact)
            extension.intellijFiles.add(extension.ideaSourcesFile)
        }

        def parentDirectory = new File(extension.ideaDirectory, "com.jetbrains/$moduleName/$extension.version")
        parentDirectory.mkdirs()
        generator.writeTo(new File(parentDirectory, "ivy-${project.name}.xml"))
        return moduleName
    }
}
