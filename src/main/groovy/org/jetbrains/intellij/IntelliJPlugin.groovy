package org.jetbrains.intellij

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.HttpResponseDecorator
import groovyx.net.http.Method
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolveException
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.internal.artifacts.publish.ArchivePublishArtifact
import org.gradle.api.internal.plugins.DefaultArtifactPublicationSet
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.publish.ivy.internal.artifact.DefaultIvyArtifact
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyConfiguration
import org.gradle.api.publish.ivy.internal.publication.DefaultIvyPublicationIdentity
import org.gradle.api.publish.ivy.internal.publisher.IvyDescriptorFileGenerator
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.language.jvm.tasks.ProcessResources
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
    private static final String INTELLIJ_PLUGINS_REPO = 'http://plugins.jetbrains.com'
    private static final String GRADLE_CACHE_DIR = 'caches/modules-2/files-2.1/com.jetbrains.intellij.idea/'
    public static final IDEA_MODULE_NAME = "idea"

    @Override
    def void apply(Project project) {
        project.getPlugins().apply(JavaPlugin)
        def intellijExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension)
        intellijExtension.with {
            plugins = []
            externalPlugins = []
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
            LOG.info("Preparing IntelliJ IDEA dependency task")
            configureIntelliJDependency(it, extension)
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
            try {
                def sourcesFiles = sourcesConfiguration.files
                if (sourcesFiles.size() == 1) {
                    def sourcesFile = sourcesFiles.first()
                    LOG.info("IDEA sources jar: " + sourcesFile.path)
                    extension.ideaSourcesFile = sourcesFile
                } else {
                    LOG.warn("Cannot attach IDEA sources. Found files: " + sourcesFiles)
                }
            } catch (ResolveException e) {
                LOG.warn("Cannot resolve IDEA sources dependency", e)
            }
        }
    }

    private static void configurePluginDependencies(@NotNull Project project,
                                                    @NotNull IntelliJPluginExtension extension) {
        def ivyFile = createIvyRepo(project, extension)
        def version = extension.version

        def externalPluginScope = project.configurations.create("externalPluginScope")

        project.sourceSets.configure {
            main.compileClasspath += externalPluginScope
            test.compileClasspath += externalPluginScope
            test.runtimeClasspath += externalPluginScope
        }

        project.repositories.ivy { repo ->
            repo.url = extension.ideaDirectory
            repo.ivyPattern(ivyFile.getAbsolutePath()) // ivy xml
            repo.artifactPattern("$extension.ideaDirectory.path/[artifact].[ext]") // idea libs

            def toolsJar = Jvm.current().toolsJar
            if (toolsJar) {
                repo.artifactPattern("$toolsJar.parent/[artifact].[ext]") // java libs
            }
            if (extension.ideaSourcesFile) { // sources
                repo.artifactPattern("$extension.ideaSourcesFile.parent/[artifact]IC-$version-[classifier].[ext]")
            }
        }
        project.dependencies.add(JavaPlugin.COMPILE_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: IDEA_MODULE_NAME, version: version, configuration: 'compile'
        ])
        project.dependencies.add(JavaPlugin.RUNTIME_CONFIGURATION_NAME, [
                group: 'com.jetbrains', name: IDEA_MODULE_NAME, version: version, configuration: 'runtime'
        ])

        extension.externalPlugins.each {
            project.dependencies.add("externalPluginScope", project.files(externalPluginJars(project, extension, it)))
        }
    }

    private static void configurePatchPluginXmlTask(@NotNull Project project) {
        LOG.info("Configuring patch plugin.xml task")
        project.tasks.withType(ProcessResources.class)*.doLast(new PatchPluginXmlTask())
    }

    private static void configurePrepareSandboxTask(@NotNull Project project) {
        LOG.info("Configuring prepare IntelliJ sandbox task")
        project.tasks.create(PrepareSandboxTask.NAME, PrepareSandboxTask)
                .dependsOn(project.getTasksByName(JavaPlugin.CLASSES_TASK_NAME, false))
                .dependsOn(project.getTasksByName(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, false))
    }

    private static void configurePrepareTestsSandboxTask(@NotNull Project project) {
        LOG.info("Configuring prepare IntelliJ sandbox for tests task")
        project.tasks.create(PrepareTestsSandboxTask.NAME, PrepareTestsSandboxTask)
                .dependsOn(project.getTasksByName(JavaPlugin.TEST_CLASSES_TASK_NAME, false))
                .dependsOn(project.getTasksByName(JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME, false))
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

    private static File createIvyRepo(@NotNull Project project, @NotNull IntelliJPluginExtension extension) {
        def generator = new IvyDescriptorFileGenerator(new DefaultIvyPublicationIdentity("com.jetbrains", IDEA_MODULE_NAME, extension.version))
        generator.addConfiguration(new DefaultIvyConfiguration("compile"))
        generator.addConfiguration(new DefaultIvyConfiguration("sources"))
        generator.addConfiguration(new DefaultIvyConfiguration("runtime"))

        def ideaLibJars = project.fileTree(extension.ideaDirectory)
        ideaLibJars.include("lib*/*.jar")
        excludeKotlinDependenciesIfNeeded(project, ideaLibJars)
        ideaLibJars.files.each {
            generator.addArtifact(Utils.createDependency(it, "compile", extension.ideaDirectory))
            extension.intellijFiles.add(it)
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
        if (toolsJar) {
            generator.addArtifact(Utils.createDependency(toolsJar, "runtime", toolsJar.parentFile))
            extension.intellijFiles.add(toolsJar)
        }

        if (extension.ideaSourcesFile) {
            // source dependency must be named in the same way as module name
            def artifact = new DefaultIvyArtifact(extension.ideaSourcesFile, IDEA_MODULE_NAME, "jar", "sources", "sources")
            artifact.conf = "sources"
            generator.addArtifact(artifact)
            extension.intellijFiles.add(extension.ideaSourcesFile)
        }

        def ivyFile = File.createTempFile("ivy-idea", ".xml")
        generator.writeTo(ivyFile)
        return ivyFile
    }

    private static def excludeKotlinDependenciesIfNeeded(@NotNull Project project, @NotNull ConfigurableFileTree tree) {
        def configurations = project.configurations
        def closure = {
            if ("org.jetbrains.kotlin".equals(it.group)) {
                if ("kotlin-runtime".equals(it.name)) {
                    tree.exclude("lib/kotlin-runtime.jar")
                } else if ("kotlin-reflect".equals(it.name)) {
                    tree.exclude("lib/kotlin-reflect.jar")
                }
            }
        }
        configurations.getByName(JavaPlugin.RUNTIME_CONFIGURATION_NAME).getAllDependencies().each closure
        configurations.getByName(JavaPlugin.COMPILE_CONFIGURATION_NAME).getAllDependencies().each closure

    }

    private static def externalPluginJars(
            @NotNull Project project,
            @NotNull IntelliJPluginExtension extension,
            @NotNull Map<String, String> plugin
    ) {

        new File("$extension.ideaDirectory/externalPluginsLibs/").mkdirs()

        def version = plugin.get("version")
        if (version == null) {
            version = 'LATEST'
        }

        def cachedJars = extractExternalPluginJars(project, plugin)
        if (cachedJars.size() > 0) {
            return cachedJars;
        }

        def tmpDir = File.createTempDir();

        def gradleCacheDir = new File(project.gradle.gradleUserHomeDir, GRADLE_CACHE_DIR)

        new HTTPBuilder(downloadLink(project, plugin)).request(Method.GET) {
            response.success = { resp, stream ->
                def name = guessName(resp, plugin)

                new File(tmpDir, name).newOutputStream() << stream

                project.copy {
                    it.from("$tmpDir/$name")
                    it.into("$gradleCacheDir/$plugin.id/$version")
                }

                if (name.endsWith("zip")) {
                    project.copy {
                        it.from(project.zipTree("$gradleCacheDir/$plugin.id/$version/$name"))
                        it.into("$gradleCacheDir/$plugin.id/$version")
                    }
                }
            }
        }

        return extractExternalPluginJars(project, plugin)
    }

    private static def guessName(@NotNull HttpResponseDecorator resp, @NotNull Map<String, String> plugin) {
        resp.getHeaders("Content-Type").each {
            plugin.put("type", it.getValue())
        }
        resp.getHeaders("Content-Disposition").each {
            plugin.put("name", it.getValue())
        }
        def name = plugin.containsKey("name") ? plugin.get("name") : plugin.get("id")
        def extension = plugin.get("type").equals("application/zip") ? 'zip' : 'jar'

        return name + "." + extension
    }

    private static def downloadLink(@NotNull Project project, @NotNull Map<String, String> plugin) {
        if (plugin.containsKey("version")) {
            return "$INTELLIJ_PLUGINS_REPO/plugin/download?pluginId=$plugin.id&version=$plugin.version"
        }

        def extension = project.extensions.getByType(IntelliJPluginExtension)
        def build = Utils.ideaBuildNumber(extension.ideaDirectory)

        return "$INTELLIJ_PLUGINS_REPO/pluginManager?action=download&id=$plugin.id&build=$build"
    }

    private static def extractExternalPluginJars(
            @NotNull Project project,
            @NotNull Map<String, String> plugin
    ) {
        def version = plugin.get("version")
        if (version == null) {
            version = 'LATEST'
        }
        def externalPluginInCache = new File("$project.gradle.gradleUserHomeDir/$GRADLE_CACHE_DIR/$plugin.id/$version");

        if (!externalPluginInCache.exists()) {
            return NO_FILES
        }

        def jarFiles = externalPluginInCache.listFiles(JARS)

        if (jarFiles.size() == 1) {
            return jarFiles
        }

        def directories = externalPluginInCache.listFiles(DIRECTORIES)
        if (directories.size() == 1) {
            return new File(externalPluginInCache.getPath() + "/" + directories[0].getName() + "/lib").listFiles(JARS)
        }

        return NO_FILES
    }

    private static final FileFilter JARS = new FileFilter() {
        @Override
        boolean accept(File pathname) {
            return pathname.getName().endsWith(".jar")
        }
    }

    private static final FileFilter DIRECTORIES = new FileFilter() {
        @Override
        boolean accept(File pathname) {
            return pathname.isDirectory()
        }
    }

    private static final File[] NO_FILES = new File[0];
}
