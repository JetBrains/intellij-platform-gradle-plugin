package org.jetbrains.intellij
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileTree
import org.gradle.api.file.FileCollection
import org.gradle.api.logging.Logging
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.SourceSet
import org.gradle.plugins.ide.idea.IdeaPlugin
import org.gradle.plugins.ide.idea.model.ProjectLibrary
import org.jetbrains.annotations.NotNull

class IntelliJPlugin implements Plugin<Project> {
    private static final def LOG = Logging.getLogger(IntelliJPlugin.class)
    private static final CONFIGURATION_NAME = "intellij"
    private static final EXTENSION_NAME = "intellij"

    @Override
    def void apply(Project project) {
        project.getPlugins().apply(JavaPlugin.class)
        def intellijExtension = project.extensions.create(EXTENSION_NAME, IntelliJPluginExtension.class)
        intellijExtension.with {
            version = "142-SNAPSHOT"
        }
        configureConfigurations(project, intellijExtension)
    }

    private static def configureConfigurations(@NotNull Project project,
                                               @NotNull IntelliJPluginExtension intelliJPluginExtension) {
        def configuration = project.configurations.create(CONFIGURATION_NAME)
                .setVisible(false)
                .setTransitive(true)
                .setDescription("The IntelliJ IDEA Community Edition artifact to be used for this project.")

        LOG.info("Preparing IntelliJ IDEA dependency task")
        project.afterEvaluate() {
            if (configuration.dependencies.empty) {
                def version = intelliJPluginExtension.version
                LOG.info("Adding IntelliJ IDEA repository")
                project.repositories.maven {
                    def type = version.contains('SNAPSHOT') ? 'snapshots' : 'releases'
                    it.url = "https://www.jetbrains.com/intellij-repository/${type}"
                }


                LOG.info("Adding IntelliJ IDEA dependency")
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
                
                project.plugins.withType(IdeaPlugin) {
                    it.model.project.projectLibraries << new ProjectLibrary("IDEA-${version}", ideaJars) 
                }
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
