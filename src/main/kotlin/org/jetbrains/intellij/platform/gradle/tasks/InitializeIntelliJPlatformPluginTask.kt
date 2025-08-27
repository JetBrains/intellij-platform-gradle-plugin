// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.intellij.platform.gradle.tasks

import org.gradle.StartParameter
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.UntrackedTask
import org.gradle.kotlin.dsl.of
import org.jetbrains.intellij.platform.gradle.Constants.Plugin
import org.jetbrains.intellij.platform.gradle.Constants.Tasks
import org.jetbrains.intellij.platform.gradle.GradleProperties
import org.jetbrains.intellij.platform.gradle.get
import org.jetbrains.intellij.platform.gradle.providers.CurrentPluginVersionValueSource
import org.jetbrains.intellij.platform.gradle.providers.LatestPluginVersionValueSource
import org.jetbrains.intellij.platform.gradle.tasks.aware.IntelliJPlatformVersionAware
import org.jetbrains.intellij.platform.gradle.tasks.aware.ModuleAware
import org.jetbrains.intellij.platform.gradle.utils.Logger
import org.jetbrains.intellij.platform.gradle.utils.Version
import org.jetbrains.intellij.platform.gradle.utils.asPath
import org.jetbrains.intellij.platform.gradle.utils.extensionProvider
import java.time.LocalDate
import kotlin.io.path.readText
import kotlin.io.path.writeText

private const val CLEAN = "clean"

/**
 * Executes before every other task introduced by IntelliJ Platform Gradle Plugin to prepare it to run.
 * It is responsible for:
 *
 * - checking if the project uses IntelliJ Platform Gradle Plugin in the latest available version
 *
 * The self-update check can be disabled via [GradleProperties.SelfUpdateCheck] Gradle property.
 */
@UntrackedTask(because = "Should always run")
abstract class InitializeIntelliJPlatformPluginTask : DefaultTask(), IntelliJPlatformVersionAware, ModuleAware {

    /**
     * Determines if the operation is running in offline mode and depends on Gradle start parameters.
     *
     * Default value: [StartParameter.isOffline]
     */
    @get:Internal
    abstract val offline: Property<Boolean>

    /**
     * Represents the property for checking if self-update checks are enabled.
     *
     * Default value: [GradleProperties.SelfUpdateCheck]
     */
    @get:Internal
    abstract val selfUpdateCheck: Property<Boolean>

    /**
     * Represents a lock file used to limit the plugin version checks in time.
     * If the file is missing and other conditions are met, the version check is performed.
     */
    @get:Internal
    abstract val selfUpdateLock: RegularFileProperty

    /**
     * Represents the current version of the plugin.
     */
    @get:Internal
    abstract val pluginVersion: Property<String>

    /**
     * Represents the latest version of the plugin.
     */
    @get:Internal
    abstract val latestPluginVersion: Property<String>

    private val log = Logger(javaClass)

    @TaskAction
    fun initialize() {
        checkPluginVersion()
    }

    /**
     * Checks if the plugin is up to date.
     */
    private fun checkPluginVersion() {
        if (module.get() || !selfUpdateCheck.get() || offline.get()) {
            return
        }

        val lastUpdate = runCatching {
            LocalDate.parse(selfUpdateLock.asPath.readText().trim())
        }.getOrNull()

        if (lastUpdate == LocalDate.now()) {
            return
        }

        try {
            val version = Version.parse(pluginVersion.get())
            val latestVersion = Version.parse(latestPluginVersion.get())

            if (version < latestVersion) {
                log.warn("${Plugin.NAME} is outdated: $version. Update `${Plugin.ID}` to: $latestVersion")
            }

            with(selfUpdateLock.asPath) {
                writeText(LocalDate.now().toString())
            }
        } catch (e: Exception) {
            log.error(e.message.orEmpty(), e)
        }
    }

    init {
        group = Plugin.GROUP_NAME
        description = "Initializes the IntelliJ Platform Gradle Plugin"
    }

    companion object : Registrable {
        override fun register(project: Project) =
            project.registerTask<InitializeIntelliJPlatformPluginTask>(Tasks.INITIALIZE_INTELLIJ_PLATFORM_PLUGIN) {
                val cachePathProvider = project.extensionProvider.flatMap { it.caching.path }

                offline.convention(project.gradle.startParameter.isOffline)
                selfUpdateCheck.convention(project.providers[GradleProperties.SelfUpdateCheck])
                selfUpdateLock.convention(
                    project.layout.file(cachePathProvider.map {
                        it.file("self-update.lock").asFile
                    })
                )
                pluginVersion.convention(project.providers.of(CurrentPluginVersionValueSource::class) {})
                latestPluginVersion.convention(project.providers.of(LatestPluginVersionValueSource::class) {})

                mustRunAfter(CLEAN)
            }
    }
}
