// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import com.jetbrains.plugin.structure.intellij.plugin.IdePluginManager
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*
import org.gradle.kotlin.dsl.named
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.utils.*
import org.jetbrains.intellij.pluginRepository.PluginRepositoryFactory
import org.jetbrains.intellij.pluginRepository.model.StringPluginId

/**
 * Publishes the plugin to the remote plugins repository, such as [JetBrains Marketplace](https://plugins.jetbrains.com).
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#uploading-a-plugin-to-jetbrains-marketplace">Uploading a Plugin to JetBrains Marketplace</a>
 * @see <a href="https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html">Plugin upload API</a>
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html#publishing-plugin-with-gradle">Publishing Plugin With Gradle</a>
 */
@UntrackedTask(because = "Output stored remotely")
abstract class PublishPluginTask : DefaultTask() {

    /**
     * Specifies the ZIP archive file to be published to the remote repository.
     * By default, it uses the output [SignPluginTask.archiveFile] if plugin signing is configured, otherwise the [BuildPluginTask.archiveFile].
     *
     * Default value: [SignPluginTask.archiveFile] if plugin signing is configured, otherwise [BuildPluginTask.archiveFile].
     *
     * @see SignPluginTask.archiveFile
     * @see BuildPluginTask.archiveFile
     * @see IntelliJPlatformExtension.Signing
     */
    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val archiveFile: RegularFileProperty

    /**
     * Specifies the URL host of a plugin repository.
     *
     * Default value: [IntelliJPlatformExtension.Publishing.host]
     */
    @get:Input
    @get:Optional
    abstract val host: Property<String>

    /**
     * Specifies the authorization token.
     *
     * Default value: [IntelliJPlatformExtension.Publishing.token]
     * Required.
     */
    @get:Input
    @get:Optional
    abstract val token: Property<String>

    /**
     * Specifies a list of [JetBrains Marketplace](https://plugins.jetbrains.com) channel names used as destination for the plugin upload.
     *
     * Default value: [IntelliJPlatformExtension.Publishing.channels]
     */
    @get:Input
    @get:Optional
    abstract val channels: ListProperty<String>

    /**
     * Publishes the plugin update and marks it a [hidden](https://plugins.jetbrains.com/docs/marketplace/hidden-plugin.html) to prevent public visibility after approval.
     *
     * Default value: [IntelliJPlatformExtension.Publishing.hidden]
     */
    @get:Input
    @get:Optional
    abstract val hidden: Property<Boolean>

    /**
     * Specifies if the IDE Services plugin repository service should be used.
     *
     * Default value: [IntelliJPlatformExtension.Publishing.ideServices]
     */
    @get:Input
    @get:Optional
    abstract val ideServices: Property<Boolean>

    private val pluginManager = IdePluginManager.createManager()
    private val log = Logger(javaClass)

    @TaskAction
    fun publishPlugin() {
        if (token.orNull.isNullOrEmpty()) {
            throw GradleException("'token' property must be specified for plugin publishing")
        }

        val path = archiveFile.asPath
        val plugin = pluginManager.safelyCreatePlugin(path, suppressPluginProblems = false).getOrThrow()

        val pluginId = plugin.pluginId
        channels.get().forEach { channel ->
            log.info("Uploading plugin '$pluginId' from '$path' to '${host.get()}', channel: '$channel'")
            try {
                val repositoryClient = when (ideServices.get()) {
                    true -> PluginRepositoryFactory.createWithImplementationClass(
                        host.get(),
                        token.get(),
                        "Automation",
                        IdeServicesPluginRepositoryService::class.java,
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
                throw GradleException("Failed to upload plugin: ${exception.message}", exception)
            }
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Publishes the plugin to the remote plugins repository."
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<PublishPluginTask>(Tasks.PUBLISH_PLUGIN) {
                val publishingProvider = project.extensionProvider.map { it.publishing }
                val buildPluginTaskProvider = project.tasks.named<BuildPluginTask>(Tasks.BUILD_PLUGIN)
                val signPluginTaskProvider = project.tasks.named<SignPluginTask>(Tasks.SIGN_PLUGIN)

                val isOffline = project.gradle.startParameter.isOffline

                token.convention(publishingProvider.flatMap { it.token })
                host.convention(publishingProvider.flatMap { it.host })
                ideServices.convention(publishingProvider.flatMap { it.ideServices })
                channels.convention(publishingProvider.flatMap { it.channels })
                hidden.convention(publishingProvider.flatMap { it.hidden })

                // TODO: can this be done in any other way?
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
