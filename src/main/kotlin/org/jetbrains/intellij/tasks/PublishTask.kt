package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import org.jetbrains.intellij.info
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.pluginRepository.model.PluginXmlId

@Suppress("UnstableApiUsage")
open class PublishTask : ConventionTask() {

    @InputFile
    val distributionFile: RegularFileProperty = project.objects.fileProperty()

    @Input
    val host: Property<String> = project.objects.property(String::class.java).convention("https://plugins.jetbrains.com")

    @Input
    @Optional
    val token: Property<String> = project.objects.property(String::class.java)

    @Input
    @Optional
    val channels: ListProperty<String> = project.objects.listProperty(String::class.java).convention(listOf("default"))

    init {
        enabled = !project.gradle.startParameter.isOffline
    }

    @TaskAction
    fun publishPlugin() {
        validateInput()

        val file = distributionFile.get().asFile
        when (val creationResult = IdePluginManager.createManager().createPlugin(file.toPath())) {
            is PluginCreationSuccess -> {
                val pluginId = creationResult.plugin.pluginId
                channels.get().forEach { channel ->
                    info(this, "Uploading plugin $pluginId from ${file.absolutePath} to ${host.get()}, channel: $channel")
                    try {
                        val repositoryClient = PluginRepositoryFactory.create(host.get(), token.get())
                        repositoryClient.uploader.uploadPlugin(pluginId as PluginXmlId, file, channel.takeIf { it != "default" }, null)
                        info(this, "Uploaded successfully")
                    } catch (exception: Exception) {
                        throw TaskExecutionException(this, GradleException("Failed to upload plugin. $exception.message", exception))
                    }
                }
            }
            is PluginCreationFail -> {
                val problems = creationResult.errorsAndWarnings.filter { it.level == PluginProblem.Level.ERROR }.joinToString()
                throw TaskExecutionException(this, GradleException("Cannot upload plugin. $problems"))
            }
            else -> {
                throw TaskExecutionException(this, GradleException("Cannot upload plugin. $creationResult"))
            }
        }
    }

    private fun validateInput() {
        if (token.orNull.isNullOrEmpty()) {
            throw TaskExecutionException(this, GradleException("token property must be specified for plugin publishing"))
        }
    }
}
