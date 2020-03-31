package org.jetbrains.intellij.tasks

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction


class DownloadRobotServerPluginTask extends ConventionTask {
    private final static String ROBOT_SERVER_REPO = "https://jetbrains.bintray.com/intellij-third-party-dependencies"
    private final static String ROBOT_SERVER_DEPENDENCY = "org.jetbrains.test:robot-server-plugin"
    public static final String DEFAULT_ROBOT_SERVER_PLUGIN_VERSION = '0.9.35'

    @Input
    String version = DEFAULT_ROBOT_SERVER_PLUGIN_VERSION

    @OutputDirectory
    File outputDir = new File(project.buildDir, "robotServerPlugin")

    @TaskAction
    void downloadPlugin() {
        def dependency = project.dependencies.create("$ROBOT_SERVER_DEPENDENCY:$version")
        def repo = project.repositories.maven { it.url = "$ROBOT_SERVER_REPO" }
        project.delete(outputDir)
        try {
            def zipFile = project.configurations.detachedConfiguration(dependency).singleFile
            ant.unzip(src: zipFile, dest: outputDir)
        }
        finally {
            project.repositories.remove(repo)
        }
    }
}
