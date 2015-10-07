package org.jetbrains.intellij

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.apache.http.entity.mime.MultipartEntityBuilder
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip

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
            if (!extension.publish.pluginId) {
                IntelliJPlugin.LOG.error("intellij.publish.pluginId is empty")
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
            IntelliJPlugin.LOG.info("Uploading plugin $extension.publish.pluginId from $distributionFile.absolutePath to $host")
            try {
                new HTTPBuilder().request("$host/plugin/uploadPlugin", Method.POST, 'multipart/form-data') { request ->
                    def content = MultipartEntityBuilder.create().addBinaryBody('file', distributionFile)
                            .addTextBody('pluginId', extension.publish.pluginId)
                            .addTextBody('userName', extension.publish.username)
                            .addTextBody('password', extension.publish.password)
                            .addTextBody('channel', extension.publish.channel ?: '')
                            .build()
                    
                    request.setEntity(content)
                    response.success = { resp ->
                        if (resp.status != 200 && resp.status != 302) {
                            IntelliJPlugin.LOG.error("Failed to upoad plugin: $resp.statusLine")
                            return
                        }
                        IntelliJPlugin.LOG.info("Uploaded successfully")
                    }
                    response.failure = { resp ->
                        IntelliJPlugin.LOG.error("Failed to upoad plugin: $resp.statusLine")
                    }
                }
            }
            catch (exception) {
                IntelliJPlugin.LOG.error("Failed to upload plugin", exception)
            }
        }
    }
}
