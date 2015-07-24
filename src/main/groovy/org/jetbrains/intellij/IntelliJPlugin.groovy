package org.jetbrains.intellij
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.query.ArtifactResolutionQuery
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.api.specs.Specs
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.bundling.Zip
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.jvm.JvmLibrary
import org.gradle.language.base.artifact.SourcesArtifact
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

class IntelliJPlugin implements Plugin<Project> {
    public static final GROUP_NAME = "intellij"

    private static final LOG = Logging.getLogger(IntelliJPlugin.class)
    private static final EXTENSION_NAME = "intellij"
    private static final CONFIGURATION_NAME = "intellij"
    private static final String DEFAULT_IDEA_VERSION = "LATEST-EAP-SNAPSHOT"

    @Override
    def void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class)
        def intellijExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension.class)
        intellijExtension.with {
            plugins = []
            version = DEFAULT_IDEA_VERSION
        }
        configurePlugin(project, intellijExtension)
        configureSetPluginVersionTask(project)
    }

    private static def configurePlugin(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        project.afterEvaluate {
            LOG.info("Preparing IntelliJ IDEA dependency task")
            def (ideaDirectory, sourcesFile) = configureIntelliJDependency(project, extension.version)
            def intellijFiles = configurePluginDependencies(project, extension, ideaDirectory, sourcesFile)
            configureBuildPluginTask(project, intellijFiles, extension)
        }
    }

    private static Tuple configureIntelliJDependency(@NotNull Project project, @NotNull String version) {
        def configuration = project.configurations.create(CONFIGURATION_NAME).setVisible(false)
                .setDescription("The IntelliJ IDEA Community Edition artifact to be used for this project.")
        LOG.info("Adding IntelliJ IDEA repository")
        def releaseType = version.contains('SNAPSHOT') ? 'snapshots' : 'releases'
        project.repositories.maven { it.url = "https://www.jetbrains.com/intellij-repository/${releaseType}" }
        LOG.info("Adding IntelliJ IDEA dependency")
        project.dependencies.add(configuration.name, "com.jetbrains.intellij.idea:ideaIC:${version}")
        LOG.info("IDEA zip: " + configuration.singleFile.path)
        return new Tuple(ideaDirectory(project, configuration.singleFile), ideaSourcesFile(project, configuration))
    }

    private static Collection<File> configurePluginDependencies(@NotNull Project project,
                                                                @NotNull IntelliJPluginExtension extension,
                                                                @NotNull File ideaDirectory,
                                                                @Nullable File sourcesFile) {

        def intellijFile = new HashSet<File>()
        def version = extension.version
        def moduleName = createIvyRepo(project, extension.plugins, ideaDirectory, sourcesFile, intellijFile, version)

        project.repositories.ivy { repo ->
            repo.url = ideaDirectory
            repo.artifactPattern("${ideaDirectory.path}/com.jetbrains/${moduleName}/${version}/[artifact]-${project.name}.[ext]") // ivy xml
            repo.artifactPattern("${ideaDirectory.path}/[artifact].[ext]") // idea libs

            def javaHomeLib = javaHomeLib()
            if (javaHomeLib != null) {
                repo.artifactPattern("${javaHomeLib}/[artifact].[ext]") // java libs
            }
            if (sourcesFile != null) {
                repo.artifactPattern("${sourcesFile.parent}/[artifact]-${version}-[classifier].[ext]") // sources
            }
        }
        project.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: moduleName, version: version, configuration: 'compile'
        ])
        project.dependencies.add(JavaPlugin.RUNTIME_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: moduleName, version: version, configuration: 'runtime'
        ])
        return intellijFile
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
            query.withArtifacts(JvmLibrary.class, SourcesArtifact.class);
            for (def component : query.execute().getResolvedComponents()) {
                for (def artifact : component.getArtifacts(SourcesArtifact.class)) {
                    if (artifact instanceof ResolvedArtifactResult) {
                        return ((ResolvedArtifactResult) artifact).getFile();
                    }
                }
            }
        }
        return null
    }

    private static void configureSetPluginVersionTask(@NotNull Project project) {
        project.afterEvaluate {
            JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention.class);
            SourceSet mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            PluginVersionTask task = project.tasks.create(PluginVersionTask.NAME, PluginVersionTask.class);
            task.sourceSet = mainSourceSet;
            task.dependsOn(project.getTasksByName(JavaPlugin.CLASSES_TASK_NAME, false))
            project.getTasksByName(JavaPlugin.JAR_TASK_NAME, false)*.dependsOn(task)
        }
    }

    private static void configureBuildPluginTask(@NotNull Project project,
                                                 @NotNull Collection<File> intellijFiles,
                                                 @NotNull IntelliJPluginExtension extension) {
        LOG.info("Configuring building IntelliJ IDEA plugin task")
        def task = project.tasks.create("buildPlugin", Zip.class)
        task.description = "Bundles the project as a distribution."
        task.group = GROUP_NAME
        task.baseName = project.name
        if (extension.archiveName != null && !extension.archiveName.isEmpty()) {
            task.archiveName = "${extension.archiveName}.${task.extension}"
        }
        def baseDir = { task.archiveName - ".${task.extension}" + "/lib" }
        task.into(baseDir).from {
            def files = new HashSet<>()
            project.configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).each {
                if (!intellijFiles.contains(it)) files.add(it)
            }
            if (!files.isEmpty()) files.add(project.jar)
            files
        }
        task.dependsOn(project.getTasksByName(JavaPlugin.JAR_TASK_NAME, true))
    }

    @NotNull
    private static File ideaDirectory(@NotNull Project project, @NotNull File zipFile) {
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

    private static def createIvyRepo(@NotNull Project project, @NotNull String[] bundledPlugins,
                                     @NotNull File ideaDirectory, @Nullable File ideaSourcesFile,
                                     @NotNull Set<File> filesToIgnoreWhileBuilding, @NotNull String version) {
        def moduleName = "ideaIC"
        def generator = new IvyDescriptorFileGenerator(new DefaultIvyPublicationIdentity("com.jetbrains", moduleName, version))
        generator.addConfiguration(new DefaultIvyConfiguration("compile"))
        generator.addConfiguration(new DefaultIvyConfiguration("sources"))
        generator.addConfiguration(new DefaultIvyConfiguration("runtime"))

        def ideaLibJars = project.fileTree(ideaDirectory)
        ideaLibJars.include("lib*/*.jar")
        ideaLibJars.files.each {
            generator.addArtifact(createDependency(it, "compile", ideaDirectory))
            filesToIgnoreWhileBuilding.add(it)
        }

        if (bundledPlugins.length > 0) {
            def bundledPluginJars = project.fileTree(ideaDirectory)
            bundledPlugins.each { bundledPluginJars.include("plugins/${it}/lib/*.jar") }
            bundledPluginJars.files.each {
                generator.addArtifact(createDependency(it, "runtime", ideaDirectory))
                filesToIgnoreWhileBuilding.add(it)
            }
        }

        def javaLibDirectory = javaHomeLib()
        def toolsJars = project.fileTree(javaLibDirectory)
        toolsJars.include("*tools.jar")
        toolsJars.files.each {
            generator.addArtifact(createDependency(it, "runtime", javaLibDirectory))
            filesToIgnoreWhileBuilding.add(it)
        }

        if (ideaSourcesFile != null) {
            // source dependency must be named in the same way as module name
            def artifact = new DefaultIvyArtifact(ideaSourcesFile, moduleName, "jar", "sources", "sources")
            artifact.conf = "sources"
            generator.addArtifact(artifact)
            filesToIgnoreWhileBuilding.add(ideaSourcesFile)
        }

        def parentDirectory = new File(ideaDirectory, "com.jetbrains/${moduleName}/${version}")
        parentDirectory.mkdirs()
        generator.writeTo(new File(parentDirectory, "ivy-${project.name}.xml"))
        return moduleName
    }

    @Nullable
    private static File javaHomeLib() {
        def javaHome = System.getProperty("java.home")
        return javaHome != null && !javaHome.isEmpty() ? new File("${javaHome}", "/../lib") : null;
    }

    @NotNull
    private static DefaultIvyArtifact createDependency(File file, String configuration, File baseDir) {
        def relativePath = baseDir.toURI().relativize(file.toURI()).getPath()
        def artifact = new DefaultIvyArtifact(file, relativePath - ".jar", "jar", "jar", null)
        artifact.conf = configuration
        artifact
    }
}
