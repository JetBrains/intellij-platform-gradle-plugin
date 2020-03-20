package org.jetbrains.intellij.tasks

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.jetbrains.intellij.dependency.PluginDependency
import org.jetbrains.intellij.dependency.PluginDependencyImpl

class DownloadRobotRegistryPluginTask extends ConventionTask {
    @Input
    String robotServerVersion

    @TaskAction
    void download() {
        def sourceUrl = "https://bintray.com/jetbrains/intellij-third-party-dependencies/download_file?file_path=org%2Fjetbrains%2Ftest%2Frobot-server-plugin%2F${robotServerVersion}%2Frobot-server-plugin-${robotServerVersion}.zip"
        def outputDir = new File(project.buildDir, "robotServerPlugin")
        outputDir.mkdirs()
        ant.get(src: sourceUrl, dest: "$outputDir/robot-server-plugin.zip")
        ant.unzip(src: "$outputDir/robot-server-plugin.zip", dest: outputDir)
    }

    PluginDependency plugin() {
        new PluginDependencyImpl("robot-server-plugin-id", "", new File(project.buildDir, "robotServerPlugin"))
    }
}
