package org.jetbrains.intellij

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.util.PatternSet
import java.io.File

class IntelliJPlugin : Plugin<Project> {
    val logger = Logging.getLogger(this.javaClass)

    val IDEA_DEPENDENCY_TASK = "ideaDependencyTask"
    val CONFIGURATION_NAME = "idea"
    val EXTENSION_NAME = "idea"

    override fun apply(project: Project) {
        project.getPlugins().apply(javaClass<JavaPlugin>())

        val ideaExtension = project.getExtensions().create(EXTENSION_NAME, javaClass<IntelliJPluginExtension>())
        configureConfigurations(project, ideaExtension)
    }

    private fun populateSourceSetWithIdeaJars(javaConvention: JavaPluginConvention,
                                              customDeps: FileCollection,
                                              sourceSetName: String) {
        val sourceSet = javaConvention.getSourceSets().getByName(sourceSetName)
        sourceSet.setCompileClasspath(sourceSet.getCompileClasspath() + customDeps)
        sourceSet.setRuntimeClasspath(sourceSet.getRuntimeClasspath() + customDeps)
    }

    private fun configureConfigurations(project: Project, intelliJPluginExtension: IntelliJPluginExtension) {
        val configuration = project.getConfigurations().create(CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)
                .setDescription("The IntelliJ IDEA Community Edition artifact to be used for this project.")

        logger.info("Preparing idea dependency")
        val ideaDependencyTask = project.getTasks().create(IDEA_DEPENDENCY_TASK, {
            it.setDescription("Downloading and preparing IntelliJ IDEA dependency.")
            it.setGroup(BasePlugin.BUILD_GROUP)
            project.getTasks().getByName(JavaPlugin.COMPILE_JAVA_TASK_NAME).dependsOn(IDEA_DEPENDENCY_TASK)
            project.getTasks().getByName(JavaPlugin.COMPILE_TEST_JAVA_TASK_NAME).dependsOn(IDEA_DEPENDENCY_TASK)
        })
        ideaDependencyTask.doLast {
            val ideaDependencies = configuration.getDependencies()
            if (ideaDependencies.isEmpty()) {
                logger.info("Adding idea dependency")
                val version = intelliJPluginExtension.version
                project.getDependencies().add(CONFIGURATION_NAME, "com.jetbrains.intellij.idea:ideaIC:${version}@zip")

                logger.info("IDEA zip: " + configuration.getSingleFile().path)

                val ideaJars = project.fileTree(ideaDirectory(project, configuration.getSingleFile()))
                ideaJars.include("lib*/*.jar");
                intelliJPluginExtension.plugins.forEach {
                    ideaJars.include("plugins/${it}/lib/*.jar")
                }
                
                val ideaTestJars = project.fileTree("${System.getProperty("java.home")}/../lib")
                        .matching(PatternSet().include("*tools.jar"))

                val javaConvention = project.getConvention().getPlugin(javaClass<JavaPluginConvention>())
                populateSourceSetWithIdeaJars(javaConvention, ideaJars, SourceSet.MAIN_SOURCE_SET_NAME)
                populateSourceSetWithIdeaJars(javaConvention, ideaJars + ideaTestJars, SourceSet.TEST_SOURCE_SET_NAME)
            }
        }
    }

    private fun ideaDirectory(project: Project, zipFile: File): File {
        val directoryName = zipFile.name.substringBeforeLast('.', "")
        val cacheDirectory = File(zipFile.getParent(), directoryName)
        if (!cacheDirectory.exists()) cacheDirectory.mkdir()
        val markerFile = File(cacheDirectory, "markerFile")
        if (!markerFile.exists()) {
            logger.info("Unzipping idea")
            (project as ProjectInternal).copy {
                it.from(zipFile.path)
                it.into(cacheDirectory)
            }
            markerFile.createNewFile()
        }
        return cacheDirectory;
    }
}