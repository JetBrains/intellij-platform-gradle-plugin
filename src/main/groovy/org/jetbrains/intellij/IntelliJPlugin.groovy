package org.jetbrains.intellij

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.jetbrains.annotations.NotNull

class IntelliJPlugin implements Plugin<Project> {
    private static final def LOG = Logging.getLogger(IntelliJPlugin.class)
    private static final IDEA_DEPENDENCY_TASK = "ideaDependencyTask"
    private static final CONFIGURATION_NAME = "idea"
    private static final EXTENSION_NAME = "idea"

    @Override
    def void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class)
        def ideaExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension.class)
        ideaExtension.with {
            version = "142-SNAPSHOT"
        }
        configureConfigurations(project, ideaExtension)
    }

    private static def configureConfigurations(@NotNull Project project,
                                               @NotNull IntelliJPluginExtension intelliJPluginExtension) {
        def configuration = project.configurations.create(CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)
                .setDescription("The IntelliJ IDEA Community Edition artifact to be used for this project.")

        LOG.info("Preparing idea dependency")
        def ideaDependencyTask = project.task(IDEA_DEPENDENCY_TASK, {
            it.description = "Downloading and preparing IntelliJ IDEA dependency."
            it.group = BasePlugin.BUILD_GROUP
            project.tasks.getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME).dependsOn(IDEA_DEPENDENCY_TASK)
            project.tasks.getByName(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME).dependsOn(IDEA_DEPENDENCY_TASK)
        })

        ideaDependencyTask.doLast {
            if (configuration.dependencies.isEmpty()) {
                LOG.info("Adding idea dependency")
                def version = intelliJPluginExtension.version
                project.dependencies.add(CONFIGURATION_NAME, "com.jetbrains.intellij.idea:ideaIC:${version}@zip")

                LOG.info("IDEA zip: " + configuration.singleFile.path)

                def ideaJars = project.fileTree(ideaDirectory(project, configuration.singleFile))
                ideaJars.include("lib*/*.jar");
                intelliJPluginExtension.plugins.each {
                    ideaJars.include("plugins/${it}/lib/*.jar")
                }

                def ideaTestJars = project.fileTree("${System.getProperty("java.home")}/../lib", {
                    ConfigurableFileTree tree ->
                        tree.include {
                            "*tools.jar"
                        }
                })

                def javaConvention = project.convention.getPlugin(JavaPluginConvention.class)
                populateSourceSetWithIdeaJars(javaConvention, ideaJars, SourceSet.MAIN_SOURCE_SET_NAME)
                populateSourceSetWithIdeaJars(javaConvention, ideaJars + ideaTestJars, SourceSet.TEST_SOURCE_SET_NAME)
            }
        }
    }

    private static def populateSourceSetWithIdeaJars(@NotNull JavaPluginConvention javaConvention,
                                                     @NotNull FileCollection customDeps,
                                                     @NotNull String sourceSetName) {
        def sourceSet = javaConvention.sourceSets.getByName(sourceSetName)
        sourceSet.compileClasspath += customDeps
        sourceSet.runtimeClasspath += customDeps
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
