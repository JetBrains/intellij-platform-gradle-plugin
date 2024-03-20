// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.of
import org.gradle.kotlin.dsl.the
import org.jetbrains.intellij.platform.gradle.BuildFeature
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.extensions.IntelliJPlatformExtension
import org.jetbrains.intellij.platform.gradle.isBuildFeatureEnabled
import org.jetbrains.intellij.platform.gradle.providers.CurrentPluginVersionValueSource
import org.jetbrains.intellij.platform.gradle.resolvers.latestVersion.IntelliJPlatformGradlePluginLatestVersionResolver
import org.jetbrains.intellij.platform.gradle.tasks.aware.CoroutinesJavaAgentAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.asPath
import java.time.LocalDate
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.io.path.outputStream

/**
 * Initializes the IntelliJ Platform Gradle Plugin and performs various checks, like if the plugin is up-to-date.
 */
@UntrackedTask(because = "Should always run")
abstract class InitializeIntelliJPlatformPluginTask : DefaultTask(), IntelliJPlatformVersionAware {

    /**
     * Indicates whether Gradle is run in offline mode.
     */
    @get:Internal
    abstract val offline: Property<Boolean>

    /**
     * Represents the property for checking if self-update is enabled.
     *
     * Default value: [BuildFeature.SELF_UPDATE_CHECK]
     */
    @get:Internal
    abstract val selfUpdateCheck: Property<Boolean>

    /**
     * Represents a lock file used to limit the plugin version checks in time.
     * If a file is absent, and other conditions are met, the version check is performed.
     */
    @get:Internal
    abstract val selfUpdateLock: RegularFileProperty

    /**
     * Java Agent file for the Coroutines library, which is required to enable coroutines debugging.
     *
     * @see [CoroutinesJavaAgentAware]
     */
    @get:Internal
    abstract val coroutinesJavaAgent: RegularFileProperty

    /**
     * Represents the current version of the plugin.
     */
    @get:Internal
    abstract val pluginVersion: Property<String>

    private val log = Logger(javaClass)

    init {
        group = Plugin.GROUP_NAME
        description = "Initializes the IntelliJ Platform Gradle Plugin"
    }

    @TaskAction
    fun initialize() {
        checkPluginVersion()
        createCoroutinesJavaAgentFile()
    }

    /**
     * Checks if the plugin is up-to-date.
     */
    private fun checkPluginVersion() {
        if (!selfUpdateCheck.get() || selfUpdateLock.asPath.exists() || offline.get()) {
            return
        }

        try {
            val version = Version.parse(pluginVersion.get())
            val latestVersion = IntelliJPlatformGradlePluginLatestVersionResolver().resolve()
            if (version < latestVersion) {
                log.warn("${Plugin.NAME} is outdated: $version. Update `${Plugin.ID}` to: $latestVersion")
            }

            with(selfUpdateLock.asPath) {
                if (!exists()) {
                    parent.createDirectories()
                    createFile()
                }
            }
        } catch (e: Exception) {
            log.error(e.message.orEmpty(), e)
        }
    }

    /**
     * Creates the Java Agent file for the Coroutines library required to enable coroutines debugging.
     */
    private fun createCoroutinesJavaAgentFile() {
        if (coroutinesJavaAgent.asPath.exists()) {
            return
        }

        val manifest = Manifest(
            """
            Manifest-Version: 1.0
            Premain-Class: kotlinx.coroutines.debug.AgentPremain
            Can-Retransform-Classes: true
            Multi-Release: true
            
            """.trimIndent().byteInputStream()
        )

        JarOutputStream(coroutinesJavaAgent.asPath.outputStream(), manifest).close()
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
                val extension = project.the<IntelliJPlatformExtension>()

                offline.convention(project.gradle.startParameter.isOffline)
                selfUpdateCheck.convention(project.isBuildFeatureEnabled(BuildFeature.SELF_UPDATE_CHECK))
                selfUpdateLock.convention(
                    project.layout.file(project.provider {
                        temporaryDir.resolve(LocalDate.now().toString())
                    })
                )
                coroutinesJavaAgent.convention(
                    project.layout.file(project.provider {
                        extension.cachePath.also {
                            it.createDirectories()
                        }.resolve("coroutines-javaagent.jar").toFile()
                    })
                )
                pluginVersion.convention(project.providers.of(CurrentPluginVersionValueSource::class) {})

                onlyIf {
                    !selfUpdateLock.asPath.exists() || !coroutinesJavaAgent.asPath.exists()
                }
                mustRunAfter("clean")
            }
    }
}
