package org.jetbrains.intellij

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet

class IntelliJPlugin : Plugin<Project> {
    val IDEA_DEPENDENCY_TASK = "ideaDependencyTask"
    val IDEA_CONFIGURATION_NAME = "idea"

    override fun apply(project: Project) {
        project.getPlugins().apply(javaClass<JavaPlugin>())
        configureConfigurations(project)
        configureBuildAndTestTasks(project)
    }

    private fun configureBuildAndTestTasks(project: Project) {
        val javaConvention = project.getConvention().getPlugin(javaClass<JavaPluginConvention>())
        val ideaDeps = project.getConfigurations().getByName(IDEA_CONFIGURATION_NAME).getAsFileTree()
        val customDeps = project.getConfigurations().getByName(IDEA_CONFIGURATION_NAME).getAsFileTree()

        populateSourceSetWithIdeaJars(javaConvention, customDeps, SourceSet.MAIN_SOURCE_SET_NAME)
        populateSourceSetWithIdeaJars(javaConvention, customDeps, SourceSet.TEST_SOURCE_SET_NAME)
    }

    private fun populateSourceSetWithIdeaJars(javaConvention: JavaPluginConvention,
                                              customDeps: FileCollection,
                                              sourceSetName: String) {
        val sourceSet = javaConvention.getSourceSets().getByName(sourceSetName)
        sourceSet.setCompileClasspath(sourceSet.getCompileClasspath().plus(customDeps))
        sourceSet.setRuntimeClasspath(sourceSet.getRuntimeClasspath().plus(customDeps))
    }

    private fun configureConfigurations(project: Project) {
        val configuration = project.getConfigurations().create(IDEA_CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)
                .setDescription("The libraries of IntelliJ IDEA Community Edition to be used for this project.")

        val ideaDependencyTask = project.getTasks().create(IDEA_DEPENDENCY_TASK, { it ->
            if (configuration.getDependencies().isEmpty()) {
                project.getDependencies().add(IDEA_CONFIGURATION_NAME, "com.jetbrains.intellij.idea:ideaIC:14.1@zip")
            }
            
            println(configuration.getSingleFile())
        })
        ideaDependencyTask.setDescription("Download and prepare IntelliJ IDEA dependency.")
        ideaDependencyTask.setGroup(BasePlugin.BUILD_GROUP)
        project.getTasks().getByName(BasePlugin.ASSEMBLE_TASK_NAME).dependsOn(IDEA_DEPENDENCY_TASK)
    }
}