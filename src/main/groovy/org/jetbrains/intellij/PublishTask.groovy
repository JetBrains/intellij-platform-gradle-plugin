package org.jetbrains.intellij

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.api.tasks.bundling.Zip
import org.jetbrains.intellij.pluginRepository.PluginRepositoryInstance

class PublishTask extends DefaultTask {
    public static String NAME = "publishPlugin"

    public PublishTask() {
        name = NAME
        group = IntelliJPlugin.GROUP_NAME
        description = "Publish plugin distribution on plugins.jetbrains.com."
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    @TaskAction
    protected void publishPlugin() {
        def extension = project.extensions.findByName(IntelliJPlugin.EXTENSION_NAME) as IntelliJPluginExtension
        if (extension != null) {
            boolean misconfigurated = false
            if (extension.publish.pluginId) {
                IntelliJPlugin.LOG.warn("intellij.publish.pluginId property is deprecated. " +
                        "Tag 'id' from plugin.xml will be used for uploading.")
            }
            def pluginId = Utils.getPluginId(project)
            if (!pluginId) {
                IntelliJPlugin.LOG.warn("id tag is missing in plugin.xml")
                misconfigurated = true
            }
            if (!extension.publish.username) {
                IntelliJPlugin.LOG.error("intellij.publish.username is empty")
                misconfigurated = true
            }
            if (!extension.publish.password) {
                IntelliJPlugin.LOG.error("intellij.publish.password is empty")
                misconfigurated = true
            }
            if (misconfigurated) {
                return
            }

            def buildPluginTask = project.tasks.findByName(IntelliJPlugin.BUILD_PLUGIN_TASK_NAME) as Zip
            def distributionFile = buildPluginTask.archivePath
            if (!distributionFile.exists()) {
                IntelliJPlugin.LOG.error("Cannot find distribution to upload: $distributionFile.absolutePath")
                return
            }

            def host = "http://plugins.jetbrains.com"
            IntelliJPlugin.LOG.info("Uploading plugin $pluginId from $distributionFile.absolutePath to $host")
            try {
                def repoClient = new PluginRepositoryInstance(host, extension.publish.username, extension.publish.password)
                repoClient.uploadPlugin(pluginId, distributionFile, extension.publish.channel ?: '')
                IntelliJPlugin.LOG.info("Uploaded successfully")
            }
            catch (exception) {
                throw new TaskExecutionException(this, new RuntimeException("Failed to upload plugin", exception))
            }
        }
    }
}
