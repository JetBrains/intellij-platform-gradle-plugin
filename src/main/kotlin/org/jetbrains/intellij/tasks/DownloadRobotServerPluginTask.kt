package org.jetbrains.intellij.tasks

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.unzip
import java.io.File
import java.net.URI

open class DownloadRobotServerPluginTask : ConventionTask() {

    val ROBOT_SERVER_REPO = "https://jetbrains.bintray.com/intellij-third-party-dependencies"
    val ROBOT_SERVER_DEPENDENCY = "org.jetbrains.test:robot-server-plugin"
    val DEFAULT_ROBOT_SERVER_PLUGIN_VERSION = "0.10.0"

    @Input
    var version = DEFAULT_ROBOT_SERVER_PLUGIN_VERSION

    @OutputDirectory
    val outputDir = File(project.buildDir, "robotServerPlugin")

    @TaskAction
    fun downloadPlugin() {
        val dependency = project.dependencies.create("$ROBOT_SERVER_DEPENDENCY:$version")
        val repo = project.repositories.maven { it.url = URI.create(ROBOT_SERVER_REPO) }
        project.delete(outputDir)
        try {
            val zipFile = project.configurations.detachedConfiguration(dependency).singleFile
            unzip(zipFile, outputDir, project, targetDirName = "")
        }
        finally {
            project.repositories.remove(repo)
        }
    }
}
