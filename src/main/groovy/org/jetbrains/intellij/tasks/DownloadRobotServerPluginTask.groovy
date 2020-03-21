package org.jetbrains.intellij.tasks

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction


class DownloadRobotServerPluginTask extends ConventionTask {
    private final static String ROBOT_SERVER_REPO = "https://jetbrains.bintray.com/intellij-third-party-dependencies"
    private final static String ROBOT_SERVER_DEPENDENCY = "org.jetbrains.test:robot-server-plugin"

    @Input
    String robotServerVersion

    @OutputDirectory
    File outputDir = new File(project.buildDir, "robotServerPlugin")

    @TaskAction
    void downloadPlugin() {
        def dependency = project.dependencies.create("$ROBOT_SERVER_DEPENDENCY:$robotServerVersion")
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
