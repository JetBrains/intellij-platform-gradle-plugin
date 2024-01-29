// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.base.plugin.PluginCreationFail
import com.jetbrains.plugin.structure.base.plugin.PluginCreationSuccess
import com.jetbrains.plugin.structure.base.problems.PluginProblem
import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.PLUGIN_GROUP_NAME
import org.jetbrains.intellij.platform.gradle.IntelliJPluginConstants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.ToolboxEnterprisePluginRepositoryService
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.pluginRepository.model.StringPluginId

/**
 * Publishes plugin to the remote [JetBrains Marketplace](https://plugins.jetbrains.com) repository.
 *
 * The following attributes are a part of the Publishing DSL `publishPlugin { ... }` in which allows Gradle to upload plugin to [JetBrains Marketplace](https://plugins.jetbrains.com).
 * Note that you need to [upload the plugin](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#uploading-a-plugin-to-jetbrains-marketplace) to the repository at least once manually (to specify options like the license, repository URL etc.) before uploads through Gradle can be used.
 *
 * See the instruction on [how to generate authentication token](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html).
 *
 * See [Publishing Plugin With Gradle](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#publishing-plugin-with-gradle) tutorial for step-by-step instructions.
 */
@UntrackedTask(because = "Output is stored remotely")
abstract class PublishPluginTask : DefaultTask() {

    /**
     * Jar or Zip file of plugin to upload.
     *
     * Default value: output of the `buildPlugin` task
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveFile: RegularFileProperty

    /**
     * URL host of a plugin repository.
     *
     * Default value: `https://plugins.jetbrains.com`
     */
    @get:Input
    @get:Optional
    abstract val host: Property<String>

    /**
     * Required.
     * Authentication token.
     */
    @get:Input
    @get:Optional
    abstract val token: Property<String>

    /**
     * List of channel names to upload plugin to.
     *
     * Default value: `["default"]`
     */
    @get:Input
    @get:Optional
    abstract val channels: ListProperty<String>

    /**
     * Publish the plugin update and mark it as hidden to prevent public release after approval.
     * See: https://plugins.jetbrains.com/docs/marketplace/hidden-plugin.html
     */
    @get:Input
    @get:Optional
    abstract val hidden: Property<Boolean>

    /**
     * Specifies if the Toolbox Enterprise plugin repository service should be used.
     *
     * Default value: `false`
     */
    @get:Input
    @get:Optional
    abstract val toolboxEnterprise: Property<Boolean>

    private val log = Logger(javaClass)

    init {
        group = PLUGIN_GROUP_NAME
        description = "Publishes plugin to the remote Marketplace repository."
    }

    @TaskAction
    fun publishPlugin() {
        if (token.orNull.isNullOrEmpty()) {
            throw TaskExecutionException(this, GradleException("token property must be specified for plugin publishing"))
        }

        val path = archiveFile.asPath
        when (val creationResult = IdePluginManager.createManager().createPlugin(path)) {
            is PluginCreationSuccess -> {
                if (creationResult.unacceptableWarnings.isNotEmpty()) {
                    val problems = creationResult.unacceptableWarnings.joinToString()
                    throw TaskExecutionException(this, GradleException("Cannot upload plugin: $problems"))
                }
                val pluginId = creationResult.plugin.pluginId
                channels.get().forEach { channel ->
                    log.info("Uploading plugin '$pluginId' from '$path' to '${host.get()}', channel: '$channel'")
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
                        repositoryClient.uploader.upload(
                            id = pluginId as StringPluginId,
                            file = path.toFile(),
                            channel = channel.takeIf { it != "default" },
                            notes = null,
                            isHidden = hidden.get(),
                        )
                        log.info("Uploaded successfully")
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

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PublishPluginTask>(Tasks.PUBLISH_PLUGIN) {
                val extension = project.the<IntelliJPlatformExtension>()
                val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)
                val signPluginTaskProvider = project.tasks.named<SignPluginTask>(Tasks.SIGN_PLUGIN)

                val isOffline = project.gradle.startParameter.isOffline

                token.convention(extension.publishing.token)
                host.convention(extension.publishing.host)
                toolboxEnterprise.convention(extension.publishing.toolboxEnterprise)
                channels.convention(extension.publishing.channel.map { listOf(it) })
                hidden.convention(extension.publishing.hidden)

                archiveFile.convention(
                    signPluginTaskProvider
                        .map { it.didWork }
                        .flatMap { signed ->
                            when {
                                signed -> signPluginTaskProvider.flatMap { it.signedArchiveFile }
                                else -> buildPluginTaskProvider.flatMap { it.archiveFile }
                            }
                        }
                )

                dependsOn(buildPluginTaskProvider)
                dependsOn(signPluginTaskProvider)
                onlyIf { !isOffline }
            }

    }
}
