// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.plugin.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.internal.ConventionTask
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskExecutionException
import org.gradle.kotlin.dsl.listProperty
import org.gradle.kotlin.dsl.property
import org.jetbrains.intellij.info
import org.jetbrains.intellij.logCategory
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.pluginRepository.model.StringPluginId
import org.jetbrains.intellij.utils.ToolboxEnterprisePluginRepositoryService
import javax.inject.Inject

open class PublishPluginTask @Inject constructor(
    objectFactory: ObjectFactory,
) : ConventionTask() {

    /**
     * Jar or Zip file of plugin to upload.
     *
     * Default value: output of the `buildPlugin` task
     */
    @InputFile
    @Optional
    val distributionFile: RegularFileProperty = objectFactory.fileProperty()

    /**
     * URL host of a plugin repository.
     *
     * Default value: `https://plugins.jetbrains.com`
     */
    @Input
    @Optional
    val host = objectFactory.property<String>()

    /**
     * Required.
     * Authentication token.
     */
    @Input
    @Optional
    val token = objectFactory.property<String>()

    /**
     * List of channel names to upload plugin to.
     *
     * Default value: `["default"]`
     */
    @Input
    @Optional
    val channels = objectFactory.listProperty<String>()

    /**
     * Specifies if the Toolbox Enterprise plugin repository service should be used.
     *
     * Default value: `false`
     */
    @Input
    @Optional
    val toolboxEnterprise = objectFactory.property<Boolean>()

    private val context = logCategory()

    @TaskAction
    fun publishPlugin() {
        validateInput()

        val file = distributionFile.get().asFile
        when (val creationResult = IdePluginManager.createManager().createPlugin(file.toPath())) {
            is PluginCreationSuccess -> {
                if (creationResult.unacceptableWarnings.isNotEmpty()) {
                    val problems = creationResult.unacceptableWarnings.joinToString()
                    throw TaskExecutionException(this, GradleException("Cannot upload plugin: $problems"))
                }
                val pluginId = creationResult.plugin.pluginId
                channels.get().forEach { channel ->
                    info(context, "Uploading plugin '$pluginId' from '${file.absolutePath}' to '${host.get()}', channel: '$channel'")
                    try {
                        val repositoryClient = when (toolboxEnterprise.get()) {
                            true -> PluginRepositoryFactory.createWithImplementationClass(
                                host.get(),
                                token.get(),
                                "Automation",
                                ToolboxEnterprisePluginRepositoryService::class.java,
                            )
                            false -> PluginRepositoryFactory.create(host.get(), token.get())
                        }
                        repositoryClient.uploader.upload(pluginId as StringPluginId, file, channel.takeIf { it != "default" }, null)
                        info(context, "Uploaded successfully")
                    } catch (exception: Exception) {
                        throw TaskExecutionException(this, GradleException("Failed to upload plugin: ${exception.message}", exception))
                    }
                }
            }

            is PluginCreationFail -> {
                val problems = creationResult.errorsAndWarnings.filter { it.level == PluginProblem.Level.ERROR }.joinToString()
                throw TaskExecutionException(this, GradleException("Cannot upload plugin: $problems"))
            }

            else -> {
                throw TaskExecutionException(this, GradleException("Cannot upload plugin: $creationResult"))
            }
        }
    }

    private fun validateInput() {
        if (token.orNull.isNullOrEmpty()) {
            throw TaskExecutionException(this, GradleException("token property must be specified for plugin publishing"))
        }
    }
}
