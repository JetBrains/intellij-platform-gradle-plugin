package org.jetbrains.intellij
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.NotNull

class IntelliJPlugin implements Plugin<Project> {
    private static final def LOG = Logging.getLogger(IntelliJPlugin.class)
    private static final EXTENSION_NAME = "intellij"
    private static final String DEFAULT_IDEA_VERSION = "142-SNAPSHOT"

    @Override
    def void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class)
        def intellijExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension.class)
        intellijExtension.with {
            version = DEFAULT_IDEA_VERSION
        }
        configureConfigurations(project, intellijExtension)
        configureSetPluginVersionTask(project)
    }

    private static def configureConfigurations(@NotNull Project project,
                                               @NotNull IntelliJPluginExtension intelliJPluginExtension) {
        def configuration = project.configurations.create("intellij")
                .setVisible(true)
                .setDescription("The IntelliJ IDEA Community Edition artifact to be used for this project.")
        def sdkConfiguration = project.configurations.create("intellij-sdk")
                .setVisible(true)
                .setDescription("The IntelliJ IDEA Community Edition SDK jars.")
        def toolsConfiguration = project.configurations.create("intellij-sdk-test")
                .setVisible(true)
                .setDescription("Extra IntelliJ IDEA Community Edition jars.")
        def bundledPluginsConfiguration = project.configurations.create("intellij-provided")
                .setVisible(true)
                .setDescription("The IntelliJ IDEA Community Edition jars of provided plugins.")

        project.configurations.compile.extendsFrom(configuration) // needed for sources resolving
        project.configurations.compile.extendsFrom(sdkConfiguration)
        project.configurations.runtime.extendsFrom(bundledPluginsConfiguration, toolsConfiguration)

        project.afterEvaluate {
            LOG.info("Preparing IntelliJ IDEA dependency task")
            if (configuration.dependencies.empty) {
                def version = intelliJPluginExtension.version
                LOG.info("Adding IntelliJ IDEA repository")
                project.repositories.maven {
                    def type = version.contains('SNAPSHOT') ? 'snapshots' : 'releases'
                    it.url = "https://www.jetbrains.com/intellij-repository/${type}"
                }

                LOG.info("Adding IntelliJ IDEA dependency")
                project.dependencies.add(configuration.name, "com.jetbrains.intellij.idea:ideaIC:${version}@zip")
                LOG.info("IDEA zip: " + configuration.singleFile.path)

                def sdkJars = project.fileTree(ideaDirectory(project, configuration.singleFile))
                sdkJars.include("lib*/*.jar");

                def bundledPluginJars = project.fileTree(ideaDirectory(project, configuration.singleFile))
                intelliJPluginExtension.plugins.each {
                    bundledPluginJars.include("plugins/${it}/lib/*.jar")
                }

                def toolsJars = project.fileTree("${System.getProperty("java.home")}/../lib", {
                    ConfigurableFileTree tree ->
                        tree.include {
                            "*tools.jar"
                        }
                })
                project.dependencies.add(sdkConfiguration.name, sdkJars)
                project.dependencies.add(toolsConfiguration.name, toolsJars)
                project.dependencies.add(bundledPluginsConfiguration.name, bundledPluginJars)
            }
        }
    }

    private static void configureSetPluginVersionTask(@NotNull Project project) {
        project.afterEvaluate {
            JavaPluginConvention javaConvention = project.convention.getPlugin(JavaPluginConvention.class);
            SourceSet mainSourceSet = javaConvention.sourceSets.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
            PluginVersionTask task = project.tasks.create(PluginVersionTask.NAME, PluginVersionTask.class);
            task.setSourceSet(mainSourceSet);
            project.getTasksByName(JavaPlugin.JAR_TASK_NAME, false)*.dependsOn(task)
        }
    }

    @NotNull
    private static File ideaDirectory(@NotNull Project project, @NotNull File zipFile) {
        def directoryName = zipFile.name - ".zip"
        def cacheDirectory = new File(zipFile.parent, directoryName)
        if (!cacheDirectory.exists()) cacheDirectory.mkdir()
        def markerFile = new File(cacheDirectory, "markerFile")
        if (!markerFile.exists()) {
            LOG.info("Unzipping idea")
            project.copy {
                it.from(project.zipTree(zipFile))
                it.into(cacheDirectory)
            }
            markerFile.createNewFile()
        }
        return cacheDirectory;
    }
}
