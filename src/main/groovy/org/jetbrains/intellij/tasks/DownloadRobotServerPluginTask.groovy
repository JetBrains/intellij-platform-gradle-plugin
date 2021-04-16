package org.jetbrains.intellij.tasks

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.util.VersionNumber


class DownloadRobotServerPluginTask extends ConventionTask {
    private final static String ROBOT_SERVER_REPO = "https://cache-redirector.jetbrains.com/intellij-dependencies"
    private final static String OLD_ROBOT_SERVER_DEPENDENCY = "org.jetbrains.test:robot-server-plugin"
    private final static String NEW_ROBOT_SERVER_DEPENDENCY = "com.intellij.remoterobot:robot-server-plugin"

    public static final String DEFAULT_ROBOT_SERVER_PLUGIN_VERSION = '0.10.0'

    @Input
    String version = DEFAULT_ROBOT_SERVER_PLUGIN_VERSION

    @OutputDirectory
    File outputDir = new File(project.buildDir, "robotServerPlugin")

    @TaskAction
    void downloadPlugin() {
        def dependency = project.dependencies.create("${getDependency()}:$version")
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

    private String getDependency() {
        if (VersionNumber.parse(version) < VersionNumber.parse("0.11.0")) {
            return OLD_ROBOT_SERVER_DEPENDENCY
        } else {
            return NEW_ROBOT_SERVER_DEPENDENCY
        }
    }
}
