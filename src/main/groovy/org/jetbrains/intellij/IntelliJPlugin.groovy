package org.jetbrains.intellij

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class IntelliJPlugin implements Plugin<Project> {
    public static final GROUP_NAME = "intellij"
    public static final EXTENSION_NAME = "intellij"
    public static final LOG = Logging.getLogger(IntelliJPlugin)

    private static final CONFIGURATION_NAME = "intellij"
    private static final String DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"

    @Override
    def void apply(Project project) {
        project.getPlugins().apply(JavaPlugin)
        def intellijExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension)
        intellijExtension.with {
            plugins = []
            version = DEFAULT_IDEA_VERSION
            pluginName = project.name
            sandboxDirectory = new File(project.buildDir, "idea-sandbox").absolutePath
            instrumentCode = true
            updateSinceUntilBuild = true
        }
        configurePlugin(project, intellijExtension)
    }

    private static def configurePlugin(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        project.afterEvaluate {
            LOG.info("Preparing IntelliJ IDEA dependency task")
            configureIntelliJDependency(it, extension)
            configurePluginDependencies(it, extension)
            configureInstrumentTask(it, extension)
            configureTestTasks(it, extension)
            if (!Utils.sourcePluginXmlFiles(it).isEmpty()) {
                configurePatchPluginXmlTask(it)
                configurePrepareSandboxTask(it)
                configureRunIdeaTask(it)
                configureBuildPluginTask(it, extension)
            } else {
                LOG.warn("File not found: plugin.xml. IntelliJ specific tasks will be unavailable.")
            }
        }
    }

    private static void configureIntelliJDependency(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        def configuration = project.configurations.create(CONFIGURATION_NAME).setVisible(false)
                .setDescription("The IntelliJ IDEA Community Edition artifact to be used for this project.")
        LOG.info("Adding IntelliJ IDEA repository")
        def version = extension.version
        def releaseType = version.contains('SNAPSHOT') ? 'snapshots' : 'releases'
        project.repositories.maven { it.url = "https://www.jetbrains.com/intellij-repository/${releaseType}" }
        LOG.info("Adding IntelliJ IDEA dependency")
        project.dependencies.add(configuration.name, "com.jetbrains.intellij.idea:ideaIC:${version}")
        LOG.info("IDEA zip: " + configuration.singleFile.path)
        extension.ideaDirectory = ideaDirectory(project, configuration)
        extension.ideaSourcesFile = ideaSourcesFile(project, configuration)
    }

    private static void configurePluginDependencies(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        def moduleName = createIvyRepo(project, extension)
        def version = extension.version

        project.repositories.ivy { repo ->
            repo.url = extension.ideaDirectory
            repo.artifactPattern("${extension.ideaDirectory.path}/com.jetbrains/${moduleName}/${version}/[artifact]-${project.name}.[ext]") // ivy xml
            repo.artifactPattern("${extension.ideaDirectory.path}/[artifact].[ext]") // idea libs

            def toolsJar = Jvm.current().toolsJar
            if (toolsJar != null) {
                repo.artifactPattern("${toolsJar.parent}/[artifact].[ext]") // java libs
            }
            if (extension.ideaSourcesFile != null) { // sources
                repo.artifactPattern("${extension.ideaSourcesFile.parent}/[artifact]-${version}-[classifier].[ext]")
            }
        }
        project.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: moduleName, version: version, configuration: 'compile'
        ])
        project.dependencies.add(JavaPlugin.RUNTIME_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: moduleName, version: version, configuration: 'runtime'
        ])
    }

    @Nullable
    private static File ideaSourcesFile(@NotNull Project project, @NotNull Configuration configuration) {
        if (!System.properties.'do.not.load.idea.sources') {
            Collection<ComponentIdentifier> components = new ArrayList<>()
            configuration.getResolvedConfiguration().getLenientConfiguration().getArtifacts(Specs.SATISFIES_ALL).each {
                def id = it.getModuleVersion().getId()
                components.add(new DefaultModuleComponentIdentifier(id.getGroup(), id.getName(), id.getVersion()))
            }

            ArtifactResolutionQuery query = project.dependencies.createArtifactResolutionQuery();
            query.forComponents(components);
            query.withArtifacts(JvmLibrary, SourcesArtifact);
            for (def component : query.execute().getResolvedComponents()) {
                for (def artifact : component.getArtifacts(SourcesArtifact)) {
                    if (artifact instanceof ResolvedArtifactResult) {
                        return ((ResolvedArtifactResult) artifact).getFile();
                    }
                }
            }
        }
        return null
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

    private static void configureRunIdeaTask(@NotNull Project project) {
        LOG.info("Configuring run IntelliJ task")
        project.tasks.create(RunIdeaTask.NAME, RunIdeaTask)
                .dependsOn(project.getTasksByName(PrepareSandboxTask.NAME, false))
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
            it.systemProperty("java.system.class.loader", "com.intellij.util.lang.UrlClassLoader")
            it.jvmArgs = Utils.getIdeaJvmArgs(it, it.jvmArgs, extension)

            def toolsJar = Jvm.current().getToolsJar()
            if (toolsJar != null) it.classpath += project.files(toolsJar)
            it.classpath += project.files("${extension.ideaDirectory}/lib/resources.jar",
                    "${extension.ideaDirectory}/lib/idea.jar");
        }
    }

    private static void configureBuildPluginTask(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring building IntelliJ IDEA plugin task")
        def prepareSandboxTask = project.tasks.findByName(PrepareSandboxTask.NAME) as PrepareSandboxTask
        project.tasks.create("buildPlugin", Zip).with {
            description = "Bundles the project as a distribution."
            group = GROUP_NAME
            baseName = extension.pluginName
            from("${prepareSandboxTask.destinationDir}/${extension.pluginName}")
            into(extension.pluginName)
            dependsOn(project.getTasksByName(PrepareSandboxTask.NAME, false))
            project.getTasksByName(LifecycleBasePlugin.ASSEMBLE_TASK_NAME, false)*.dependsOn(it)
        }
    }

    @NotNull
    private static File ideaDirectory(@NotNull Project project, @NotNull Configuration configuration) {
        File zipFile = configuration.singleFile
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
        def moduleName = "ideaIC"
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
            bundledPlugins.each { bundledPluginJars.include("plugins/${it}/lib/*.jar") }
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

        def parentDirectory = new File(extension.ideaDirectory, "com.jetbrains/${moduleName}/${extension.version}")
        parentDirectory.mkdirs()
        generator.writeTo(new File(parentDirectory, "ivy-${project.name}.xml"))
        return moduleName
    }
}
